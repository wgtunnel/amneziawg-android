/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.config;

import android.content.Context;
import androidx.annotation.Nullable;

import org.amnezia.awg.config.BadConfigException.Location;
import org.amnezia.awg.config.BadConfigException.Reason;
import org.amnezia.awg.config.BadConfigException.Section;
import org.amnezia.awg.config.proxy.Proxy;
import org.amnezia.awg.util.NonNullForAll;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Represents the contents of a awg-quick configuration file, made up of one or more "Interface"
 * sections (combined together), zero or more "Peer" sections (treated individually), and
 * zero or more proxy sections added programmatically.
 * <p>
 * Instances of this class are immutable.
 */
@NonNullForAll
public final class Config {
    private final Interface interfaze;
    private final List<Peer> peers;
    private final List<Proxy> proxies;

    @Nullable private final DnsSettings dsSettings;

    private Config(final Builder builder) {
        interfaze = Objects.requireNonNull(builder.interfaze, "An [Interface] section is required");
        // Defensively copy to ensure immutability even if the Builder is reused.
        peers = List.copyOf(builder.peers);
        proxies = List.copyOf(builder.proxies);
        this.dsSettings = builder.dnsSettings;
    }

    /**
     * Parses a series of "Interface" and "Peer" sections into a {@code Config}. Throws
     * {@link BadConfigException} if the input is not well-formed or contains data that cannot
     * be parsed.
     *
     * @param stream a stream of UTF-8 text that is interpreted as an AmneziaWG configuration
     * @return a {@code Config} instance representing the supplied configuration
     */
    public static Config parse(final InputStream stream)
            throws IOException, BadConfigException {
        return parse(new BufferedReader(new InputStreamReader(stream)));
    }

