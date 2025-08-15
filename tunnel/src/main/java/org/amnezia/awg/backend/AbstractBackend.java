/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.backend;

import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;
import com.getkeepsafe.relinker.ReLinker;
import org.amnezia.awg.config.Config;
import org.amnezia.awg.crypto.Key;
import org.amnezia.awg.crypto.KeyFormatException;
import org.amnezia.awg.util.NonNullForAll;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.amnezia.awg.GoBackend.*;

@NonNullForAll
public abstract class AbstractBackend implements Backend, KillSwitchHandler {
    private static final String TAG = "AmneziaWG/AbstractBackend";
    protected final Context context;
    protected final TunnelActionHandler tunnelActionHandler;

    @Nullable private static VpnService.AlwaysOnCallback alwaysOnCallback;
    @Nullable protected Config currentConfig;
    @Nullable protected Tunnel currentTunnel;
    protected int currentTunnelHandle = -1;
    protected BackendStatus backendStatus = BackendStatus.Inactive.INSTANCE;

    static CompletableFuture<VpnService> vpnService = new CompletableFuture<>();

    public static void setAlwaysOnCallback(final VpnService.AlwaysOnCallback cb) {
        alwaysOnCallback = cb;
    }


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
    public BackendStatus getBackendStatus() {
        return backendStatus;
    }

    @Override
    public String getVersion() {
        return awgVersion();
    }

