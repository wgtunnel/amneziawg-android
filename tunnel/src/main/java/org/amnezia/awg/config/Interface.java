/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.config;

import org.amnezia.awg.config.BadConfigException.Location;
import org.amnezia.awg.config.BadConfigException.Reason;
import org.amnezia.awg.config.BadConfigException.Section;
import org.amnezia.awg.crypto.Key;
import org.amnezia.awg.crypto.KeyFormatException;
import org.amnezia.awg.crypto.KeyPair;
import org.amnezia.awg.util.NonNullForAll;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import androidx.annotation.Nullable;

/**
 * Represents the configuration for an AmneziaWG interface (an [Interface] block). Interfaces must
 * have a private key (used to initialize a {@code KeyPair}), and may optionally have several other
 * attributes.
 * <p>
 * Instances of this class are immutable.
 */
@NonNullForAll
public final class Interface {
    private static final int MAX_UDP_PORT = 65535;
    private static final int MIN_UDP_PORT = 0;

    private final Set<InetNetwork> addresses;
    private final Set<InetAddress> dnsServers;
    private final Set<String> dnsSearchDomains;
    private final Set<String> excludedApplications;
    private final Set<String> includedApplications;
    private final KeyPair keyPair;
    private final Optional<Integer> listenPort;
    private final Optional<Integer> mtu;
    private final Optional<Integer> junkPacketCount;
    private final Optional<Integer> junkPacketMinSize;
    private final Optional<Integer> junkPacketMaxSize;
    private final Optional<Integer> initPacketJunkSize;
    private final Optional<Integer> responsePacketJunkSize;
    private final Optional<Long> initPacketMagicHeader;
    private final Optional<Long> responsePacketMagicHeader;
    private final Optional<Long> underloadPacketMagicHeader;
    private final Optional<Long> transportPacketMagicHeader;
    private final Optional<String> i1;
    private final Optional<String> i2;
    private final Optional<String> i3;
    private final Optional<String> i4;
    private final Optional<String> i5;
    private final Optional<String> j1;
    private final Optional<String> j2;
    private final Optional<String> j3;
    private final Optional<Integer> itime;
    private final Set<String> blockedDomains;
    private final Optional<Boolean> domainBlockingEnabled;
    private final List<String> preUp;
    private final List<String> postUp;
    private final List<String> preDown;
    private final List<String> postDown;

