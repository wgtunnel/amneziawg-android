/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.config;

import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;
import org.amnezia.awg.util.NonNullForAll;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An external endpoint (host and port) used to connect to an AmneziaWG {@link Peer}.
 * <p>
 * Instances of this class are externally immutable.
 */
@NonNullForAll
public final class InetEndpoint {
    private static final Pattern BARE_IPV6 = Pattern.compile("^[^\\[\\]]*:[^\\[\\]]*");
    private static final Pattern FORBIDDEN_CHARACTERS = Pattern.compile("[/?#]");
    private static final String TAG = "PEER";

    private static final long DEFAULT_TTL_SECONDS = 300;
    private static final long NEGATIVE_TTL_SECONDS = 30;

    private static boolean useDoH = false; // User setting, default false
    public static final String DEFAULT_DOH_URL = "https://1.1.1.1/dns-query";
    private static String preferredDoHUrl = DEFAULT_DOH_URL;

    private static Resolver currentResolver = new SystemResolver();

    public static void setUseDoH(boolean use) {
        useDoH = use;
    }

    public static void setPreferredDoHUrl(String url) {
        preferredDoHUrl = url;
    }

    public static void setResolver(Resolver resolver) {
        currentResolver = resolver;
    }

    private final String host;
    private final boolean isResolved;
    private final Object lock = new Object();
    private final int port;
    private Instant lastFailedResolution = Instant.MIN;

    // Dual caches for IPv4 and IPv6
    @Nullable private InetEndpoint resolvedIpv4;
    @Nullable private InetEndpoint resolvedIpv6;
    private Instant lastResolutionIpv4 = Instant.MIN;
    private Instant lastResolutionIpv6 = Instant.MIN;

    private InetEndpoint(final String host, final boolean isResolved, final int port) {
        this.host = host;
        this.isResolved = isResolved;
        this.port = port;
    }