    /**
     * Parses a series of "Interface" and "Peer" sections into a {@code Config}. Throws
     * {@link BadConfigException} if the input is not well-formed or contains data that cannot
     * be parsed.
     *
     * @param reader a BufferedReader of UTF-8 text that is interpreted as an AmneziaWG configuration
     * @return a {@code Config} instance representing the supplied configuration
     */
    public static Config parse(final BufferedReader reader)
            throws IOException, BadConfigException {
        final Builder builder = new Builder();
        final Collection<String> interfaceLines = new ArrayList<>();
        final Collection<String> peerLines = new ArrayList<>();
        boolean inInterfaceSection = false;
        boolean inPeerSection = false;
        boolean seenInterfaceSection = false;
        @Nullable String line;
        while ((line = reader.readLine()) != null) {
            final int commentIndex = line.indexOf('#');
            if (commentIndex != -1)
                line = line.substring(0, commentIndex);
            line = line.trim();
            if (line.isEmpty())
                continue;
            if (line.startsWith("[")) {
                // Consume all [Peer] lines read so far.
                if (inPeerSection) {
                    builder.parsePeer(peerLines);
                    peerLines.clear();
                }
                if ("[Interface]".equalsIgnoreCase(line)) {
                    inInterfaceSection = true;
                    inPeerSection = false;
                    seenInterfaceSection = true;
                } else if ("[Peer]".equalsIgnoreCase(line)) {
                    inInterfaceSection = false;
                    inPeerSection = true;
                } else {
                    throw new BadConfigException(Section.CONFIG, Location.TOP_LEVEL,
                            Reason.UNKNOWN_SECTION, line);
                }
            } else if (inInterfaceSection) {
                interfaceLines.add(line);
            } else if (inPeerSection) {
                peerLines.add(line);
            } else {
                throw new BadConfigException(Section.CONFIG, Location.TOP_LEVEL,
                        Reason.UNKNOWN_SECTION, line);
            }
        }
        if (inPeerSection)
            builder.parsePeer(peerLines);
        if (!seenInterfaceSection)
            throw new BadConfigException(Section.CONFIG, Location.TOP_LEVEL,
                    Reason.MISSING_SECTION, null);
        // Combine all [Interface] sections in the file.
        builder.parseInterface(interfaceLines);
        return builder.build();
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Config other))
            return false;
        return interfaze.equals(other.interfaze)
                && peers.equals(other.peers)
                && proxies.equals(other.proxies);
    }

    /**
     * Returns the interface section of the configuration.
     *
     * @return the interface configuration
     */
    public Interface getInterface() {
        return interfaze;
    }

    /**
     * Returns a list of the configuration's peer sections.
     *
     * @return a list of {@link Peer}s
     */
    public List<Peer> getPeers() {
        return peers;
    }

    /**
     * Returns a list of the configuration's proxy sections.
     *
     * @return a list of {@link Proxy}s
     */
    public List<Proxy> getProxies() {
        return proxies;
    }

    @Nullable
    public DnsSettings getDnsSettings() {
        return dsSettings;
    }

    @Override
    public int hashCode() {
        return Objects.hash(interfaze, peers, proxies);
    }

    /**
     * Converts the {@code Config} into a string suitable for debugging purposes.
     *
     * @return a concise single-line identifier for the {@code Config}
     */
    @Override
    public String toString() {
        return "(Config " + interfaze + " (" + peers.size() + " peers, " + proxies.size() + " proxies))";
    }

    /**
     * Converts the {@code Config} into a string suitable for use as a {@code awg-quick}
     * configuration file.
     *
     * @return the {@code Config} represented as one [Interface], zero or more [Peer], and zero or
     * more [Socks5] or [Http] sections
     */
    public String toAwgQuickString(final Boolean includeScripts, final  Boolean includeProxies) {
        final StringBuilder sb = new StringBuilder();
        sb.append("[Interface]\n").append(interfaze.toAwgQuickString(includeScripts));
        for (final Peer peer : peers) {
            sb.append("\n[Peer]\n").append( peer.toAwgQuickString());
        }
        if (includeProxies) {
            for (final Proxy proxy : proxies)
                sb.append("\n").append(proxy.toQuickString());
        }
        return sb.toString();
    }

    /**
     * Converts the {@code Config} into a string suitable for use as a standard {@code wg-quick}
     * configuration file, ignoring Amnezia-specific properties.
     *
     * @return the {@code Config} represented as one [Interface] and zero or more [Peer] sections
     */
    public String toWgQuickString(final Boolean includeScripts) {
        final StringBuilder sb = new StringBuilder();
        sb.append("[Interface]\n").append(interfaze.toWgQuickString(includeScripts));
        for (final Peer peer : peers) {
            sb.append("\n[Peer]\n").append(peer.toAwgQuickString());
        }
        return sb.toString();
    }

    /**
     * Converts the {@code Config} into a string suitable for use as a {@code awg-quick}
     * configuration file with resolved endpoints.
     *
     * @return the {@code Config} represented as one [Interface], zero or more [Peer], and zero or
     * more [Socks5] or [Http] sections
     */
    public String toAwgQuickStringResolved(final Boolean includeScripts, final Boolean includeProxies, final Boolean preferIpv4, Context context) {
        final StringBuilder sb = new StringBuilder();
        sb.append("[Interface]\n").append(interfaze.toAwgQuickString(includeScripts));
        for (final Peer peer : peers) {
            sb.append("\n[Peer]\n").append(peer.toAwgQuickStringResolved(preferIpv4, context));
        }
        if (includeProxies) {
            for (final Proxy proxy : proxies)
                sb.append("\n").append(proxy.toQuickString());
        }
        return sb.toString();
    }

    /**
     * Serializes the {@code Config} for use with the AmneziaWG cross-platform userspace API.
     * Note: Proxy sections are not included in this format, as they are specific to awg-quick.
     *
     * @return the {@code Config} represented as a series of "key=value" lines
     */
    public String toAwgUserspaceString(Boolean preferIpv4, Context context) {
        final StringBuilder sb = new StringBuilder();
        sb.append(interfaze.toAwgUserspaceString());
        sb.append("replace_peers=true\n");
        for (final Peer peer : peers)
            sb.append(peer.toAwgUserspaceString(preferIpv4, context));
        return sb.toString();
    }


    @SuppressWarnings("UnusedReturnValue")
    public static final class Builder {
        private final ArrayList<Peer> peers = new ArrayList<>();
        private final ArrayList<Proxy> proxies = new ArrayList<>();
        @Nullable private Interface interfaze;

        @Nullable private DnsSettings dnsSettings;

        public Builder addPeer(final Peer peer) {
            peers.add(peer);
            return this;
        }

        public Builder setDnsSettings(@Nullable final DnsSettings dnsSettings) {
            this.dnsSettings = dnsSettings;
            return this;
        }

        public Builder addPeers(final Collection<Peer> peers) {
            this.peers.addAll(peers);
            return this;
        }

        public Builder addProxy(final Proxy proxy) {
            proxies.add(proxy);
            return this;
        }

        public Builder addProxies(final Collection<Proxy> proxies) {
            this.proxies.addAll(proxies);
            return this;
        }

        public Config build() {
            if (interfaze == null)
                throw new IllegalArgumentException("An [Interface] section is required");
            return new Config(this);
        }

        public Builder parseInterface(final Iterable<? extends CharSequence> lines)
                throws BadConfigException {
            return setInterface(Interface.parse(lines));
        }

        public Builder parsePeer(final Iterable<? extends CharSequence> lines)
                throws BadConfigException {
            return addPeer(Peer.parse(lines));
        }

        public Builder setInterface(final Interface interfaze) {
            this.interfaze = interfaze;
            return this;
        }
    }
}