    @Override
    public Statistics getStatistics(final Tunnel tunnel) throws Exception {
        final Statistics stats = new Statistics();
        if (tunnel != currentTunnel || currentTunnelHandle == -1) {
            return stats;
        }
        final String config = awgGetConfig(currentTunnelHandle);
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

    @Override
    public Tunnel.State setState(final Tunnel tunnel, Tunnel.State state, @Nullable final Config config) throws Exception {
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
    }

    public interface AlwaysOnCallback {
        void alwaysOnTriggered();
    }

    @Override
    public BackendStatus setBackendStatus(BackendStatus backendStatus) throws Exception {
        if ((this.backendStatus instanceof BackendStatus.Inactive && backendStatus instanceof BackendStatus.Inactive) ||
                (this.backendStatus instanceof BackendStatus.ServiceActive && backendStatus instanceof BackendStatus.ServiceActive) ||
                (this.backendStatus instanceof BackendStatus.KillSwitchActive currentKillSwitch &&
                        backendStatus instanceof BackendStatus.KillSwitchActive newKillSwitch &&
                        currentKillSwitch.getAllowedIps().equals(newKillSwitch.getAllowedIps()))) {
            Log.d(TAG, "Backend status already active");
            return this.backendStatus;
        }
        if (currentTunnel != null) {
            Log.d(TAG, "Tunnel running, deferring status change until tunnel is down");
            this.backendStatus = backendStatus;
            return backendStatus;
        }
        this.backendStatus = setBackendStatusInternal(backendStatus);
        return this.backendStatus;
    }

    private void setStateInternal(final Tunnel tunnel, @Nullable final Config config, final Tunnel.State state)
            throws Exception {
        Log.i(TAG, "Bringing tunnel " + tunnel.getName() + ' ' + state);
        if (state == Tunnel.State.UP) {
            if (config == null) {
                throw new BackendException(BackendException.Reason.TUNNEL_MISSING_CONFIG);
            }
            // Deactivate killswitch before bringing up the tunnel
            if (backendStatus instanceof BackendStatus.KillSwitchActive) {
                deactivateKillSwitch();
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
            if (backendStatus instanceof BackendStatus.KillSwitchActive killSwitch) {
                activateKillSwitch(killSwitch.getAllowedIps());
            } else if (backendStatus instanceof BackendStatus.ServiceActive) {
                activateService();
            } else if (backendStatus instanceof BackendStatus.Inactive) {
                shutdown();
            }
        }
        tunnel.onStateChange(state);
    }

    @Override
    public void activateKillSwitch(Collection<String> allowedIps) throws Exception {
        if (!vpnService.isDone()) {
            Log.d(TAG, "Requesting to start VpnService for kill switch");
            context.startService(new Intent(context, VpnService.class));
        }
        try {
            VpnService service = vpnService.get(2, TimeUnit.SECONDS);
            service.setOwner(this);
            service.activateKillSwitch(allowedIps);
            this.backendStatus = new BackendStatus.KillSwitchActive(allowedIps);
        } catch (final Exception e) {
            Log.e(TAG, "Failed to activate kill switch", e);
            this.backendStatus = BackendStatus.Inactive.INSTANCE;
            throw e;
        }
    }

    @Override
    public void deactivateKillSwitch() throws Exception {
        if (vpnService.isDone()) {
            try {
                VpnService service = vpnService.get(0, TimeUnit.MILLISECONDS);
                service.deactivateKillSwitch();
                this.backendStatus = BackendStatus.Inactive.INSTANCE;
            } catch (final Exception e) {
                Log.e(TAG, "Failed to deactivate kill switch", e);
                this.backendStatus = BackendStatus.Inactive.INSTANCE;
                throw e;
            }
        } else {
            Log.i(TAG, "No VpnService active for kill switch deactivation");
            this.backendStatus = BackendStatus.Inactive.INSTANCE;
        }
    }

    protected abstract void configureAndStartTunnel(Tunnel tunnel, Config config) throws Exception;

    protected abstract void stopTunnel(Tunnel tunnel, @Nullable Config config) throws Exception;

    protected abstract BackendStatus setBackendStatusInternal(BackendStatus backendStatus) throws Exception;

    protected void activateService() {
        if (!vpnService.isDone()) {
            Log.d(TAG, "Requesting service activation");
            context.startService(new Intent(context, VpnService.class));
        }
        try {
            VpnService service = vpnService.get(2, TimeUnit.SECONDS);
            if(backendStatus instanceof BackendStatus.KillSwitchActive) service.deactivateKillSwitch();
            Log.d(TAG, "Service is now active");
            service.setOwner(this);
            backendStatus = BackendStatus.ServiceActive.INSTANCE;
        } catch (final TimeoutException | ExecutionException | InterruptedException ignored) {
            backendStatus = BackendStatus.Inactive.INSTANCE;
        } catch (Exception e) {
            Log.e(TAG, "Failed to activate service", e);
        }
    }

    protected void shutdown() throws Exception {
        Log.d(TAG, "Shutdown...");
        if (backendStatus instanceof BackendStatus.KillSwitchActive) {
            deactivateKillSwitch();
        }
        this.backendStatus = BackendStatus.Inactive.INSTANCE;
    }

    @NonNullForAll
    public static class VpnService extends android.net.VpnService {
        private static final String TAG = "AmneziaWG/VpnService";
        @Nullable private KillSwitchHandler owner;
        @Nullable private ParcelFileDescriptor mInterface;

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
            if (owner != null && owner instanceof AbstractBackend backend) {
                final Tunnel tunnel = backend.currentTunnel;
                if (tunnel != null) {
                    if (backend.currentTunnelHandle != -1) {
                        awgTurnOff(backend.currentTunnelHandle);
                    }
                    backend.currentTunnel = null;
                    backend.currentTunnelHandle = -1;
                    backend.currentConfig = null;
                    backend.backendStatus = Backend.BackendStatus.Inactive.INSTANCE;
                    tunnel.onStateChange(Tunnel.State.DOWN);
                }
            }
            vpnService = new CompletableFuture<>();
            super.onDestroy();
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

        public void setOwner(final KillSwitchHandler owner) {
            this.owner = owner;
            if (owner instanceof AbstractBackend backend) {
                backend.backendStatus = Backend.BackendStatus.ServiceActive.INSTANCE;
            }
        }

        public void activateKillSwitch(Collection<String> allowedIps) throws Exception {
            Builder builder = new Builder();
            Log.d(TAG, "Starting kill switch with allowedIps: " + allowedIps);
            builder.setSession("KillSwitchSession")
                    .addAddress("10.0.0.2", 32)
                    .addAddress("2001:db8::2", 64);
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
            try {
                if (mInterface != null) {
                    mInterface.close();
                }
                mInterface = builder.establish();
                if (owner instanceof AbstractBackend backend) {
                    backend.backendStatus = new Backend.BackendStatus.KillSwitchActive(allowedIps);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to start kill switch", e);
                throw e;
            }
        }

        public void deactivateKillSwitch() throws Exception {
            if (mInterface != null) {
                try {
                    mInterface.close();
                    Log.d(TAG, "FD closed");
                    mInterface = null;
                    if (owner instanceof AbstractBackend backend) {
                        backend.backendStatus = Backend.BackendStatus.ServiceActive.INSTANCE;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to stop kill switch", e);
                    throw e;
                }
            } else {
                Log.i(TAG, "FD already closed");
            }
        }

        public interface AlwaysOnCallback {
            void alwaysOnTriggered();
        }
    }

}