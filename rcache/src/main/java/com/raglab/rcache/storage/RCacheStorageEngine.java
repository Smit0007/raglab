package com.raglab.rcache.storage;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance concurrent in-memory storage engine for RCache.
 * Provides:
 * - Thread-safe ConcurrentHashMap storage
 * - Passive on-access TTL eviction
 * - Active periodic background TTL eviction sweep
 * - Atomic counter operations (INCR, DECR)
 * - Batch operations (MGET, MSET)
 */
public class RCacheStorageEngine implements AutoCloseable {

    private final ConcurrentHashMap<String, CacheEntry> store = new ConcurrentHashMap<>();
    private final ScheduledExecutorService backgroundCleaner;
    private final AtomicLong keyCount = new AtomicLong(0);

    public RCacheStorageEngine() {
        this(true);
    }

    public RCacheStorageEngine(boolean enableBackgroundCleaner) {
        if (enableBackgroundCleaner) {
            this.backgroundCleaner = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "rcache-ttl-cleaner");
                t.setDaemon(true);
                return t;
            });
            // Run active expiration sweep every 100ms
            this.backgroundCleaner.scheduleAtFixedRate(this::activeExpireSweep, 100, 100, TimeUnit.MILLISECONDS);
        } else {
            this.backgroundCleaner = null;
        }
    }

    public void set(String key, byte[] value, long ttlSeconds) {
        CacheEntry entry = new CacheEntry(value, ttlSeconds);
        store.put(key, entry);
    }

    public byte[] get(String key) {
        long now = System.currentTimeMillis();
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(now)) {
            store.remove(key, entry);
            return null;
        }
        return entry.getData();
    }

    public boolean delete(String key) {
        return store.remove(key) != null;
    }

    public int deleteMulti(List<String> keys) {
        int count = 0;
        for (String k : keys) {
            if (store.remove(k) != null) {
                count++;
            }
        }
        return count;
    }

    public int exists(List<String> keys) {
        long now = System.currentTimeMillis();
        int count = 0;
        for (String k : keys) {
            CacheEntry entry = store.get(k);
            if (entry != null) {
                if (entry.isExpired(now)) {
                    store.remove(k, entry);
                } else {
                    count++;
                }
            }
        }
        return count;
    }

    public boolean expire(String key, long seconds) {
        long now = System.currentTimeMillis();
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired(now)) {
            store.remove(key, entry);
            return false;
        }
        entry.setTtlSeconds(seconds);
        return true;
    }

    public long ttl(String key) {
        long now = System.currentTimeMillis();
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return -2L; // Key does not exist
        }
        if (entry.isExpired(now)) {
            store.remove(key, entry);
            return -2L; // Key expired
        }
        return entry.getTtlRemainingSeconds(now);
    }

    public long incrBy(String key, long delta) {
        long now = System.currentTimeMillis();
        // Compute atomically using store.compute
        CacheEntry updated = store.compute(key, (k, existing) -> {
            if (existing == null || existing.isExpired(now)) {
                // Initialize with delta, no expiry
                String valStr = Long.toString(delta);
                return new CacheEntry(valStr.getBytes(StandardCharsets.UTF_8), -1L);
            }
            byte[] existingBytes = existing.getData();
            String existingStr = new String(existingBytes, StandardCharsets.UTF_8);
            long currentVal;
            try {
                currentVal = Long.parseLong(existingStr);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("ERR value is not an integer or out of range");
            }
            long nextVal = Math.addExact(currentVal, delta);
            long ttlRemainingSec = existing.getTtlRemainingSeconds(now);
            return new CacheEntry(Long.toString(nextVal).getBytes(StandardCharsets.UTF_8), ttlRemainingSec > 0 ? ttlRemainingSec : -1L);
        });

        String resultStr = new String(updated.getData(), StandardCharsets.UTF_8);
        return Long.parseLong(resultStr);
    }

    public List<byte[]> mget(List<String> keys) {
        List<byte[]> results = new ArrayList<>(keys.size());
        long now = System.currentTimeMillis();
        for (String k : keys) {
            CacheEntry entry = store.get(k);
            if (entry == null || entry.isExpired(now)) {
                if (entry != null) store.remove(k, entry);
                results.add(null);
            } else {
                results.add(entry.getData());
            }
        }
        return results;
    }

    public void mset(Map<String, byte[]> keyValues) {
        for (Map.Entry<String, byte[]> e : keyValues.entrySet()) {
            set(e.getKey(), e.getValue(), -1L);
        }
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }

    /**
     * Active probabilistic sweep: checks a batch of keys for expiration.
     */
    public void activeExpireSweep() {
        if (store.isEmpty()) return;
        long now = System.currentTimeMillis();
        int sampleSize = Math.min(50, store.size());
        Iterator<Map.Entry<String, CacheEntry>> it = store.entrySet().iterator();
        int sampled = 0;
        while (it.hasNext() && sampled < sampleSize) {
            Map.Entry<String, CacheEntry> entry = it.next();
            if (entry.getValue().isExpired(now)) {
                it.remove();
            }
            sampled++;
        }
    }

    @Override
    public void close() {
        if (backgroundCleaner != null) {
            backgroundCleaner.shutdownNow();
        }
    }
}

