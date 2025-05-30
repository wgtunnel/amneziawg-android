/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.backend;

import org.amnezia.awg.config.Config;
import org.amnezia.awg.util.NonNullForAll;

import java.util.Collection;
import java.util.Set;

import androidx.annotation.Nullable;

/**
 * Interface for implementations of the AmneziaWG secure network tunnel.
 */

@NonNullForAll
public interface Backend {
    /**
     * Enumerate names of currently-running tunnels.
     *
     * @return The set of running tunnel names.
     */
    Set<String> getRunningTunnelNames();

    /**
     * Get the state of a tunnel.
     *
     * @param tunnel The tunnel to examine the state of.
     * @return The state of the tunnel.
     * @throws Exception Exception raised when retrieving tunnel's state.
     */
    Tunnel.State getState(Tunnel tunnel) throws Exception;

    /**
     * Get the state of the backend.
     *
     * @return The state of the backend.
     * @throws Exception Exception raised when retrieving tunnel's state.
     */
    BackendState getBackendState() throws Exception;

    /**
     * Get statistics about traffic and errors on this tunnel. If the tunnel is not running, the
     * statistics object will be filled with zero values.
     *
     * @param tunnel The tunnel to retrieve statistics for.
     * @return The statistics for the tunnel.
     * @throws Exception Exception raised when retrieving statistics.
     */
    Statistics getStatistics(Tunnel tunnel) throws Exception;

    /**
     * Determine version of underlying backend.
     *
     * @return The version of the backend.
     * @throws Exception Exception raised while retrieving version.
     */
    String getVersion() throws Exception;

    /**
     * Set the state of a tunnel, updating it's configuration. If the tunnel is already up, config
     * may update the running configuration; config may be null when setting the tunnel down.
     *
     * @param tunnel The tunnel to control the state of.
     * @param state  The new state for this tunnel. Must be {@code UP}, {@code DOWN}, or
     *               {@code TOGGLE}.
     * @param config The configuration for this tunnel, may be null if state is {@code DOWN}.
     * @return The updated state of the tunnel.
     * @throws Exception Exception raised while changing state.
     */
    Tunnel.State setState(Tunnel tunnel, Tunnel.State state, @Nullable Config config) throws Exception;


    BackendState setBackendState(BackendState backendState, Collection<String> allowedIps) throws Exception;

    enum BackendState {
        KILL_SWITCH_ACTIVE,
        SERVICE_ACTIVE,
        INACTIVE
    }
}
