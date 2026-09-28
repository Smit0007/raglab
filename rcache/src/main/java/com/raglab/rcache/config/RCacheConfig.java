package com.raglab.rcache.config;

/**
 * Basic configuration model for the RCache server.
 */
public record RCacheConfig(
        int port,
        String bindAddress,
        int maxConnections
) {
    public static RCacheConfig defaultConfig() {
        return new RCacheConfig(6380, "0.0.0.0", 1024);
    }
}
