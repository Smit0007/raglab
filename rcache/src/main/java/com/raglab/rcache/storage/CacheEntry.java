package com.raglab.rcache.storage;

import java.util.Arrays;

/**
 * Value entry storing binary payload, creation time, access time, and optional expiry epoch ms.
 */
public class CacheEntry {
    private final byte[] data;
    private final long createdAtMs;
    private volatile long lastAccessedAtMs;
    private volatile long expireAtMs; // -1 if no expiry

    public CacheEntry(byte[] data, long ttlSeconds) {
        this.data = data;
        long now = System.currentTimeMillis();
        this.createdAtMs = now;
        this.lastAccessedAtMs = now;
        this.expireAtMs = ttlSeconds > 0 ? now + (ttlSeconds * 1000L) : -1L;
    }

    public byte[] getData() {
        this.lastAccessedAtMs = System.currentTimeMillis();
        return data;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public long getLastAccessedAtMs() {
        return lastAccessedAtMs;
    }

    public long getExpireAtMs() {
        return expireAtMs;
    }

    public void setTtlSeconds(long ttlSeconds) {
        if (ttlSeconds > 0) {
            this.expireAtMs = System.currentTimeMillis() + (ttlSeconds * 1000L);
        } else {
            this.expireAtMs = 0L; // Expire immediately
        }
    }

    public void persist() {
        this.expireAtMs = -1L;
    }

    public boolean isExpired(long now) {
        return expireAtMs != -1L && now >= expireAtMs;
    }

    public long getTtlRemainingSeconds(long now) {
        if (expireAtMs == -1L) {
            return -1L; // No TTL associated
        }
        long diff = expireAtMs - now;
        if (diff <= 0) {
            return -2L; // Expired
        }
        return (diff + 999L) / 1000L; // Ceil to nearest second
    }

    public long sizeBytes() {
        return data == null ? 0 : data.length;
    }
}

