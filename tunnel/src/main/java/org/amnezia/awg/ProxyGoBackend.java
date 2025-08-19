package org.amnezia.awg;

import androidx.annotation.Nullable;
import org.amnezia.awg.backend.SocketProtector;

public class ProxyGoBackend {
    public static native int awgStartProxy(String ifName, String config, String pkgName, int bypass);

    public static native void awgStopProxy();

    @Nullable
    public static native String awgGetProxyConfig(int handle);

    public static native void awgSetSocketProtector(SocketProtector sp);
}
