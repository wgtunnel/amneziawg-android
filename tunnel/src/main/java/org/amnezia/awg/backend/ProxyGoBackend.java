/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.backend;

import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;
import org.amnezia.awg.config.Config;
import org.amnezia.awg.util.NonNullForAll;

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
        final String quickConfig = config.toAwgQuickString(false);
        Log.d(TAG, "Starting proxy with config:\n" + quickConfig);
        tunnelActionHandler.runPreUp(config.getInterface().getPreUp());
        String packageName = context.getPackageName();
        Log.d(TAG, "App package name " + packageName);
        awgStartWireproxy(quickConfig);
        currentTunnelHandle = 1;
        tunnelActionHandler.runPostUp(config.getInterface().getPostUp());
        if (currentTunnelHandle < 0) {
            throw new BackendException(BackendException.Reason.GO_ACTIVATION_ERROR_CODE, currentTunnelHandle);
        }
    }

    @Override
    protected void stopTunnel(final Tunnel tunnel, @Nullable final Config config) throws Exception {
        if (currentTunnelHandle == -1) {
            Log.w(TAG, "Tunnel already down");
            return;
        }
        tunnelActionHandler.runPreDown(config != null ? config.getInterface().getPreDown() : null);
        awgStopWireproxy();
        currentTunnelHandle = -1;
        tunnelActionHandler.runPostDown(config != null ? config.getInterface().getPostDown() : null);
    }

    @Override
    protected BackendStatus setBackendStatusInternal(final BackendStatus backendStatus) throws Exception {
        if (backendStatus instanceof BackendStatus.KillSwitchActive killSwitch) {
            Log.d(TAG, "Starting kill switch");
            activateKillSwitch(killSwitch.getAllowedIps());
        } else if (backendStatus instanceof BackendStatus.ServiceActive) {
            Log.d(TAG, "ServiceActive not applicable, setting to Inactive");
            deactivateKillSwitch(); // Ensure no killswitch is active
            this.backendStatus = BackendStatus.Inactive.INSTANCE;
            return this.backendStatus;
        } else if (backendStatus instanceof BackendStatus.Inactive) {
            Log.d(TAG, "Setting to Inactive");
            shutdown();
        } else {
            throw new IllegalStateException("Unknown BackendStatus subclass: " + backendStatus.getClass().getName());
        }
        return this.backendStatus;
    }
}