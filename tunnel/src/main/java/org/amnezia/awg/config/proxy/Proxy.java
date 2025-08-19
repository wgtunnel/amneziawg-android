package org.amnezia.awg.config.proxy;

import androidx.annotation.Nullable;
import java.util.regex.Pattern;

public abstract class Proxy {
    private static final Pattern BIND_IP_PATTERN = Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)(:\\d{1,5})?$");

    protected final String bindAddress;
    @Nullable
    protected final String username;
    @Nullable
    protected final String password;

    protected Proxy(String bindAddress, @Nullable String username, @Nullable String password) {
        this.bindAddress = validProxyBindAddress(bindAddress);
        this.username = username;
        this.password = password;
    }

    protected Proxy(int port, @Nullable String username, @Nullable String password) {
        this(String.valueOf(port), username, password);
    }

    public static String validProxyBindAddress(String address) throws IllegalArgumentException {
        if (!BIND_IP_PATTERN.matcher(address).matches()) {
            throw new IllegalArgumentException("Invalid IP Address");
        }

        String[] parts = address.split(":");
        if (parts.length == 1) {
            throw new IllegalArgumentException("Missing port number");
        }

        try {
            int port = Integer.parseInt(parts[1]);
            if(!(port >= 1 && port <= 65535)) {
                throw new IllegalArgumentException("Invalid port number");

            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number");
        }
        return address;
    }

    protected String buildAuthString() {
        StringBuilder sb = new StringBuilder();
        if (username != null || password != null) {
            if (username != null) {
                sb.append("Username = ").append(username).append("\n");
            }
            if (password != null) {
                sb.append("Password = ").append(password).append("\n");
            }
        }
        return sb.toString();
    }

    public abstract String toQuickString();
}