    public static InetEndpoint parse(final String endpoint) throws ParseException {
        if (FORBIDDEN_CHARACTERS.matcher(endpoint).find())
            throw new ParseException(InetEndpoint.class, endpoint, "Forbidden characters");
        final URI uri;
        try {
            uri = new URI("awg://" + endpoint);
        } catch (final URISyntaxException e) {
            throw new ParseException(InetEndpoint.class, endpoint, e);
        }
        if (uri.getPort() < 0 || uri.getPort() > 65535)
            throw new ParseException(InetEndpoint.class, endpoint, "Missing/invalid port number");
        try {
            InetAddresses.parse(uri.getHost());
            // Parsing the host as a numeric address worked, so we don't need to do DNS lookups.
            return new InetEndpoint(uri.getHost(), true, uri.getPort());
        } catch (final ParseException ignored) {
            // Failed to parse the host as a numeric address, so it must be a DNS hostname/FQDN.
            return new InetEndpoint(uri.getHost(), false, uri.getPort());
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof InetEndpoint other))
            return false;
        return host.equals(other.host) && port == other.port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Generate an {@code InetEndpoint} instance with the same port and the host resolved using DNS
     * to a numeric address. If the host is already numeric, the existing instance may be returned.
     * Because this function may perform network I/O, it must not be called from the main thread.
     * @param preferIpv4 whether ipv4 resolution should be preferred over the default ipv6
     * @return the resolved endpoint, or {@link Optional#empty()}
     */
    public Optional<InetEndpoint> getResolved(Boolean preferIpv4, Context context) {
        Log.d(TAG, "Resolving with ipv4 preferred: " + preferIpv4 + " and resolver: " + currentResolver.getClass().getSimpleName());
        if (isResolved) return Optional.of(this);
        Instant now = Instant.now();
        synchronized (lock) {
            Instant lastRes = preferIpv4 ? lastResolutionIpv4 : lastResolutionIpv6;
            InetEndpoint cached = preferIpv4 ? resolvedIpv4 : resolvedIpv6;
            if (Duration.between(lastRes, now).getSeconds() <= DEFAULT_TTL_SECONDS && cached != null) {
                return Optional.of(cached);
            }

            if (Duration.between(lastFailedResolution, now).getSeconds() <= NEGATIVE_TTL_SECONDS) {
                return Optional.empty();
            }

            try {
                InetAddress[] candidates = currentResolver.resolve(host);
                if (candidates.length == 0) {
                    Log.w(TAG, "No addresses resolved for host: " + host);
                    lastFailedResolution = now;
                    return Optional.empty();
                }

                if (currentResolver instanceof DoHResolver) {
                    boolean hasIpv6 = NetworkUtils.hasGlobalIpv6(context);
                    List<InetAddress> filtered = new ArrayList<>();
                    for (InetAddress addr : candidates) {
                        if (addr instanceof Inet4Address || (addr instanceof Inet6Address && hasIpv6)) {
                            filtered.add(addr);
                        }
                    }
                    candidates = filtered.toArray(new InetAddress[0]);
                    if (candidates.length == 0) {
                        lastFailedResolution = now;
                        return Optional.empty();
                    }
                }

                InetEndpoint ipv4Ep = null;
                InetEndpoint ipv6Ep = null;
                for (InetAddress addr : candidates) {
                    String addrStr = addr.getHostAddress();
                    if (addr instanceof Inet4Address && ipv4Ep == null) {
                        ipv4Ep = new InetEndpoint(addrStr, true, port);
                    } else if (addr instanceof Inet6Address && ipv6Ep == null) {
                        ipv6Ep = new InetEndpoint(addrStr, true, port);
                    }
                    if (ipv4Ep != null && ipv6Ep != null) break;
                }

                // Cache based on family
                if (ipv4Ep != null) {
                    resolvedIpv4 = ipv4Ep;
                    lastResolutionIpv4 = now;
                }
                if (ipv6Ep != null) {
                    resolvedIpv6 = ipv6Ep;
                    lastResolutionIpv6 = now;
                }

                // Select with fallback
                InetEndpoint preferredEp = preferIpv4 ? ipv4Ep : ipv6Ep;
                InetEndpoint fallbackEp = preferIpv4 ? ipv6Ep : ipv4Ep;
                InetEndpoint toReturn = (preferredEp != null) ? preferredEp : fallbackEp;
                return Optional.ofNullable(toReturn);
            } catch (final UnknownHostException e) {
                Log.w(TAG, "Failed to resolve host " + host + ": " + e.getMessage());
                lastFailedResolution = now;
                return Optional.empty();
            }
        }
    }

    /**
     * Clears the cached DNS resolution and resets resolution timestamps.
     * This forces the next call to getResolved to perform a fresh DNS query.
     */
    public void clearCache() {
        synchronized (lock) {
            resolvedIpv4 = null;
            resolvedIpv6 = null;
            lastResolutionIpv4 = Instant.MIN;
            lastResolutionIpv6 = Instant.MIN;
            lastFailedResolution = Instant.MIN;
            Log.d(TAG, "Cleared DNS cache for host: " + host);
        }
    }

    @Override
    public int hashCode() {
        return host.hashCode() ^ port;
    }

    @Override
    public String toString() {
        final boolean isBareIpv6 = isResolved && BARE_IPV6.matcher(host).matches();
        return (isBareIpv6 ? '[' + host + ']' : host) + ':' + port;
    }

    public interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    public static class SystemResolver implements Resolver {
        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            return InetAddress.getAllByName(host);
        }
    }

    public static String removeBrackets(String host) {
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    public record DoHResolver(Optional<String> dohUrl, Optional<SocketFactory> socketFactory) implements Resolver {
        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            String sanitizedHost = removeBrackets(host);
            IPAddress address = new IPAddressString(sanitizedHost).getAddress();
            if (address != null) {
                byte[] bytes = address.getBytes();
                InetAddress ipAddr = InetAddress.getByAddress(null, bytes);
                Log.i(TAG, "Skipping DoH for static IP endpoint ");
                return new InetAddress[] { ipAddr };
            }

            Log.i(TAG, "Using DoH URL: " + dohUrl.orElse(preferredDoHUrl));
            Log.i(TAG, "SocketFactory in use: " + (socketFactory.map(factory -> factory.getClass().getSimpleName()).orElse("none")));

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            socketFactory.ifPresent(builder::socketFactory);
            OkHttpClient client = builder
                    .build();
            String url = dohUrl.orElse(preferredDoHUrl);
            DnsOverHttps doh = new DnsOverHttps.Builder()
                    .client(client)
                    .url(HttpUrl.parse(url)).build();
            try {
                List<InetAddress> addresses = doh.lookup(host);
                return addresses.toArray(new InetAddress[0]);
            } catch (IOException e) {
                throw new UnknownHostException(e.getMessage());
            }
        }
    }
}