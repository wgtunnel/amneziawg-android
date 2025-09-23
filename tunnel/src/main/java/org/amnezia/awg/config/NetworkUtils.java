package org.amnezia.awg.config;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;

import java.net.Inet6Address;
import java.net.InetAddress;

public class NetworkUtils {
    public static boolean hasGlobalIpv6(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) return false;
        LinkProperties linkProperties = cm.getLinkProperties(activeNetwork);
        if (linkProperties == null) return false;
        for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
            InetAddress addr = linkAddress.getAddress();
            if (addr instanceof Inet6Address && !addr.isLinkLocalAddress() && !addr.isLoopbackAddress() && !addr.isSiteLocalAddress()) {
                return true;  // Found a global IPv6 address (not fe80::/10, ::1, or fc00::/7)
            }
        }
        return false;
    }
}