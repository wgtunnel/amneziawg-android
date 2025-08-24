/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.backend;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.ArraySet;
import android.util.Log;
import androidx.annotation.Nullable;
import com.getkeepsafe.relinker.ReLinker;
import org.amnezia.awg.config.Config;
import org.amnezia.awg.config.DnsSettings;
import org.amnezia.awg.config.InetEndpoint;
import org.amnezia.awg.config.Peer;
import org.amnezia.awg.crypto.Key;
import org.amnezia.awg.crypto.KeyFormatException;
import org.amnezia.awg.hevtunnel.TProxyService;
import org.amnezia.awg.util.NonNullForAll;

import javax.net.SocketFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import static org.amnezia.awg.GoBackend.awgTurnOff;
import static org.amnezia.awg.GoBackend.awgVersion;
import static org.amnezia.awg.ProxyGoBackend.awgSetSocketProtector;
import static org.amnezia.awg.ProxyGoBackend.awgStopProxy;

@NonNullForAll
public abstract class AbstractBackend implements Backend {
    private static final String TAG = "AmneziaWG/AbstractBackend";

    private static final int DNS_RESOLUTION_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 500;
    private static final int MTU = 1280;

    //kill switch defaults
    protected static final String USERNAME = "local";
    protected static final String PASSWORD = UUID.randomUUID().toString();
    protected static final String LOCALHOST = "127.0.0.1";
    protected static final int PORT = 25344;

    protected final Context context;
    protected final TunnelActionHandler tunnelActionHandler;

    @Nullable protected Config currentConfig;
    @Nullable protected Tunnel currentTunnel;
    protected int currentTunnelHandle = -1;

    protected final ReentrantLock tunnelLock = new ReentrantLock();

    protected static CompletableFuture<VpnService> vpnService = new CompletableFuture<>();

    @Nullable private static VpnService.AlwaysOnCallback alwaysOnCallback;

    public static void setAlwaysOnCallback(final VpnService.AlwaysOnCallback cb) {
        alwaysOnCallback = cb;
    }
    protected volatile BackendMode backendMode = BackendMode.Inactive.INSTANCE;

    protected AbstractBackend(final Context context, final TunnelActionHandler tunnelActionHandler) {
        ReLinker.loadLibrary(context, "am-go");
        this.context = context;
        this.tunnelActionHandler = tunnelActionHandler;
    }

    @Override
    public Set<String> getRunningTunnelNames() {
        if (currentTunnel != null) {
            final Set<String> runningTunnels = new ArraySet<>();
            runningTunnels.add(currentTunnel.getName());
            return runningTunnels;
        }
        return Collections.emptySet();
    }

    @Override
    public Tunnel.State getState(final Tunnel tunnel) {
        return currentTunnel == tunnel ? Tunnel.State.UP : Tunnel.State.DOWN;
    }

    @Override
    public BackendMode getBackendMode() {
        return backendMode;
    }

    @Override
    public String getVersion() {
        return awgVersion();
    }

    @Override
    public Tunnel.State setState(final Tunnel tunnel, Tunnel.State state, @Nullable final Config config) throws Exception {
        tunnelLock.lock();
        try {
            final Tunnel.State originalState = getState(tunnel);
            if (state == originalState && tunnel == currentTunnel && config == currentConfig) {
                return originalState;
            }
            if (state == Tunnel.State.UP) {
                final Config originalConfig = currentConfig;
                final Tunnel originalTunnel = currentTunnel;
                if (currentTunnel != null) {
                    setStateInternal(currentTunnel, null, Tunnel.State.DOWN);
                }
                try {
                    handleResolverConfiguration(config);
                    setStateInternal(tunnel, config, state);
                } catch (final Exception e) {
                    if (originalTunnel != null) {
                        setStateInternal(originalTunnel, originalConfig, Tunnel.State.UP);
                    }
                    throw e;
                }
            } else if (state == Tunnel.State.DOWN && tunnel == currentTunnel) {
                setStateInternal(tunnel, null, Tunnel.State.DOWN);
            }
            return getState(tunnel);
        } finally {
            tunnelLock.unlock();
        }
    }

    private void handleResolverConfiguration(@Nullable Config config) throws ExecutionException, InterruptedException, TimeoutException {
        // Set resolver based on mode
        boolean isKillSwitch = backendMode instanceof BackendMode.KillSwitch;
        SocketFactory socketFactory = isKillSwitch ? vpnService.get(2, TimeUnit.SECONDS).new ProtectedSocketFactory() : null;
        // Need to pass settings for useDOH and dohUrl via settings
        DnsSettings dnsSettings = (config != null && config.getDnsSettings() != null) ? config.getDnsSettings() : new DnsSettings(false, null);
        InetEndpoint.setResolver(dnsSettings.dohEnabled() || isKillSwitch ? new InetEndpoint.DoHResolver(dnsSettings.dohUrl(), Optional.ofNullable(socketFactory)) : new InetEndpoint.SystemResolver());
    }

