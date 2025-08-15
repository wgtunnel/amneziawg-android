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
import org.amnezia.awg.config.InetEndpoint;
import org.amnezia.awg.config.InetNetwork;
import org.amnezia.awg.config.Peer;
import org.amnezia.awg.util.NonNullForAll;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.amnezia.awg.GoBackend.*;

@NonNullForAll
public final class GoBackend extends AbstractBackend {
    private static final String TAG = "AmneziaWG/GoBackend";
    private static final int DNS_RESOLUTION_RETRIES = 3;

    public GoBackend(final Context context, final TunnelActionHandler tunnelActionHandler) {
        super(context, tunnelActionHandler);
    }

    @Override
    protected void configureAndStartTunnel(final Tunnel tunnel, final Config config) throws Exception {
        if (VpnService.prepare(context) != null) {
            throw new BackendException(BackendException.Reason.VPN_NOT_AUTHORIZED);
        }
        final VpnService service;
        if (!vpnService.isDone()) {
            Log.d(TAG, "Requesting to start VpnService");
            context.startService(new Intent(context, VpnService.class));
        }
        try {
            service = vpnService.get(2, TimeUnit.SECONDS);
        } catch (final TimeoutException e) {
            final Exception be = new BackendException(BackendException.Reason.UNABLE_TO_START_VPN);
            be.initCause(e);
            throw be;
        }
        service.setOwner(this);

        if (currentTunnelHandle != -1) {
            Log.w(TAG, "Tunnel already up");
            return;
        }

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
                    Thread.sleep(500L * (1 << i));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BackendException(BackendException.Reason.DNS_RESOLUTION_FAILURE, "Interrupted during DNS retry");
                }
            } else {
                throw new BackendException(BackendException.Reason.DNS_RESOLUTION_FAILURE, failedEndpoints.get(0).getHost());
            }
        }

        final String goConfig = config.toAwgUserspaceString(tunnel.isIpv4ResolutionPreferred());
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
        service.deactivateKillSwitch();
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
    protected BackendStatus setBackendStatusInternal(final BackendStatus backendStatus) throws Exception {
        if (backendStatus instanceof BackendStatus.KillSwitchActive killSwitch) {
            Log.d(TAG, "Starting kill switch");
            activateKillSwitch(killSwitch.getAllowedIps());
        } else if (backendStatus instanceof BackendStatus.ServiceActive) {
            Log.d(TAG, "Starting service");
            activateService();
        } else if (backendStatus instanceof BackendStatus.Inactive) {
            Log.d(TAG, "Inactive, shutting down");
            shutdown();
        } else {
            throw new IllegalStateException("Unknown BackendStatus subclass: " + backendStatus.getClass().getName());
        }
        return backendStatus;
    }
}