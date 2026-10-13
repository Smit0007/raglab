package com.raglab.rcache.client;

public class RCacheException extends RuntimeException {
    public RCacheException(String message) {
        super(message);
    }

    public RCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}

