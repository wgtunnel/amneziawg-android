package org.amnezia.awg.config.proxy;

import androidx.annotation.Nullable;

public class HttpProxy extends Proxy {
    public HttpProxy(String bindAddress, @Nullable String username, @Nullable String password) {
        super(bindAddress, username, password);
    }

    @Override
    public String toQuickString() {
        return "[http]\n" +
                "BindAddress = " + bindAddress + "\n" +
                buildAuthString();
    }
}
