package org.amnezia.awg;

public class ProxyGoBackend {
    public static native void awgStartWireproxy(String config);

    public static native int awgStopWireproxy();
}
