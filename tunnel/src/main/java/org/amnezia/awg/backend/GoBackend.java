/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.backend;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.util.Log;
import androidx.annotation.Nullable;
import org.amnezia.awg.config.Config;
import org.amnezia.awg.config.InetNetwork;
import org.amnezia.awg.config.Peer;
import org.amnezia.awg.util.NonNullForAll;

import java.net.InetAddress;
import static org.amnezia.awg.GoBackend.*;

@NonNullForAll
public final class GoBackend extends AbstractBackend {
    private static final String TAG = "AmneziaWG/GoBackend";

    public GoBackend(final Context context, final TunnelActionHandler tunnelActionHandler) {
        super(context, tunnelActionHandler);
    }

    @Override
    protected void configureAndStartTunnel(final Tunnel tunnel, final Config config) throws Exception {
        if (VpnService.prepare(context) != null) {
            throw new BackendException(BackendException.Reason.VPN_NOT_AUTHORIZED);
        }

        final VpnService service = startVpnService(this);

        if (currentTunnelHandle != -1) {
            Log.w(TAG, "Tunnel already up");
            return;
        }

        resolvePeerEndpoints(config, tunnel.isIpv4ResolutionPreferred(), true);

        final String goConfig = config.toAwgQuickStringResolved(false, false, tunnel.isIpv4ResolutionPreferred());
        final VpnService.Builder builder = service.getBuilder();
        builder.setSession(tunnel.getName());

        for (final String excludedApplication : config.getInterface().getExcludedApplications())
            builder.addDisallowedApplication(excludedApplication);

        for (final String includedApplication : config.getInterface().getIncludedApplications())
            builder.addAllowedApplication(includedApplication);

        for (final InetNetwork addr : config.getInterface().getAddresses())
            builder.addAddress(addr.getAddress(), addr.getMask());

        for (final InetAddress addr : config.getInterface().getDnsServers())
            builder.addDnsServer(addr.getHostAddress());

        for (final String dnsSearchDomain : config.getInterface().getDnsSearchDomains())
            builder.addSearchDomain(dnsSearchDomain);

        boolean sawDefaultRoute = false;
        for (final Peer peer : config.getPeers()) {
            for (final InetNetwork addr : peer.getAllowedIps()) {
                if (addr.getMask() == 0)
                    sawDefaultRoute = true;
                builder.addRoute(addr.getAddress(), addr.getMask());
            }
        }

        if (!(sawDefaultRoute && config.getPeers().size() == 1)) {
            builder.allowFamily(OsConstants.AF_INET);
            builder.allowFamily(OsConstants.AF_INET6);
        }

        builder.setMtu(config.getInterface().getMtu().orElse(1280));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            builder.setMetered(false);

        service.setUnderlyingNetworks(null);
        builder.setBlocking(true);
        try (final ParcelFileDescriptor tun = builder.establish()) {
            if (tun == null)
                throw new BackendException(BackendException.Reason.TUN_CREATION_ERROR);
            Log.d(TAG, "Go backend " + awgVersion());
            tunnelActionHandler.runPreUp(config.getInterface().getPreUp());
            String packageName = context.getPackageName();
            Log.d(TAG, "App package name " + packageName);
            currentTunnelHandle = awgTurnOn(tunnel.getName(), tun.detachFd(), goConfig, packageName);
            tunnelActionHandler.runPostUp(config.getInterface().getPostUp());
        }
        if (currentTunnelHandle < 0)
            throw new BackendException(BackendException.Reason.GO_ACTIVATION_ERROR_CODE, currentTunnelHandle);

        service.protect(awgGetSocketV4(currentTunnelHandle));
        service.protect(awgGetSocketV6(currentTunnelHandle));
    }

    @Override
    protected void stopTunnel(final Tunnel tunnel, @Nullable final Config config) throws Exception {
        if (currentTunnelHandle == -1) {
            Log.w(TAG, "Tunnel already down");
            return;
        }
        int handleToClose = currentTunnelHandle;
        tunnelActionHandler.runPreDown(config != null ? config.getInterface().getPreDown() : null);
        awgTurnOff(handleToClose);
        tunnelActionHandler.runPostDown(config != null ? config.getInterface().getPostDown() : null);
    }

    @Override
    public boolean updateActiveTunnelPeers(Config config) throws UnsupportedOperationException {
        if (currentTunnelHandle == -1) throw new UnsupportedOperationException();
        int completed = awgUpdateTunnelPeers(currentTunnelHandle, config.toAwgQuickStringResolved(false, false, currentTunnel.isIpv4ResolutionPreferred()));
        return completed == 0;

    }

    @Override
    @Nullable
    protected String getTunnelConfig(final int handle) {
        return awgGetConfig(handle);
    }

    @Override
    protected BackendMode setBackendModeInternal(final BackendMode backendMode) {
        Log.w(TAG, "Backend mode not supported for this backend");
        return backendMode;
    }
}