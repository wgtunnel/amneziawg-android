package org.amnezia.awg.config.proxy;

import androidx.annotation.Nullable;

public class Socks5Proxy extends Proxy {
    public Socks5Proxy(String bindAddress, @Nullable String username, @Nullable String password) {
        super(bindAddress, username, password);
    }

    @Override
    public String toQuickString() {
        return "[Socks5]\n" +
                "BindAddress = " + bindAddress + "\n" +
                buildAuthString();
    }
}