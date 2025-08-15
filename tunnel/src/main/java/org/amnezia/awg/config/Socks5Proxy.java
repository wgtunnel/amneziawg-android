package org.amnezia.awg.config;

import androidx.annotation.Nullable;

public class Socks5Proxy extends Proxy {
    public Socks5Proxy(String bindAddress, @Nullable String username, @Nullable String password) {
        super(bindAddress, username, password);
    }

    @Override
    String toQuickString() {
        return "[Socks5]\n" +
                "BindAddress = " + bindAddress + "\n" +
                buildAuthString();
    }
}