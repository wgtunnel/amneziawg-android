/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.backend;

import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;
import org.amnezia.awg.config.Config;
import org.amnezia.awg.config.proxy.Socks5Proxy;
import org.amnezia.awg.util.NonNullForAll;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.amnezia.awg.ProxyGoBackend.*;

@NonNullForAll
public final class ProxyGoBackend extends AbstractBackend {
    private static final String TAG = "AmneziaWG/ProxyGoBackend";

    public ProxyGoBackend(final Context context, final TunnelActionHandler tunnelActionHandler) {
        super(context, tunnelActionHandler);
    }

    record KillSwitchContext(VpnService vpnService, int port, Config config) {}


    private int getAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private KillSwitchContext setupKillSwitch(Config config) throws BackendException {
        if (VpnService.prepare(context) != null)
            throw new BackendException(BackendException.Reason.VPN_NOT_AUTHORIZED);
        try {
            Log.d(TAG, "Kill switch: Refreshed VpnService and protector");
            VpnService vpnService = startVpnService(this);
            int port = getAvailablePort();

            Config startConfig = new Config.Builder()
                    .setDnsSettings(config.getDnsSettings())
                    .setInterface(config.getInterface())
                    .addPeers(config.getPeers())
                    .addProxies(List.of(new Socks5Proxy(
                            String.format("%s:%d", LOCALHOST, port),
                            USERNAME,
                            PASSWORD
                    )))
                    .build();
            return new KillSwitchContext(vpnService, port, startConfig);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start kill switch", e);
            throw new BackendException(BackendException.Reason.UNABLE_TO_START_VPN);
        }
    }


    @Override
    protected void configureAndStartTunnel(final Tunnel tunnel, final Config config) throws Exception {
        if (currentTunnelHandle != -1) {
            Log.w(TAG, "Tunnel already up");
            return;
        }
        boolean isKillSwitch = backendMode instanceof BackendMode.KillSwitch;
        KillSwitchContext ks = isKillSwitch ? setupKillSwitch(config) : null;
        Config startConfig = (ks != null) ? ks.config() : config;

        resolvePeerEndpoints(config, tunnel.isIpv4ResolutionPreferred(), true);

        final String quickConfig = startConfig.toAwgQuickStringResolved(false, true, tunnel.isIpv4ResolutionPreferred(), context);
        tunnelActionHandler.runPreUp(config.getInterface().getPreUp());
        String uapiPath = context.getDataDir().getAbsolutePath();
        // simple flag to tell proxy backend to bypass netstack sockets or not
        int bypass = isKillSwitch ? 1 : 0;
        currentTunnelHandle = awgStartProxy(tunnel.getName(), quickConfig, uapiPath, bypass);
        tunnelActionHandler.runPostUp(config.getInterface().getPostUp());
        if (currentTunnelHandle < 0) {
            throw new BackendException(BackendException.Reason.GO_ACTIVATION_ERROR_CODE, currentTunnelHandle);
        }
        if (ks != null) ks.vpnService.startHevTunnel(ks.port);
    }

    @Override
    protected void stopTunnel(final Tunnel tunnel, @Nullable final Config config) {
        if (currentTunnelHandle == -1) {
            Log.w(TAG, "Tunnel already down");
            return;
        }
        tunnelActionHandler.runPreDown(config != null ? config.getInterface().getPreDown() : null);
        awgResetJNIGlobals();
        awgStopProxy();
        if(backendMode instanceof BackendMode.KillSwitch) try {
            vpnService.get(2_000L, TimeUnit.SECONDS).stopHevTunnel();
        } catch (Exception e) {
            Log.e(TAG, "Failed to hev tunnel", e);
        }
        currentTunnelHandle = -1;
        tunnelActionHandler.runPostDown(config != null ? config.getInterface().getPostDown() : null);
    }

    @Override
    protected BackendMode setBackendModeInternal(final BackendMode backendMode) throws Exception {
        Log.d(TAG, "Setting backend mode: " + backendMode + "current " + this.backendMode);
        Optional<VpnService> service = vpnService.isDone() ? Optional.ofNullable(vpnService.get(2, TimeUnit.SECONDS)) : Optional.empty();

        // config already matches and up, return
        if(backendMode instanceof BackendMode.KillSwitch update) {
            if(service.isPresent() && this.backendMode instanceof BackendMode.KillSwitch current) {
                if(current.getAllowedIps()
                        .equals(update.getAllowedIps()) && current.isMetered() == update.isMetered() && current.isDualStack() == update.isDualStack()) {
                    return current;
                } else {
                    service.get().activateKillSwitch(update.getAllowedIps(), update.isMetered(), update.isDualStack());
                    return update;
                }
            } else {
                Log.d(TAG, "Getting the service");
                VpnService newService = startVpnService(this);
                newService.activateKillSwitch(update.getAllowedIps(), update.isMetered(), update.isDualStack());
                return update;
            }
        } else {
            // mode inactive, shutdown
            service.ifPresent(VpnService::shutdown);
            return backendMode;
        }
    }

    @Override
    @Nullable
    protected String getTunnelConfig(final int handle) {
        return awgGetProxyConfig(handle);
    }

    @Override
    public boolean updateActiveTunnelPeers(Config config) throws UnsupportedOperationException {
        if (currentTunnelHandle == -1) throw new UnsupportedOperationException();
        int completed = awgUpdateProxyTunnelPeers(currentTunnelHandle, config.toAwgQuickStringResolved(false, false,  currentTunnel.isIpv4ResolutionPreferred(), context));
        return completed == 0;

    }
}