    public enum DnsProtocol {
        PLAIN, DOH, DOT;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ENGLISH);
        }
    }

    private Interface(final Builder builder) {
        // Defensively copy to ensure immutability even if the Builder is reused.
        addresses = Collections.unmodifiableSet(new LinkedHashSet<>(builder.addresses));
        dnsServers = Collections.unmodifiableSet(new LinkedHashSet<>(builder.dnsServers));
        dnsSearchDomains = Collections.unmodifiableSet(new LinkedHashSet<>(builder.dnsSearchDomains));
        excludedApplications = Collections.unmodifiableSet(new LinkedHashSet<>(builder.excludedApplications));
        includedApplications = Collections.unmodifiableSet(new LinkedHashSet<>(builder.includedApplications));
        keyPair = Objects.requireNonNull(builder.keyPair, "Interfaces must have a private key");
        listenPort = builder.listenPort;
        mtu = builder.mtu;
        junkPacketCount = builder.junkPacketCount;
        junkPacketMinSize = builder.junkPacketMinSize;
        junkPacketMaxSize = builder.junkPacketMaxSize;
        initPacketJunkSize = builder.initPacketJunkSize;
        responsePacketJunkSize = builder.responsePacketJunkSize;
        initPacketMagicHeader = builder.initPacketMagicHeader;
        responsePacketMagicHeader = builder.responsePacketMagicHeader;
        underloadPacketMagicHeader = builder.underloadPacketMagicHeader;
        transportPacketMagicHeader = builder.transportPacketMagicHeader;
        i1 = builder.i1;
        i2 = builder.i2;
        i3 = builder.i3;
        i4 = builder.i4;
        i5 = builder.i5;
        j1 = builder.j1;
        j2 = builder.j2;
        j3 = builder.j3;
        itime = builder.itime;
        blockedDomains = Collections.unmodifiableSet(new LinkedHashSet<>(builder.blockedDomains));
        domainBlockingEnabled = builder.domainBlockingEnabled;
        preUp = List.copyOf(builder.preUp);
        postUp = List.copyOf(builder.postUp);
        preDown = List.copyOf(builder.preDown);
        postDown = List.copyOf(builder.postDown);
    }

    /**
     * Parses an series of "KEY = VALUE" lines into an {@code Interface}. Throws
     * {@link ParseException} if the input is not well-formed or contains unknown attributes.
     *
     * @param lines An iterable sequence of lines, containing at least a private key attribute
     * @return An {@code Interface} with all of the attributes from {@code lines} set
     */
    public static Interface parse(final Iterable<? extends CharSequence> lines)
            throws BadConfigException {
        final Builder builder = new Builder();
        for (final CharSequence line : lines) {
            final Attribute attribute = Attribute.parse(line).orElseThrow(() ->
                    new BadConfigException(Section.INTERFACE, Location.TOP_LEVEL,
                            Reason.SYNTAX_ERROR, line));
            switch (attribute.getKey().toLowerCase(Locale.ENGLISH)) {
                case "address":
                    builder.parseAddresses(attribute.getValue());
                    break;
                case "dns":
                    builder.parseDnsServers(attribute.getValue());
                    break;
                case "blockeddomains":
                    builder.parseBlockedDomains(attribute.getValue());
                    break;
                case "domainblockingenabled":
                    builder.parseDomainBlockingEnabled(attribute.getValue());
                    break;
                case "excludedapplications":
                    builder.parseExcludedApplications(attribute.getValue());
                    break;
                case "includedapplications":
                    builder.parseIncludedApplications(attribute.getValue());
                    break;
                case "listenport":
                    builder.parseListenPort(attribute.getValue());
                    break;
                case "mtu":
                    builder.parseMtu(attribute.getValue());
                    break;
                case "privatekey":
                    builder.parsePrivateKey(attribute.getValue());
                    break;
                case "jc":
                    builder.parseJunkPacketCount(attribute.getValue());
                    break;
                case "jmin":
                    builder.parseJunkPacketMinSize(attribute.getValue());
                    break;
                case "jmax":
                    builder.parseJunkPacketMaxSize(attribute.getValue());
                    break;
                case "s1":
                    builder.parseInitPacketJunkSize(attribute.getValue());
                    break;
                case "s2":
                    builder.parseResponsePacketJunkSize(attribute.getValue());
                    break;
                case "h1":
                    builder.parseInitPacketMagicHeader(attribute.getValue());
                    break;
                case "h2":
                    builder.parseResponsePacketMagicHeader(attribute.getValue());
                    break;
                case "h3":
                    builder.parseUnderloadPacketMagicHeader(attribute.getValue());
                    break;
                case "h4":
                    builder.parseTransportPacketMagicHeader(attribute.getValue());
                    break;
                case "i1":
                    builder.parseI1(attribute.getValue());
                    break;
                case "i2":
                    builder.parseI2(attribute.getValue());
                    break;
                case "i3":
                    builder.parseI3(attribute.getValue());
                    break;
                case "i4":
                    builder.parseI4(attribute.getValue());
                    break;
                case "i5":
                    builder.parseI5(attribute.getValue());
                    break;
                case "j1":
                    builder.parseJ1(attribute.getValue());
                    break;
                case "j2":
                    builder.parseJ2(attribute.getValue());
                    break;
                case "j3":
                    builder.parseJ3(attribute.getValue());
                    break;
                case "itime":
                    builder.parseItime(attribute.getValue());
                    break;
                case "preup":
                    builder.parsePreUp(attribute.getValue());
                    break;
                case "postup":
                    builder.parsePostUp(attribute.getValue());
                    break;
                case "predown":
                    builder.parsePreDown(attribute.getValue());
                    break;
                case "postdown":
                    builder.parsePostDown(attribute.getValue());
                    break;
                default:
                    throw new BadConfigException(Section.INTERFACE, Location.TOP_LEVEL,
                            Reason.UNKNOWN_ATTRIBUTE, attribute.getKey());
            }
        }
        return builder.build();
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Interface other))
            return false;
        return addresses.equals(other.addresses)
                && dnsServers.equals(other.dnsServers)
                && dnsSearchDomains.equals(other.dnsSearchDomains)
                && excludedApplications.equals(other.excludedApplications)
                && includedApplications.equals(other.includedApplications)
                && keyPair.equals(other.keyPair)
                && listenPort.equals(other.listenPort)
                && mtu.equals(other.mtu)
                && junkPacketCount.equals(other.junkPacketCount)
                && junkPacketMinSize.equals(other.junkPacketMinSize)
                && junkPacketMaxSize.equals(other.junkPacketMaxSize)
                && initPacketJunkSize.equals(other.initPacketJunkSize)
                && responsePacketJunkSize.equals(other.responsePacketJunkSize)
                && initPacketMagicHeader.equals(other.initPacketMagicHeader)
                && responsePacketMagicHeader.equals(other.responsePacketMagicHeader)
                && underloadPacketMagicHeader.equals(other.underloadPacketMagicHeader)
                && transportPacketMagicHeader.equals(other.transportPacketMagicHeader)
                && i1.equals(other.i1)
                && i2.equals(other.i2)
                && i3.equals(other.i3)
                && i4.equals(other.i4)
                && i5.equals(other.i5)
                && j1.equals(other.j1)
                && j2.equals(other.j2)
                && j3.equals(other.j3)
                && itime.equals(other.itime)
                && blockedDomains.equals(other.blockedDomains)
                && domainBlockingEnabled.equals(other.domainBlockingEnabled)
                && preUp.equals(other.preUp)
                && postUp.equals(other.postUp)
                && preDown.equals(other.preDown)
                && postDown.equals(other.postDown);
    }

    /**
     * Returns the set of IP addresses assigned to the interface.
     *
     * @return a set of {@link InetNetwork}s
     */
    public Set<InetNetwork> getAddresses() {
        // The collection is already immutable.
        return addresses;
    }

    /**
     * Returns the set of DNS servers associated with the interface.
     *
     * @return a set of {@link InetAddress}es
     */
    public Set<InetAddress> getDnsServers() {
        // The collection is already immutable.
        return dnsServers;
    }

    /**
     * Returns the set of DNS search domains associated with the interface.
     *
     * @return a set of strings
     */
    public Set<String> getDnsSearchDomains() {
        // The collection is already immutable.
        return dnsSearchDomains;
    }

    /**
     * Returns the set of applications excluded from using the interface.
     *
     * @return a set of package names
     */
    public Set<String> getExcludedApplications() {
        // The collection is already immutable.
        return excludedApplications;
    }

    /**
     * Returns the set of applications included exclusively for using the interface.
     *
     * @return a set of package names
     */
    public Set<String> getIncludedApplications() {
        // The collection is already immutable.
        return includedApplications;
    }

    /**
     * Returns the public/private key pair used by the interface.
     *
     * @return a key pair
     */
    public KeyPair getKeyPair() {
        return keyPair;
    }

    /**
     * Returns the UDP port number that the AmneziaWG interface will listen on.
     *
     * @return a UDP port number, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getListenPort() {
        return listenPort;
    }

    /**
     * Returns the MTU used for the AmneziaWG interface.
     *
     * @return the MTU, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getMtu() {
        return mtu;
    }

    /**
     * Returns the junkPacketCount used for the AmneziaWG interface.
     *
     * @return the junkPacketCount, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getJunkPacketCount() {
        return junkPacketCount;
    }

    /**
     * Returns the junkPacketMinSize used for the AmneziaWG interface.
     *
     * @return the junkPacketMinSize, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getJunkPacketMinSize() {
        return junkPacketMinSize;
    }

    /**
     * Returns the junkPacketMaxSize used for the AmneziaWG interface.
     *
     * @return the junkPacketMaxSize, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getJunkPacketMaxSize() {
        return junkPacketMaxSize;
    }

    /**
     * Returns the initPacketJunkSize used for the AmneziaWG interface.
     *
     * @return the initPacketJunkSize, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getInitPacketJunkSize() {
        return initPacketJunkSize;
    }

    /**
     * Returns the responsePacketJunkSize used for the AmneziaWG interface.
     *
     * @return the responsePacketJunkSize, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getResponsePacketJunkSize() {
        return responsePacketJunkSize;
    }

    /**
     * Returns the initPacketMagicHeader used for the AmneziaWG interface.
     *
     * @return the initPacketMagicHeader, or {@code Optional.empty()} if none is configured
     */
    public Optional<Long> getInitPacketMagicHeader() {
        return initPacketMagicHeader;
    }

    /**
     * Returns the responsePacketMagicHeader used for the AmneziaWG interface.
     *
     * @return the responsePacketMagicHeader, or {@code Optional.empty()} if none is configured
     */
    public Optional<Long> getResponsePacketMagicHeader() {
        return responsePacketMagicHeader;
    }

    /**
     * Returns the underloadPacketMagicHeader used for the AmneziaWG interface.
     *
     * @return the underloadPacketMagicHeader, or {@code Optional.empty()} if none is configured
     */
    public Optional<Long> getUnderloadPacketMagicHeader() {
        return underloadPacketMagicHeader;
    }

    /**
     * Returns the transportPacketMagicHeader used for the AmneziaWG interface.
     *
     * @return the transportPacketMagicHeader, or {@code Optional.empty()} if none is configured
     */
    public Optional<Long> getTransportPacketMagicHeader() {
        return transportPacketMagicHeader;
    }

    /**
     * Returns the I1 used for the AmneziaWG interface.
     *
     * @return the I1, or {@code Optional.empty()} if none is configured
     */
    public Optional<String> getI1() {
        return i1;
    }

    /**
     * Returns the I2 used for the AmneziaWG interface.
     *
     * @return the I2, or {@code Optional.empty()} if none is configured
     */
    public Optional<String> getI2() {
        return i2;
    }

    /**
     * Returns the I3 used for the AmneziaWG interface.
     *
     * @return the I3, or {@code Optional.empty()} if none is configured
     */
    public Optional<String> getI3() {
        return i3;
    }

    /**
     * Returns the I4 used for the AmneziaWG interface.
     *
     * @return the I4, or {@code Optional.empty()} if none is configured
     */
    public Optional<String> getI4() {
        return i4;
    }

    /**
     * Returns the I5 used for the AmneziaWG interface.
     *
     * @return the I5, or {@code Optional.empty()} if none is configured
     */
    public Optional<String> getI5() {
        return i5;
    }

    /**
     * Returns the J1 used for the AmneziaWG interface.
     *
     * @return the J1, or {@code Optional.empty()} if none is configured
     */
    public Optional<String> getJ1() {
        return j1;
    }

    /**
     * Returns the J2 used for the AmneziaWG interface.
     *
     * @return the J2, or {@code Optional.empty()} if none is configured
     */
    public Optional<String> getJ2() {
        return j2;
    }

    /**
     * Returns the J3 used for the AmneziaWG interface.
     *
     * @return the J3, or {@code Optional.empty()} if none is configured
     */
    public Optional<String> getJ3() {
        return j3;
    }

    /**
     * Returns the Itime used for the AmneziaWG interface.
     *
     * @return the Itime, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getItime() {
        return itime;
    }


    /**
     * Returns the list of blocked domains associated with the interface.
     *
     * @return a set of strings
     */
    public Set<String> getBlockedDomains() {
        // The collection is already immutable.
        return blockedDomains;
    }

    /**
     * Returns whether domain blocking is enabled for the interface.
     *
     * @return the preferIpv6Dns, or {@code Optional.empty()} if none is configured
     */
    public Optional<Boolean> getDomainBlockingEnabled() {
        return domainBlockingEnabled;
    }

    public List<String> getPreUp() {
        return preUp;
    }

    public List<String> getPostUp() {
        return postUp;
    }

    public List<String> getPreDown() {
        return preDown;
    }

    public List<String> getPostDown() {
        return postDown;
    }


    @Override
    public int hashCode() {
        int hash = 1;
        hash = 31 * hash + addresses.hashCode();
        hash = 31 * hash + dnsServers.hashCode();
        hash = 31 * hash + dnsSearchDomains.hashCode();
        hash = 31 * hash + excludedApplications.hashCode();
        hash = 31 * hash + includedApplications.hashCode();
        hash = 31 * hash + keyPair.hashCode();
        hash = 31 * hash + listenPort.hashCode();
        hash = 31 * hash + mtu.hashCode();
        hash = 31 * hash + junkPacketCount.hashCode();
        hash = 31 * hash + junkPacketMinSize.hashCode();
        hash = 31 * hash + junkPacketMaxSize.hashCode();
        hash = 31 * hash + initPacketJunkSize.hashCode();
        hash = 31 * hash + responsePacketJunkSize.hashCode();
        hash = 31 * hash + initPacketMagicHeader.hashCode();
        hash = 31 * hash + responsePacketMagicHeader.hashCode();
        hash = 31 * hash + underloadPacketMagicHeader.hashCode();
        hash = 31 * hash + transportPacketMagicHeader.hashCode();
        hash = 31 * hash + i1.hashCode();
        hash = 31 * hash + i2.hashCode();
        hash = 31 * hash + i3.hashCode();
        hash = 31 * hash + i4.hashCode();
        hash = 31 * hash + i5.hashCode();
        hash = 31 * hash + j1.hashCode();
        hash = 31 * hash + j2.hashCode();
        hash = 31 * hash + j3.hashCode();
        hash = 31 * hash + itime.hashCode();
        hash = 31 * hash + blockedDomains.hashCode();
        hash = 31 * hash + domainBlockingEnabled.hashCode();
        hash = 31 * hash + preUp.hashCode();
        hash = 31 * hash + postUp.hashCode();
        hash = 31 * hash + preDown.hashCode();
        hash = 31 * hash + postDown.hashCode();
        return hash;
    }

    /**
     * Converts the {@code Interface} into a string suitable for debugging purposes. The {@code
     * Interface} is identified by its public key and (if set) the port used for its UDP socket.
     *
     * @return A concise single-line identifier for the {@code Interface}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("(Interface ");
        sb.append(keyPair.getPublicKey().toBase64());
        listenPort.ifPresent(lp -> sb.append(" @").append(lp));
        sb.append(')');
        return sb.toString();
    }

    /**
     * Converts the {@code Interface} into a string suitable for inclusion in a {@code awg-quick}
     * configuration file.
     *
     * @return The {@code Interface} represented as a series of "Key = Value" lines
     */
    public String toAwgQuickString(final Boolean includeScripts) {
        final StringBuilder sb = new StringBuilder();
        if (!addresses.isEmpty())
            sb.append("Address = ").append(Attribute.join(addresses)).append('\n');
        if (!dnsServers.isEmpty()) {
            final List<String> dnsServerStrings = dnsServers.stream().map(InetAddress::getHostAddress).collect(Collectors.toList());
            dnsServerStrings.addAll(dnsSearchDomains);
            sb.append("DNS = ").append(Attribute.join(dnsServerStrings)).append('\n');
        }
        if (!blockedDomains.isEmpty())
            sb.append("BlockedDomains = ").append(Attribute.join(blockedDomains)).append('\n');
        domainBlockingEnabled.ifPresent(pref -> sb.append("DomainBlockingEnabled = ").append(pref).append('\n'));
        if (!excludedApplications.isEmpty())
            sb.append("ExcludedApplications = ").append(Attribute.join(excludedApplications)).append('\n');
        if (!includedApplications.isEmpty())
            sb.append("IncludedApplications = ").append(Attribute.join(includedApplications)).append('\n');
        listenPort.ifPresent(lp -> sb.append("ListenPort = ").append(lp).append('\n'));
        mtu.ifPresent(m -> sb.append("MTU = ").append(m).append('\n'));
        junkPacketCount.ifPresent(jc -> sb.append("Jc = ").append(jc).append('\n'));
        junkPacketMinSize.ifPresent(jmin -> sb.append("Jmin = ").append(jmin).append('\n'));
        junkPacketMaxSize.ifPresent(jmax -> sb.append("Jmax = ").append(jmax).append('\n'));
        initPacketJunkSize.ifPresent(s1 -> sb.append("S1 = ").append(s1).append('\n'));
        responsePacketJunkSize.ifPresent(s2 -> sb.append("S2 = ").append(s2).append('\n'));
        initPacketMagicHeader.ifPresent(h1 -> sb.append("H1 = ").append(h1).append('\n'));
        responsePacketMagicHeader.ifPresent(h2 -> sb.append("H2 = ").append(h2).append('\n'));
        underloadPacketMagicHeader.ifPresent(h3 -> sb.append("H3 = ").append(h3).append('\n'));
        transportPacketMagicHeader.ifPresent(h4 -> sb.append("H4 = ").append(h4).append('\n'));
        i1.ifPresent(i -> sb.append("I1 = ").append(i).append('\n'));
        i2.ifPresent(i -> sb.append("I2 = ").append(i).append('\n'));
        i3.ifPresent(i -> sb.append("I3 = ").append(i).append('\n'));
        i4.ifPresent(i -> sb.append("I4 = ").append(i).append('\n'));
        i5.ifPresent(i -> sb.append("I5 = ").append(i).append('\n'));
        j1.ifPresent(j -> sb.append("J1 = ").append(j).append('\n'));
        j2.ifPresent(j -> sb.append("J2 = ").append(j).append('\n'));
        j3.ifPresent(j -> sb.append("J3 = ").append(j).append('\n'));
        itime.ifPresent(it -> sb.append("ITime = ").append(it).append('\n'));
        sb.append("PrivateKey = ").append(keyPair.getPrivateKey().toBase64()).append('\n');
        if(includeScripts) {
            for (final String script : preUp)
                sb.append("PreUp = ").append(script).append('\n');
            for (final String script : postUp)
                sb.append("PostUp = ").append(script).append('\n');
            for (final String script : preDown)
                sb.append("PreDown = ").append(script).append('\n');
            for (final String script : postDown)
                sb.append("PostDown = ").append(script).append('\n');
        }
        return sb.toString();
    }

    /**
     * Serializes the {@code Interface} for use with the AmneziaWG cross-platform userspace API.
     * Note that not all attributes are included in this representation.
     *
     * @return the {@code Interface} represented as a series of "KEY=VALUE" lines
     */
    public String toAwgUserspaceString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("private_key=").append(keyPair.getPrivateKey().toHex()).append('\n');
        listenPort.ifPresent(lp -> sb.append("listen_port=").append(lp).append('\n'));
        junkPacketCount.ifPresent(jc -> sb.append("jc=").append(jc).append('\n'));
        junkPacketMinSize.ifPresent(jmin -> sb.append("jmin=").append(jmin).append('\n'));
        junkPacketMaxSize.ifPresent(jmax -> sb.append("jmax=").append(jmax).append('\n'));
        initPacketJunkSize.ifPresent(s1 -> sb.append("s1=").append(s1).append('\n'));
        responsePacketJunkSize.ifPresent(s2 -> sb.append("s2=").append(s2).append('\n'));
        initPacketMagicHeader.ifPresent(h1 -> sb.append("h1=").append(h1).append('\n'));
        responsePacketMagicHeader.ifPresent(h2 -> sb.append("h2=").append(h2).append('\n'));
        underloadPacketMagicHeader.ifPresent(h3 -> sb.append("h3=").append(h3).append('\n'));
        transportPacketMagicHeader.ifPresent(h4 -> sb.append("h4=").append(h4).append('\n'));
        i1.ifPresent(i -> sb.append("i1=").append(i).append('\n'));
        i2.ifPresent(i -> sb.append("i2=").append(i).append('\n'));
        i3.ifPresent(i -> sb.append("i3=").append(i).append('\n'));
        i4.ifPresent(i -> sb.append("i4=").append(i).append('\n'));
        i5.ifPresent(i -> sb.append("i5=").append(i).append('\n'));
        j1.ifPresent(j -> sb.append("j1=").append(j).append('\n'));
        j2.ifPresent(j -> sb.append("j2=").append(j).append('\n'));
        j3.ifPresent(j -> sb.append("j3=").append(j).append('\n'));
        itime.ifPresent(it -> sb.append("itime=").append(it).append('\n'));
        return sb.toString();
    }

    /**
     * Creates a new Interface with updated DNS settings, copying all other fields from this instance.
     *
     * @param protocol the new DNS protocol
     * @param additionalServers the new additional DNS servers
     * @param preferIpv6 whether to prefer IPv6 DNS
     * @return a new Interface with the updated DNS settings
     */
    public Interface withUpdatedDns(DnsProtocol protocol, Set<String> additionalServers, boolean preferIpv6) {
        Builder builder = new Builder();
        builder.addresses.addAll(this.addresses);
        builder.dnsServers.addAll(this.dnsServers);
        builder.dnsSearchDomains.addAll(this.dnsSearchDomains);
        builder.excludedApplications.addAll(this.excludedApplications);
        builder.includedApplications.addAll(this.includedApplications);
        builder.keyPair = this.keyPair;
        builder.listenPort = this.listenPort;
        builder.mtu = this.mtu;
        builder.junkPacketCount = this.junkPacketCount;
        builder.junkPacketMinSize = this.junkPacketMinSize;
        builder.junkPacketMaxSize = this.junkPacketMaxSize;
        builder.initPacketJunkSize = this.initPacketJunkSize;
        builder.responsePacketJunkSize = this.responsePacketJunkSize;
        builder.initPacketMagicHeader = this.initPacketMagicHeader;
        builder.responsePacketMagicHeader = this.responsePacketMagicHeader;
        builder.underloadPacketMagicHeader = this.underloadPacketMagicHeader;
        builder.transportPacketMagicHeader = this.transportPacketMagicHeader;
        builder.i1 = this.i1;
        builder.i2 = this.i2;
        builder.i3 = this.i3;
        builder.i4 = this.i4;
        builder.i5 = this.i5;
        builder.j1 = this.j1;
        builder.j2 = this.j2;
        builder.j3 = this.j3;
        builder.itime = this.itime;
        builder.blockedDomains.addAll(additionalServers);
        builder.domainBlockingEnabled = Optional.of(preferIpv6);
        builder.preUp.addAll(this.preUp);
        builder.postUp.addAll(this.postUp);
        builder.preDown.addAll(this.preDown);
        builder.postDown.addAll(this.postDown);
        try {
            return builder.build();
        } catch (BadConfigException e) {
            throw new IllegalStateException("Failed to build updated Interface", e);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public static final class Builder {
        // Defaults to an empty set.
        private final Set<InetNetwork> addresses = new LinkedHashSet<>();
        // Defaults to an empty set.
        private final Set<InetAddress> dnsServers = new LinkedHashSet<>();
        // Defaults to an empty set.
        private final Set<String> dnsSearchDomains = new LinkedHashSet<>();
        // Defaults to an empty set.
        private final Set<String> excludedApplications = new LinkedHashSet<>();
        // Defaults to an empty set.
        private final Set<String> includedApplications = new LinkedHashSet<>();
        // No default; must be provided before building.
        @Nullable private KeyPair keyPair;
        // Defaults to not present.
        private Optional<Integer> listenPort = Optional.empty();
        // Defaults to not present.
        private Optional<Integer> mtu = Optional.empty();
        // Defaults to not present.
        private Optional<Integer> junkPacketCount = Optional.empty();
        // Defaults to not present.
        private Optional<Integer> junkPacketMinSize = Optional.empty();
        // Defaults to not present.
        private Optional<Integer> junkPacketMaxSize = Optional.empty();
        // Defaults to not present.
        private Optional<Integer> initPacketJunkSize = Optional.empty();
        // Defaults to not present.
        private Optional<Integer> responsePacketJunkSize = Optional.empty();
        // Defaults to not present.
        private Optional<Long> initPacketMagicHeader = Optional.empty();
        // Defaults to not present.
        private Optional<Long> responsePacketMagicHeader = Optional.empty();
        // Defaults to not present.
        private Optional<Long> underloadPacketMagicHeader = Optional.empty();
        // Defaults to not present.
        private Optional<Long> transportPacketMagicHeader = Optional.empty();
        // Defaults to not present.
        private Optional<String> i1 = Optional.empty();
        // Defaults to not present.
        private Optional<String> i2 = Optional.empty();
        // Defaults to not present.
        private Optional<String> i3 = Optional.empty();
        // Defaults to not present.
        private Optional<String> i4 = Optional.empty();
        // Defaults to not present.
        private Optional<String> i5 = Optional.empty();
        // Defaults to not present.
        private Optional<String> j1 = Optional.empty();
        // Defaults to not present.
        private Optional<String> j2 = Optional.empty();
        // Defaults to not present.
        private Optional<String> j3 = Optional.empty();
        // Defaults to not present.
        private Optional<Integer> itime = Optional.empty();
        // Defaults to an empty set.
        private final Set<String> blockedDomains = new LinkedHashSet<>();
        // Defaults to not present.
        private Optional<Boolean> domainBlockingEnabled = Optional.empty();
        private final List<String> preUp = new ArrayList<>();
        // Defaults to empty list
        private final List<String> postUp = new ArrayList<>();
        // Defaults to empty list
        private final List<String> preDown = new ArrayList<>();
        // Defaults to empty list
        private final List<String> postDown = new ArrayList<>();


        public Builder addAddress(final InetNetwork address) {
            addresses.add(address);
            return this;
        }

        public Builder addAddresses(final Collection<InetNetwork> addresses) {
            this.addresses.addAll(addresses);
            return this;
        }

        public Builder addDnsServer(final InetAddress dnsServer) {
            dnsServers.add(dnsServer);
            return this;
        }

        public Builder addDnsServers(final Collection<? extends InetAddress> dnsServers) {
            this.dnsServers.addAll(dnsServers);
            return this;
        }

        public Builder addDnsSearchDomain(final String dnsSearchDomain) {
            dnsSearchDomains.add(dnsSearchDomain);
            return this;
        }

        public Builder addDnsSearchDomains(final Collection<String> dnsSearchDomains) {
            this.dnsSearchDomains.addAll(dnsSearchDomains);
            return this;
        }

        public Interface build() throws BadConfigException {
            if (keyPair == null)
                throw new BadConfigException(Section.INTERFACE, Location.PRIVATE_KEY,
                        Reason.MISSING_ATTRIBUTE, null);
            if (!includedApplications.isEmpty() && !excludedApplications.isEmpty())
                throw new BadConfigException(Section.INTERFACE, Location.INCLUDED_APPLICATIONS,
                        Reason.INVALID_KEY, null);

            // Validate AmneziaWG parameters (inter-dependent checks)
            int jc = junkPacketCount.orElse(0);
            int jmin = junkPacketMinSize.orElse(0);
            int jmax = junkPacketMaxSize.orElse(0);
            int s1 = initPacketJunkSize.orElse(0);
            int s2 = responsePacketJunkSize.orElse(0);
            long h1 = initPacketMagicHeader.orElse(0L);
            long h2 = responsePacketMagicHeader.orElse(0L);
            long h3 = underloadPacketMagicHeader.orElse(0L);
            long h4 = transportPacketMagicHeader.orElse(0L);
            int it = itime.orElse(0);

            if (jc > 0 && jmin > jmax) {
                throw new BadConfigException(Section.INTERFACE, Location.JUNK_PACKET_MIN_SIZE,
                        Reason.INVALID_VALUE, "Jmin > Jmax");
            }
            if (148 + s1 == 92 + s2) {
                throw new BadConfigException(Section.INTERFACE, Location.INIT_PACKET_JUNK_SIZE,
                        Reason.INVALID_VALUE, "S1 + 148 == S2 + 92");
            }
            boolean anyHeaderSet = initPacketMagicHeader.isPresent() || responsePacketMagicHeader.isPresent() ||
                    underloadPacketMagicHeader.isPresent() || transportPacketMagicHeader.isPresent();
            if (anyHeaderSet) {
                if (!(initPacketMagicHeader.isPresent() && responsePacketMagicHeader.isPresent() &&
                        underloadPacketMagicHeader.isPresent() && transportPacketMagicHeader.isPresent())) {
                    throw new BadConfigException(Section.INTERFACE, Location.INIT_PACKET_MAGIC_HEADER,
                            Reason.MISSING_ATTRIBUTE, "All H1-H4 must be set if any are present");
                }
                Set<Long> seen = new HashSet<>();
                if (!seen.add(h1) || !seen.add(h2) || !seen.add(h3) || !seen.add(h4)) {
                    throw new BadConfigException(Section.INTERFACE, Location.INIT_PACKET_MAGIC_HEADER,
                            Reason.INVALID_VALUE, "H1-H4 must be unique");
                }
            }

            return new Interface(this);
        }

        public Builder excludeApplication(final String application) {
            excludedApplications.add(application);
            return this;
        }

        public Builder excludeApplications(final Collection<String> applications) {
            excludedApplications.addAll(applications);
            return this;
        }

        public Builder includeApplication(final String application) {
            includedApplications.add(application);
            return this;
        }

        public Builder includeApplications(final Collection<String> applications) {
            includedApplications.addAll(applications);
            return this;
        }

        public Builder parseAddresses(final CharSequence addresses) throws BadConfigException {
            try {
                for (final String address : Attribute.split(addresses))
                    addAddress(InetNetwork.parse(address));
                return this;
            } catch (final ParseException e) {
                throw new BadConfigException(Section.INTERFACE, Location.ADDRESS, e);
            }
        }

        public Builder parseDnsServers(final CharSequence dnsServers) throws BadConfigException {
            try {
                for (final String dnsServer : Attribute.split(dnsServers)) {
                    try {
                        addDnsServer(InetAddresses.parse(dnsServer));
                    } catch (final ParseException e) {
                        if (e.getParsingClass() != InetAddress.class || !InetAddresses.isHostname(dnsServer))
                            throw e;
                        addDnsSearchDomain(dnsServer);
                    }
                }
                return this;
            } catch (final ParseException e) {
                throw new BadConfigException(Section.INTERFACE, Location.DNS, e);
            }
        }

        public Builder parseBlockedDomains(final CharSequence blockedDomains) {
            for (final String domain : Attribute.split(blockedDomains)) {
                addBlockedDomains(domain);
            }
            return this;
        }

        private Builder addBlockedDomains(final String dnsServer) {
            blockedDomains.add(dnsServer);
            return this;
        }

        public Builder parseDomainBlockingEnabled(final String domainBlockingEnabled) throws BadConfigException {
            try {
                this.domainBlockingEnabled = Optional.of(Boolean.parseBoolean(domainBlockingEnabled));
                return this;
            } catch (Exception e) {
                throw new BadConfigException(Section.INTERFACE, Location.TOP_LEVEL, Reason.INVALID_VALUE, domainBlockingEnabled);
            }
        }

        public Builder parseExcludedApplications(final CharSequence apps) {
            return excludeApplications(List.of(Attribute.split(apps)));
        }

        public Builder parseIncludedApplications(final CharSequence apps) {
            return includeApplications(List.of(Attribute.split(apps)));
        }

        public Builder parseListenPort(final String listenPort) throws BadConfigException {
            try {
                int lp = Integer.parseInt(listenPort);
                if (lp < MIN_UDP_PORT || lp > MAX_UDP_PORT) {
                    throw new BadConfigException(Section.INTERFACE, Location.LISTEN_PORT, Reason.INVALID_VALUE, listenPort);
                }
                return setListenPort(lp);
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.LISTEN_PORT, listenPort, e);
            }
        }

        public Builder parseMtu(final String mtu) throws BadConfigException {
            try {
                int m = Integer.parseInt(mtu);
                if (m < 0) {
                    throw new BadConfigException(Section.INTERFACE, Location.MTU, Reason.INVALID_VALUE, mtu);
                }
                return setMtu(m);
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.MTU, mtu, e);
            }
        }

        public Builder parseJunkPacketCount(final String junkPacketCount) throws BadConfigException {
            try {
                int jc = Integer.parseInt(junkPacketCount);
                if (jc < 0 || jc > 10) {
                    throw new BadConfigException(Section.INTERFACE, Location.JUNK_PACKET_COUNT, Reason.INVALID_VALUE, junkPacketCount);
                }
                return setJunkPacketCount(jc);
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.JUNK_PACKET_COUNT, junkPacketCount, e);
            }
        }

        public Builder parseJunkPacketMinSize(final String junkPacketMinSize) throws BadConfigException {
            try {
                int jmin = Integer.parseInt(junkPacketMinSize);
                if (jmin < 0 || jmin > 1024) {
                    throw new BadConfigException(Section.INTERFACE, Location.JUNK_PACKET_MIN_SIZE, Reason.INVALID_VALUE, junkPacketMinSize);
                }
                return setJunkPacketMinSize(jmin);
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.JUNK_PACKET_MIN_SIZE, junkPacketMinSize, e);
            }
        }

        public Builder parseJunkPacketMaxSize(final String junkPacketMaxSize) throws BadConfigException {
            try {
                int jmax = Integer.parseInt(junkPacketMaxSize);
                if (jmax < 0 || jmax > 1024) {
                    throw new BadConfigException(Section.INTERFACE, Location.JUNK_PACKET_MAX_SIZE, Reason.INVALID_VALUE, junkPacketMaxSize);
                }
                return setJunkPacketMaxSize(jmax);
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.JUNK_PACKET_MAX_SIZE, junkPacketMaxSize, e);
            }
        }

        public Builder parseInitPacketJunkSize(final String initPacketJunkSize) throws BadConfigException {
            try {
                int s1 = Integer.parseInt(initPacketJunkSize);
                if (s1 < 0 || s1 > 64) {
                    throw new BadConfigException(Section.INTERFACE, Location.INIT_PACKET_JUNK_SIZE, Reason.INVALID_VALUE, initPacketJunkSize);
                }
                return setInitPacketJunkSize(s1);
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.INIT_PACKET_JUNK_SIZE, initPacketJunkSize, e);
            }
        }

        public Builder parseResponsePacketJunkSize(final String responsePacketJunkSize) throws BadConfigException {
            try {
                int s2 = Integer.parseInt(responsePacketJunkSize);
                if (s2 < 0 || s2 > 64) {
                    throw new BadConfigException(Section.INTERFACE, Location.RESPONSE_PACKET_JUNK_SIZE, Reason.INVALID_VALUE, responsePacketJunkSize);
                }
                return setResponsePacketJunkSize(s2);
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.RESPONSE_PACKET_JUNK_SIZE, responsePacketJunkSize, e);
            }
        }

        public Builder parseInitPacketMagicHeader(final String initPacketMagicHeader) throws BadConfigException {
            try {
                long h1 = Long.parseLong(initPacketMagicHeader);
                if (h1 < 0) {
                    throw new BadConfigException(Section.INTERFACE, Location.INIT_PACKET_MAGIC_HEADER, Reason.INVALID_VALUE, initPacketMagicHeader);
                }
                return setInitPacketMagicHeader(h1);
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.INIT_PACKET_MAGIC_HEADER, initPacketMagicHeader, e);
            }
        }

        public Builder parseResponsePacketMagicHeader(final String responsePacketMagicHeader) throws BadConfigException {
            try {
                long h2 = Long.parseLong(responsePacketMagicHeader);
                if (h2 < 0) {
                    throw new BadConfigException(Section.INTERFACE, Location.RESPONSE_PACKET_MAGIC_HEADER, Reason.INVALID_VALUE, responsePacketMagicHeader);
                }
                return setResponsePacketMagicHeader(h2);
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.RESPONSE_PACKET_MAGIC_HEADER, responsePacketMagicHeader, e);
            }
        }

        public Builder parseUnderloadPacketMagicHeader(final String underloadPacketMagicHeader) throws BadConfigException {
            try {
                long h3 = Long.parseLong(underloadPacketMagicHeader);
                if (h3 < 0) {
                    throw new BadConfigException(Section.INTERFACE, Location.UNDERLOAD_PACKET_MAGIC_HEADER, Reason.INVALID_VALUE, underloadPacketMagicHeader);
                }
                return setUnderloadPacketMagicHeader(h3);
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.UNDERLOAD_PACKET_MAGIC_HEADER, underloadPacketMagicHeader, e);
            }
        }

        public Builder parseTransportPacketMagicHeader(final String transportPacketMagicHeader) throws BadConfigException {
            try {
                long h4 = Long.parseLong(transportPacketMagicHeader);
                if (h4 < 0) {
                    throw new BadConfigException(Section.INTERFACE, Location.TRANSPORT_PACKET_MAGIC_HEADER, Reason.INVALID_VALUE, transportPacketMagicHeader);
                }
                return setTransportPacketMagicHeader(h4);
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.TRANSPORT_PACKET_MAGIC_HEADER, transportPacketMagicHeader, e);
            }
        }

        public Builder parseI1(final String i1) {
            return setI1(i1);
        }

        public Builder parseI2(final String i2) {
            return setI2(i2);
        }

        public Builder parseI3(final String i3) {
            return setI3(i3);
        }

        public Builder parseI4(final String i4) {
            return setI4(i4);
        }

        public Builder parseI5(final String i5) {
            return setI5(i5);
        }

        public Builder parseJ1(final String j1) {
            return setJ1(j1);
        }

        public Builder parseJ2(final String j2) {
            return setJ2(j2);
        }

        public Builder parseJ3(final String j3) {
            return setJ3(j3);
        }

        public Builder parseItime(final String itime) throws BadConfigException {
            try {
                int it = Integer.parseInt(itime);
                if (it < 0) {
                    throw new BadConfigException(Section.INTERFACE, Location.ITIME, Reason.INVALID_VALUE, itime);
                }
                return setItime(it);
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.ITIME, itime, e);
            }
        }

        public Builder parsePrivateKey(final String privateKey) throws BadConfigException {
            try {
                return setKeyPair(new KeyPair(Key.fromBase64(privateKey)));
            } catch (final KeyFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.PRIVATE_KEY, e);
            }
        }

        public Builder parsePreUp(final String script) {
            preUp.add(script);
            return this;
        }

        public Builder parsePostUp(final String script) {
            postUp.add(script);
            return this;
        }

        public Builder parsePreDown(final String script) {
            preDown.add(script);
            return this;
        }

        public Builder parsePostDown(final String script) {
            postDown.add(script);
            return this;
        }


        public Builder setKeyPair(final KeyPair keyPair) {
            this.keyPair = keyPair;
            return this;
        }

        public Builder setListenPort(final int listenPort) throws BadConfigException {
            if (listenPort < MIN_UDP_PORT || listenPort > MAX_UDP_PORT)
                throw new BadConfigException(Section.INTERFACE, Location.LISTEN_PORT,
                        Reason.INVALID_VALUE, String.valueOf(listenPort));
            this.listenPort = listenPort == 0 ? Optional.empty() : Optional.of(listenPort);
            return this;
        }

        public Builder setMtu(final int mtu) throws BadConfigException {
            if (mtu < 0)
                throw new BadConfigException(Section.INTERFACE, Location.MTU,
                        Reason.INVALID_VALUE, String.valueOf(mtu));
            this.mtu = mtu == 0 ? Optional.empty() : Optional.of(mtu);
            return this;
        }

        public Builder setJunkPacketCount(final int junkPacketCount) throws BadConfigException {
            if (junkPacketCount < 0)
                throw new BadConfigException(Section.INTERFACE, Location.JUNK_PACKET_COUNT,
                        Reason.INVALID_VALUE, String.valueOf(junkPacketCount));
            this.junkPacketCount = junkPacketCount == 0 ? Optional.empty() : Optional.of(junkPacketCount);
            return this;
        }

        public Builder setJunkPacketMinSize(final int junkPacketMinSize) throws BadConfigException {
            if (junkPacketMinSize < 0)
                throw new BadConfigException(Section.INTERFACE, Location.JUNK_PACKET_MIN_SIZE,
                        Reason.INVALID_VALUE, String.valueOf(junkPacketMinSize));
            this.junkPacketMinSize = junkPacketMinSize == 0 ? Optional.empty() : Optional.of(junkPacketMinSize);
            return this;
        }

        public Builder setJunkPacketMaxSize(final int junkPacketMaxSize) throws BadConfigException {
            if (junkPacketMaxSize < 0)
                throw new BadConfigException(Section.INTERFACE, Location.JUNK_PACKET_MAX_SIZE,
                        Reason.INVALID_VALUE, String.valueOf(junkPacketMaxSize));
            this.junkPacketMaxSize = junkPacketMaxSize == 0 ? Optional.empty() : Optional.of(junkPacketMaxSize);
            return this;
        }

        public Builder setInitPacketJunkSize(final int initPacketJunkSize) throws BadConfigException {
            if (initPacketJunkSize < 0)
                throw new BadConfigException(Section.INTERFACE, Location.INIT_PACKET_JUNK_SIZE,
                        Reason.INVALID_VALUE, String.valueOf(initPacketJunkSize));
            this.initPacketJunkSize = initPacketJunkSize == 0 ? Optional.empty() : Optional.of(initPacketJunkSize);
            return this;
        }

        public Builder setResponsePacketJunkSize(final int responsePacketJunkSize) throws BadConfigException {
            if (responsePacketJunkSize < 0)
                throw new BadConfigException(Section.INTERFACE, Location.RESPONSE_PACKET_JUNK_SIZE,
                        Reason.INVALID_VALUE, String.valueOf(responsePacketJunkSize));
            this.responsePacketJunkSize = responsePacketJunkSize == 0 ? Optional.empty() : Optional.of(responsePacketJunkSize);
            return this;
        }

        public Builder setInitPacketMagicHeader(final long initPacketMagicHeader) throws BadConfigException {
            if (initPacketMagicHeader < 0)
                throw new BadConfigException(Section.INTERFACE, Location.INIT_PACKET_MAGIC_HEADER,
                        Reason.INVALID_VALUE, String.valueOf(initPacketMagicHeader));
            this.initPacketMagicHeader = initPacketMagicHeader == 0 ? Optional.empty() : Optional.of(initPacketMagicHeader);
            return this;
        }

        public Builder setResponsePacketMagicHeader(final long responsePacketMagicHeader) throws BadConfigException {
            if (responsePacketMagicHeader < 0)
                throw new BadConfigException(Section.INTERFACE, Location.RESPONSE_PACKET_MAGIC_HEADER,
                        Reason.INVALID_VALUE, String.valueOf(responsePacketMagicHeader));
            this.responsePacketMagicHeader = responsePacketMagicHeader == 0 ? Optional.empty() : Optional.of(responsePacketMagicHeader);
            return this;
        }

        public Builder setUnderloadPacketMagicHeader(final long underloadPacketMagicHeader) throws BadConfigException {
            if (underloadPacketMagicHeader < 0)
                throw new BadConfigException(Section.INTERFACE, Location.UNDERLOAD_PACKET_MAGIC_HEADER,
                        Reason.INVALID_VALUE, String.valueOf(underloadPacketMagicHeader));
            this.underloadPacketMagicHeader = underloadPacketMagicHeader == 0 ? Optional.empty() : Optional.of(underloadPacketMagicHeader);
            return this;
        }

        public Builder setTransportPacketMagicHeader(final long transportPacketMagicHeader) throws BadConfigException {
            if (transportPacketMagicHeader < 0)
                throw new BadConfigException(Section.INTERFACE, Location.TRANSPORT_PACKET_MAGIC_HEADER,
                        Reason.INVALID_VALUE, String.valueOf(transportPacketMagicHeader));
            this.transportPacketMagicHeader = transportPacketMagicHeader == 0 ? Optional.empty() : Optional.of(transportPacketMagicHeader);
            return this;
        }

        public Builder setI1(final String i1) {
            this.i1 = i1.isEmpty() ? Optional.empty() : Optional.of(i1);
            return this;
        }

        public Builder setI2(final String i2) {
            this.i2 = i2.isEmpty() ? Optional.empty() : Optional.of(i2);
            return this;
        }

        public Builder setI3(final String i3) {
            this.i3 = i3.isEmpty() ? Optional.empty() : Optional.of(i3);
            return this;
        }

        public Builder setI4(final String i4) {
            this.i4 = i4.isEmpty() ? Optional.empty() : Optional.of(i4);
            return this;
        }

        public Builder setI5(final String i5) {
            this.i5 = i5.isEmpty() ? Optional.empty() : Optional.of(i5);
            return this;
        }

        public Builder setJ1(final String j1) {
            this.j1 = j1.isEmpty() ? Optional.empty() : Optional.of(j1);
            return this;
        }

        public Builder setJ2(final String j2) {
            this.j2 = j2.isEmpty() ? Optional.empty() : Optional.of(j2);
            return this;
        }

        public Builder setJ3(final String j3) {
            this.j3 = j3.isEmpty() ? Optional.empty() : Optional.of(j3);
            return this;
        }

        public Builder setItime(final int itime) throws BadConfigException {
            if (itime < 0)
                throw new BadConfigException(Section.INTERFACE, Location.ITIME,
                        Reason.INVALID_VALUE, String.valueOf(itime));
            this.itime = itime == 0 ? Optional.empty() : Optional.of(itime);
            return this;
        }
    }
}