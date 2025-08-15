package org.amnezia.awg.config;

import androidx.annotation.Nullable;

public class HttpProxy extends Proxy {
    public HttpProxy(String bindAddress, @Nullable String username, @Nullable String password) {
        super(bindAddress, username, password);
    }

    @Override
    String toQuickString() {
        return "[http]\n" +
                "BindAddress = " + bindAddress + "\n" +
                buildAuthString();
    }
}
