/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.backend;

import org.amnezia.awg.config.Config;
import org.amnezia.awg.util.NonNullForAll;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
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
     * Get the active mode of the backend.
     *
     * @return The mode of the backend.
     */
    BackendMode getBackendMode();

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


    /**
     * Set the mode of the backend.
     * Primarily use to turn on kill switch for compatible backends.
     *
     * @return The mode of the backend.
     * @throws Exception Exception raised when mode fails to engage.
     */
    BackendMode setBackendMode(BackendMode backendMode) throws Exception;


    abstract class BackendMode {

        private BackendMode() {}


        /**
         * Backend is in kill switch mode, with optional allowIps exclusion meant for private IPs.
         * This mode is only supported for {@link ProxyGoBackend}
         */
        public static final class KillSwitch extends BackendMode {
            private final Set<String> allowedIps;

            /**
             *  @param allowedIps should only be a list of private IPs, or it undermines this mode.
             */
            public KillSwitch(Set<String> allowedIps) {
                this.allowedIps = Set.copyOf(allowedIps);
            }

            public Set<String> getAllowedIps() {
                return allowedIps;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                KillSwitch that = (KillSwitch) o;
                return Objects.equals(allowedIps, that.allowedIps);
            }

            @Override
            public int hashCode() {
                return Objects.hash(allowedIps);
            }

            @Override
            public String toString() {
                return "KillSwitch{allowedIps=" + allowedIps + "}";
            }
        }

        /**
         * Backend mode is not in an active mode.
         */
        public static final class Inactive extends BackendMode {
            public static final Inactive INSTANCE = new Inactive();
            private Inactive() {}

            @Override
            public boolean equals(Object o) {
                return this == o || (o != null && getClass() == o.getClass());
            }

            @Override
            public int hashCode() {
                return 0; // Constant for singleton
            }

            @Override
            public String toString() {
                return "Inactive{}";
            }
        }

        @Override
        public abstract boolean equals(Object o);

        @Override
        public abstract int hashCode();

        @Override
        public abstract String toString();
    }
}