    @Override
    public BackendMode setBackendMode(BackendMode backendMode) throws Exception {
        tunnelLock.lock();
        try {
            this.backendMode = setBackendModeInternal(backendMode);
            return this.backendMode;
        } finally {
            tunnelLock.unlock();
        }
    }

    protected void setStateInternal(final Tunnel tunnel, @Nullable final Config config, final Tunnel.State state)
            throws Exception {
        Log.i(TAG, "Bringing tunnel " + tunnel.getName() + ' ' + state);
        if (state == Tunnel.State.UP) {
            if (config == null) {
                throw new BackendException(BackendException.Reason.TUNNEL_MISSING_CONFIG);
            }
            configureAndStartTunnel(tunnel, config);
            currentTunnel = tunnel;
            currentConfig = config;
        } else {
            if (currentTunnelHandle == -1) {
                Log.w(TAG, "Tunnel already down");
                return;
            }
            stopTunnel(tunnel, currentConfig);
            currentTunnel = null;
            currentTunnelHandle = -1;
            currentConfig = null;
        }
        tunnel.onStateChange(state);
    }

    protected VpnService startVpnService(AbstractBackend owner) throws Exception {
        if (!vpnService.isDone()) {
            Log.d(TAG, "Requesting to start VpnService");
            context.startService(new Intent(context, VpnService.class));
        } else return vpnService.get(2, TimeUnit.SECONDS);
        VpnService service;
        try {
            service = vpnService.get(2, TimeUnit.SECONDS);
        } catch (final TimeoutException e) {
            final Exception be = new BackendException(BackendException.Reason.UNABLE_TO_START_VPN);
            be.initCause(e);
            throw be;
        }
        service.setOwner(owner);
        return service;
    }

    protected abstract void configureAndStartTunnel(Tunnel tunnel, Config config) throws Exception;

    protected abstract void stopTunnel(Tunnel tunnel, @Nullable Config config) throws Exception;

    protected abstract BackendMode setBackendModeInternal(BackendMode backendMode) throws Exception;

    @Override
    public Statistics getStatistics(final Tunnel tunnel) throws Exception {
        final Statistics stats = new Statistics();
        if (tunnel != currentTunnel || currentTunnelHandle == -1) {
            return stats;
        }
        final String config = getTunnelConfig(currentTunnelHandle);
        if (config == null) {
            return stats;
        }
        Key key = null;
        long rx = 0;
        long tx = 0;
        String endpoint = "";
        long latestHandshakeMSec = 0;
        for (final String line : config.split("\\n")) {
            if (line.startsWith("public_key=")) {
                if (key != null) {
                    stats.add(key, endpoint, rx, tx, latestHandshakeMSec);
                }
                rx = 0;
                tx = 0;
                latestHandshakeMSec = 0;
                try {
                    key = Key.fromHex(line.substring(11));
                } catch (final KeyFormatException ignored) {
                    key = null;
                }
            } else if (line.startsWith("rx_bytes=")) {
                if (key == null) {
                    continue;
                }
                try {
                    rx = Long.parseLong(line.substring(9));
                } catch (final NumberFormatException ignored) {
                    rx = 0;
                }
            } else if (line.startsWith("endpoint=")) {
                if (key == null) {
                    continue;
                }
                try {
                    endpoint = line.substring(9);
                } catch (final Exception ignored) {
                    endpoint = "";
                }
            } else if (line.startsWith("tx_bytes=")) {
                if (key == null) {
                    continue;
                }
                try {
                    tx = Long.parseLong(line.substring(9));
                } catch (final NumberFormatException ignored) {
                    tx = 0;
                }
            } else if (line.startsWith("last_handshake_time_sec=")) {
                if (key == null) {
                    continue;
                }
                try {
                    latestHandshakeMSec += Long.parseLong(line.substring(24)) * 1000;
                } catch (final NumberFormatException ignored) {
                    latestHandshakeMSec = 0;
                }
            } else if (line.startsWith("last_handshake_time_nsec=")) {
                if (key == null) {
                    continue;
                }
                try {
                    latestHandshakeMSec += Long.parseLong(line.substring(25)) / 1000000;
                } catch (final NumberFormatException ignored) {
                    latestHandshakeMSec = 0;
                }
            }
        }
        if (key != null) {
            stats.add(key, endpoint, rx, tx, latestHandshakeMSec);
        }
        return stats;
    }

