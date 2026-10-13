package com.raglab.rcache.client;

import com.raglab.rcache.command.CommandDispatcher;
import com.raglab.rcache.server.RCacheServer;
import com.raglab.rcache.storage.RCacheStorageEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RCacheClientIntegrationTest {

    private RCacheStorageEngine storageEngine;
    private CommandDispatcher dispatcher;
    private RCacheServer server;
    private RCacheClient client;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        storageEngine = new RCacheStorageEngine(true);
        dispatcher = new CommandDispatcher(storageEngine);
        port = 17380 + (int) (Math.random() * 1000);
        server = new RCacheServer("127.0.0.1", port, dispatcher);
        server.start();
        Thread.sleep(100);

        client = new RCacheClient("127.0.0.1", port);
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.close();
        if (storageEngine != null) storageEngine.close();
    }

    @Test
    @DisplayName("RCacheClient: ping, set, get, exists, del, ttl, incr")
    void testClientOperations() throws Exception {
        // Ping
        assertEquals("PONG", client.ping());

        // Set & Get
        assertTrue(client.set("foo", "bar"));
        Optional<String> val = client.getString("foo");
        assertTrue(val.isPresent());
        assertEquals("bar", val.get());

        // Exists
        assertTrue(client.exists("foo"));
        assertFalse(client.exists("nonexistent"));

        // TTL & Expire
        assertTrue(client.expire("foo", 10));
        assertTrue(client.ttl("foo") > 0);

        // Delete
        assertTrue(client.delete("foo"));
        assertFalse(client.exists("foo"));
        assertTrue(client.getString("foo").isEmpty());

        // Counters
        assertEquals(1L, client.incr("cnt"));
        assertEquals(5L, client.incrBy("cnt", 4));
        assertEquals(4L, client.decr("cnt"));

        // Batch MSET / MGET
        client.mset(Map.of(
            "k1", "v1".getBytes(StandardCharsets.UTF_8),
            "k2", "v2".getBytes(StandardCharsets.UTF_8)
        ));
        Map<String, byte[]> mgetRes = client.mget(List.of("k1", "missing", "k2"));
        assertEquals(2, mgetRes.size());
        assertEquals("v1", new String(mgetRes.get("k1"), StandardCharsets.UTF_8));
        assertEquals("v2", new String(mgetRes.get("k2"), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("RCacheClient Fail-Open: Server termination triggers circuit breaker and returns empty results without crashing")
    void testFailOpenResilience() {
        // Verify healthy initially
        assertTrue(client.set("k1", "v1"));

        // Abruptly terminate server
        server.close();

        // Safe operations return empty / false without unhandled exceptions
        Optional<String> getRes = client.getString("k1");
        assertTrue(getRes.isEmpty());

        assertFalse(client.set("k2", "v2"));
        assertNull(client.incr("counter"));

        // Verify circuit breaker opened
        assertTrue(client.isCircuitBreakerOpen());
    }
}

