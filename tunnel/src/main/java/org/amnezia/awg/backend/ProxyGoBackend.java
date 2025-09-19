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

import java.util.List;

import static org.amnezia.awg.ProxyGoBackend.*;

@NonNullForAll
public final class ProxyGoBackend extends AbstractBackend {
    private static final String TAG = "AmneziaWG/ProxyGoBackend";

    public ProxyGoBackend(final Context context, final TunnelActionHandler tunnelActionHandler) {
        super(context, tunnelActionHandler);
    }

    @Override
    protected void configureAndStartTunnel(final Tunnel tunnel, final Config config) throws Exception {
        if (currentTunnelHandle != -1) {
            Log.w(TAG, "Tunnel already up");
            return;
        }
        resolvePeerEndpoints(config, tunnel.isIpv4ResolutionPreferred(), true);

        Config startConfig = config;

        // overwrite proxy settings for lockdown mode
        boolean isKillSwitch = backendMode instanceof BackendMode.KillSwitch;
        if (isKillSwitch) startConfig = new Config.Builder()
                .setDnsSettings(config.getDnsSettings())
                .setInterface(config.getInterface())
                .addPeers(config.getPeers())
                .addProxies(List.of(new Socks5Proxy(
                        String.format("%s:%d", LOCALHOST, PORT),
                        USERNAME,
                        PASSWORD
                ))).build();

        if (isKillSwitch) {
            if (VpnService.prepare(context) != null) {
                throw new BackendException(BackendException.Reason.VPN_NOT_AUTHORIZED);
            }
            startVpnService(this);  // fetch and sets owner/protector
            Log.d(TAG, "Proxy tunnel: Refreshed VpnService and protector for kill switch");
        }

        final String quickConfig = startConfig.toAwgQuickStringResolved(false, true, tunnel.isIpv4ResolutionPreferred());
        tunnelActionHandler.runPreUp(config.getInterface().getPreUp());
        String packageName = context.getPackageName();
        // simple flag to tell proxy backend to bypass netstack sockets or not
        int bypass = isKillSwitch ? 1 : 0;
        currentTunnelHandle = awgStartProxy(tunnel.getName(), quickConfig, packageName, bypass);
        tunnelActionHandler.runPostUp(config.getInterface().getPostUp());
        if (currentTunnelHandle < 0) {
            throw new BackendException(BackendException.Reason.GO_ACTIVATION_ERROR_CODE, currentTunnelHandle);
        }
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
        currentTunnelHandle = -1;
        tunnelActionHandler.runPostDown(config != null ? config.getInterface().getPostDown() : null);
    }

    @Override
    protected BackendMode setBackendModeInternal(final BackendMode backendMode) throws Exception {
        Log.d(TAG, "Setting backend mode: " + backendMode + "current " + this.backendMode);
        boolean disableLockdown = backendMode instanceof BackendMode.Inactive;
        boolean isLockdownActive = vpnService.isDone();
        if(disableLockdown &&
                this.backendMode instanceof BackendMode.Inactive) return backendMode;

        if(backendMode instanceof BackendMode.KillSwitch update &&
                this.backendMode instanceof BackendMode.KillSwitch current) {
            if(current.getAllowedIps()
                    .equals(update.getAllowedIps())) {
                return backendMode;
            }
        }
        Log.d(TAG, "Checking if vpnservice is active");
        // nothing to do
        if(disableLockdown && !isLockdownActive) return backendMode;

        Log.d(TAG, "Getting the service");
        VpnService service = startVpnService(this);

        if (backendMode instanceof BackendMode.KillSwitch) {
            service.setOwner(this);
        }

        if(disableLockdown) {
            Log.d(TAG, "Shutting it down");
            service.shutdown();
            return backendMode;
        }
        if(isLockdownActive) service.shutdown();
        if (backendMode instanceof BackendMode.KillSwitch mode) service.activateKillSwitch(mode.getAllowedIps());
        return backendMode;
    }

    @Override
    @Nullable
    protected String getTunnelConfig(final int handle) {
        return awgGetProxyConfig(handle);
    }

    @Override
    public boolean updateActiveTunnelPeers(Config config) throws UnsupportedOperationException {
        if (currentTunnelHandle == -1) throw new UnsupportedOperationException();
        int completed = awgUpdateProxyTunnelPeers(currentTunnelHandle, config.toAwgQuickStringResolved(false, false,  currentTunnel.isIpv4ResolutionPreferred()));
        return completed == 0;

    }
}