package com.raglab.rcache;

import com.raglab.rcache.config.RCacheConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RCacheApplicationTest {

    @Test
    void testDefaultConfig() {
        RCacheConfig config = RCacheConfig.defaultConfig();
        assertNotNull(config);
        assertEquals(6380, config.port());
        assertEquals("0.0.0.0", config.bindAddress());
        assertEquals(1024, config.maxConnections());
    }
}