    @Nullable
    protected abstract String getTunnelConfig(int handle);

    protected void resolvePeerEndpoints(Config config, Tunnel tunnel) throws BackendException {
        List<InetEndpoint> failedEndpoints = new ArrayList<>();
        for (int i = 0; i < DNS_RESOLUTION_RETRIES; ++i) {
            failedEndpoints.clear();
            for (final Peer peer : config.getPeers()) {
                Optional<InetEndpoint> epOpt = peer.getEndpoint();
                if (epOpt.isEmpty()) continue;
                InetEndpoint ep = epOpt.get();
                if (ep.getResolved(tunnel.isIpv4ResolutionPreferred()).isEmpty()) {
                    failedEndpoints.add(ep);
                }
            }
            if (failedEndpoints.isEmpty()) break;
            if (i < DNS_RESOLUTION_RETRIES - 1) {
                for (InetEndpoint ep : failedEndpoints) {
                    Log.w(TAG, "DNS host \"" + ep.getHost() + "\" failed (attempt " + (i + 1) + " of " + DNS_RESOLUTION_RETRIES + ")");
                }
                try {
                    Thread.sleep(INITIAL_BACKOFF_MS * (1 << i));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BackendException(BackendException.Reason.DNS_RESOLUTION_FAILURE, "Interrupted during DNS retry");
                }
            } else {
                throw new BackendException(BackendException.Reason.DNS_RESOLUTION_FAILURE, failedEndpoints.get(0).getHost());
            }
        }
    }

    @NonNullForAll
    public static class VpnService extends android.net.VpnService implements SocketProtector {
        private static final String TAG = "AmneziaWG/VpnService";
        private static final String HEV_CONFIG_FILE_NAME = "tproxy.conf";

        @Nullable private AbstractBackend owner;

        @Nullable private Thread hevStartThread;
        @Nullable private ParcelFileDescriptor fd;

        public Builder getBuilder() {
            return new Builder();
        }

        @Override
        public void onCreate() {
            vpnService.complete(this);
            super.onCreate();
        }

        @Override
        public void onDestroy() {
            if (owner != null) handleDestroy(owner);
            vpnService = new CompletableFuture<>();
            super.onDestroy();
        }

        public void shutdown() {
            try {
                stopKillSwitch();
            } catch (Exception e) {
                Log.w(TAG, e);
            }
        }

        private void handleDestroy(final AbstractBackend owner) {
            final Tunnel tunnel = owner.currentTunnel;
            if (tunnel != null) {
                if (owner.currentTunnelHandle != -1) {
                    if(owner instanceof GoBackend) awgTurnOff(owner.currentTunnelHandle);
                    if(owner instanceof ProxyGoBackend) awgStopProxy();
                }
                owner.currentTunnel = null;
                owner.currentTunnelHandle = -1;
                owner.currentConfig = null;
                owner.backendMode = BackendMode.Inactive.INSTANCE;
                tunnel.onStateChange(Tunnel.State.DOWN);
            }
        }

        @Override
        public int onStartCommand(@Nullable final Intent intent, final int flags, final int startId) {
            vpnService.complete(this);
            if (intent == null || intent.getComponent() == null || !intent.getComponent().getPackageName().equals(getPackageName())) {
                Log.d(TAG, "Service started by Always-on VPN feature");
                if (alwaysOnCallback != null) {
                    alwaysOnCallback.alwaysOnTriggered();
                }
            }
            return super.onStartCommand(intent, flags, startId);
        }

        public void setOwner(final AbstractBackend owner) {
            this.owner = owner;
            try {
                if(owner instanceof ProxyGoBackend) awgSetSocketProtector(this);
            } catch (final Exception e) {
                Log.w(TAG, e);
            }
        }

        protected void activateKillSwitch(Set<String> allowedIps) throws Exception {
            Builder builder = new Builder();
            Log.d(TAG, "Starting kill switch with allowedIps: " + allowedIps);
            builder.setSession("Lockdown");
            builder.addAddress("10.0.0.1", 32); // Dummy IPv4
//            builder.addAddress("2001:db8::1", 128); // Dummy IPv6 (non-routable, per RFC 3849)
            if (allowedIps.isEmpty()) {
                builder.addRoute("0.0.0.0", 0);
            } else {
                allowedIps.forEach((net) -> {
                    Log.d(TAG, "Adding allowedIp: " + net);
                    String[] netSplit = net.split("/");
                    builder.addRoute(netSplit[0], Integer.parseInt(netSplit[1]));
                });
            }

            builder.addRoute("::", 0);
            builder.setMtu(MTU);
            builder.addDnsServer("1.1.1.1");

            fd = builder.establish();
            if (fd == null) {
                throw new BackendException(BackendException.Reason.VPN_NOT_AUTHORIZED);
            }

            hevStartThread = new Thread(() -> {
                try {
                    startHevTunnel();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to start hev tunnel", e);
                }
            });
            hevStartThread.start();
        }

