package com.raglab.application.service.cache;

import com.raglab.rcache.client.RCacheClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service layer abstraction over RCacheClient with fail-open semantics.
 */
@Service
public class RCacheService {

    private static final Logger log = LoggerFactory.getLogger(RCacheService.class);
    private final RCacheClient rCacheClient;

    public RCacheService(RCacheClient rCacheClient) {
        this.rCacheClient = rCacheClient;
    }

    public boolean put(String key, String value, long ttlSeconds) {
        try {
            return rCacheClient.set(key, value.getBytes(java.nio.charset.StandardCharsets.UTF_8), ttlSeconds);
        } catch (Exception e) {
            log.warn("RCache put error for key '{}': {}", key, e.getMessage());
            return false;
        }
    }

    public Optional<String> get(String key) {
        try {
            return rCacheClient.getString(key);
        } catch (Exception e) {
            log.warn("RCache get error for key '{}': {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public boolean delete(String key) {
        try {
            return rCacheClient.delete(key);
        } catch (Exception e) {
            log.warn("RCache delete error for key '{}': {}", key, e.getMessage());
            return false;
        }
    }

    public Long increment(String key) {
        try {
            return rCacheClient.incr(key);
        } catch (Exception e) {
            log.warn("RCache increment error for key '{}': {}", key, e.getMessage());
            return null;
        }
    }

    public boolean isAvailable() {
        try {
            return "PONG".equalsIgnoreCase(rCacheClient.ping());
        } catch (Exception e) {
            return false;
        }
    }
}