        @Override
        public int bypass(int fd) {
            Log.d(TAG, "Bypassing VPN fd: " + fd);
            int bypassed;
            try {
                bypassed = protect(fd) ? 1 : 0;
            } catch (Exception e) {
                Log.e(TAG, "Failed to protect VPN fd", e);
                bypassed = 0;
            }
            Log.d(TAG, "Socked protected result: " + fd);
            return bypassed;
        }

        private void stopKillSwitch() {
            TProxyService.TProxyStopService();
            if (hevStartThread != null && hevStartThread.isAlive()) {
                hevStartThread.interrupt();
                try {
                    hevStartThread.join(2000); // Wait up to 2s for thread to stop
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted while joining hev thread", e);
                    Thread.currentThread().interrupt();
                }
                hevStartThread = null;
            }
            if(fd != null) {
                Log.d(TAG,"Fd is not null, we need to close it");
                try {
                    fd.close();
                    fd = null;
                } catch (IOException e) {
                    Log.w(TAG,"Error while closing VPN service", e);
                }
            }
            Log.d(TAG, "Kill switch stopped");
        }

        private void startHevTunnel() throws IOException {
            File tproxyFile = new File(getCacheDir(), HEV_CONFIG_FILE_NAME);
            String hevConf = String.format("""
                misc:
                  task-stack-size: 10240
                tunnel:
                  mtu: %d
                socks5:
                  address: '%s'
                  port: %d
                  username: '%s'
                  password: '%s'
                  udp: 'udp'
                """, MTU, LOCALHOST, PORT, USERNAME, PASSWORD);

            try (FileOutputStream fos = new FileOutputStream(tproxyFile, false)) {
                fos.write(hevConf.getBytes());
            } catch (IOException e) {
                Log.e(TAG, "Failed to write tproxy.conf: " + e.getMessage());
                throw new IOException("Failed to write tproxy.conf", e);
            }

            while (!Thread.currentThread().isInterrupted()) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(LOCALHOST, PORT), 1000);
                    Log.d(TAG, "SOCKS5 proxy is up, starting hev-socks5-tunnel...");
                    if(fd != null) {
                        // use dup, as hev expects java side to manage closure
                        TProxyService.TProxyStartService(tproxyFile.getAbsolutePath(), fd.getFd());
                    }
                    return;  // Success, exit the method
                } catch (IOException e) {
                    Log.d(TAG, "SOCKS5 proxy not ready yet, retrying...");
                }

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    Log.d(TAG, "Hev start interrupted");
                    return;  // Exit on interrupt
                }
            }
        }

        private class ProtectedSocketFactory extends SocketFactory {
            @Override
            public Socket createSocket() throws IOException {
                Socket socket = new Socket();
                // Explicitly bind to any local address and random port (0) before connecting.
                socket.bind(new InetSocketAddress(0));
                // Protect the socket *before* any connection attempt.
                if (!protect(socket)) {
                    Log.w(TAG, "protected socket failed");
                    socket.close();  // Clean up if protection fails.
                    throw new IOException("Failed to protect socket before connect");
                } else Log.d(TAG, "protected socket created");
                return socket;
            }

            @Override
            public Socket createSocket(String host, int port) throws IOException {
                Socket socket = createSocket();
                socket.connect(new InetSocketAddress(host, port));
                return socket;
            }

            @Override
            public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
                Socket socket = createSocket();
                socket.bind(new InetSocketAddress(localHost, localPort));
                socket.connect(new InetSocketAddress(host, port));
                return socket;
            }

            @Override
            public Socket createSocket(InetAddress host, int port) throws IOException {
                Socket socket = createSocket();
                socket.connect(new InetSocketAddress(host, port));
                return socket;
            }

            @Override
            public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
                Socket socket = createSocket();
                socket.bind(new InetSocketAddress(localAddress, localPort));
                socket.connect(new InetSocketAddress(address, port));
                return socket;
            }
        }

        public interface AlwaysOnCallback {
            void alwaysOnTriggered();
        }
    }
}