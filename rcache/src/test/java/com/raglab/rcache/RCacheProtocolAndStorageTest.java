package com.raglab.rcache;

import com.raglab.rcache.command.CommandDispatcher;
import com.raglab.rcache.protocol.RcpCodec;
import com.raglab.rcache.protocol.RcpFrame;
import com.raglab.rcache.server.RCacheServer;
import com.raglab.rcache.storage.RCacheStorageEngine;
import org.junit.jupiter.api.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RCacheProtocolAndStorageTest {

    private RCacheStorageEngine storageEngine;
    private CommandDispatcher dispatcher;
    private RCacheServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        storageEngine = new RCacheStorageEngine(true);
        dispatcher = new CommandDispatcher(storageEngine);
        port = 16380 + (int)(Math.random() * 1000);
        server = new RCacheServer("127.0.0.1", port, dispatcher);
        server.start();
        Thread.sleep(100);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.close();
        if (storageEngine != null) storageEngine.close();
    }

    @Test
    @DisplayName("Codec: Encode and decode simple, integer, error, bulk string and array frames")
    void testFrameCodec() {
        // Simple string
        RcpFrame simple = new RcpFrame.SimpleStringFrame("PONG");
        byte[] encSimple = RcpCodec.encode(simple);
        assertArrayEquals("+PONG\r\n".getBytes(StandardCharsets.UTF_8), encSimple);
        assertEquals(simple, RcpCodec.decode(ByteBuffer.wrap(encSimple)));

        // Error
        RcpFrame err = new RcpFrame.ErrorFrame("ERR invalid syntax");
        byte[] encErr = RcpCodec.encode(err);
        assertEquals(err, RcpCodec.decode(ByteBuffer.wrap(encErr)));

        // Integer
        RcpFrame num = new RcpFrame.IntegerFrame(42);
        byte[] encNum = RcpCodec.encode(num);
        assertArrayEquals(":42\r\n".getBytes(StandardCharsets.UTF_8), encNum);
        assertEquals(num, RcpCodec.decode(ByteBuffer.wrap(encNum)));

        // Bulk string
        RcpFrame bulk = new RcpFrame.BulkStringFrame("hello world".getBytes(StandardCharsets.UTF_8));
        byte[] encBulk = RcpCodec.encode(bulk);
        assertEquals(bulk, RcpCodec.decode(ByteBuffer.wrap(encBulk)));

        // NIL Bulk string
        byte[] encNil = RcpCodec.encode(RcpFrame.BulkStringFrame.NIL);
        assertArrayEquals("$-1\r\n".getBytes(StandardCharsets.UTF_8), encNil);
        assertEquals(RcpFrame.BulkStringFrame.NIL, RcpCodec.decode(ByteBuffer.wrap(encNil)));

        // Array
        RcpFrame arr = new RcpFrame.ArrayFrame(List.of(simple, num, bulk));
        byte[] encArr = RcpCodec.encode(arr);
        assertEquals(arr, RcpCodec.decode(ByteBuffer.wrap(encArr)));
    }

    @Test
    @DisplayName("Protocol TCP Integration: PING, SET, GET, DEL, EXISTS")
    void testCoreCommandsOverSocket() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // 1. PING
            sendFrame(out, new RcpFrame.ArrayFrame(List.of(new RcpFrame.BulkStringFrame("PING".getBytes(StandardCharsets.UTF_8)))));
            RcpFrame res1 = readFrame(in);
            assertInstanceOf(RcpFrame.SimpleStringFrame.class, res1);
            assertEquals("PONG", ((RcpFrame.SimpleStringFrame) res1).content());

            // 2. SET key value
            sendFrame(out, new RcpFrame.ArrayFrame(List.of(
                new RcpFrame.BulkStringFrame("SET".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("test:key1".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("test_value_123".getBytes(StandardCharsets.UTF_8))
            )));
            RcpFrame res2 = readFrame(in);
            assertInstanceOf(RcpFrame.SimpleStringFrame.class, res2);
            assertEquals("OK", ((RcpFrame.SimpleStringFrame) res2).content());

            // 3. GET key
            sendFrame(out, new RcpFrame.ArrayFrame(List.of(
                new RcpFrame.BulkStringFrame("GET".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("test:key1".getBytes(StandardCharsets.UTF_8))
            )));
            RcpFrame res3 = readFrame(in);
            assertInstanceOf(RcpFrame.BulkStringFrame.class, res3);
            assertEquals("test_value_123", ((RcpFrame.BulkStringFrame) res3).asUtf8String());

            // 4. EXISTS key
            sendFrame(out, new RcpFrame.ArrayFrame(List.of(
                new RcpFrame.BulkStringFrame("EXISTS".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("test:key1".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("nonexistent".getBytes(StandardCharsets.UTF_8))
            )));
            RcpFrame res4 = readFrame(in);
            assertInstanceOf(RcpFrame.IntegerFrame.class, res4);
            assertEquals(1L, ((RcpFrame.IntegerFrame) res4).value());

            // 5. DEL key
            sendFrame(out, new RcpFrame.ArrayFrame(List.of(
                new RcpFrame.BulkStringFrame("DEL".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("test:key1".getBytes(StandardCharsets.UTF_8))
            )));
            RcpFrame res5 = readFrame(in);
            assertInstanceOf(RcpFrame.IntegerFrame.class, res5);
            assertEquals(1L, ((RcpFrame.IntegerFrame) res5).value());

            // 6. GET deleted key -> NIL
            sendFrame(out, new RcpFrame.ArrayFrame(List.of(
                new RcpFrame.BulkStringFrame("GET".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("test:key1".getBytes(StandardCharsets.UTF_8))
            )));
            RcpFrame res6 = readFrame(in);
            assertInstanceOf(RcpFrame.BulkStringFrame.class, res6);
            assertTrue(((RcpFrame.BulkStringFrame) res6).isNil());
        }
    }

    @Test
    @DisplayName("Expiration and TTL: SET with EX, EXPIRE, TTL semantics")
    void testTtlAndExpiration() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // SET k v EX 1
            sendFrame(out, new RcpFrame.ArrayFrame(List.of(
                new RcpFrame.BulkStringFrame("SET".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("temp:key".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("temp_val".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("EX".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("1".getBytes(StandardCharsets.UTF_8))
            )));
            readFrame(in);

            // TTL immediately
            sendFrame(out, new RcpFrame.ArrayFrame(List.of(
                new RcpFrame.BulkStringFrame("TTL".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("temp:key".getBytes(StandardCharsets.UTF_8))
            )));
            RcpFrame ttlFrame = readFrame(in);
            assertInstanceOf(RcpFrame.IntegerFrame.class, ttlFrame);
            assertTrue(((RcpFrame.IntegerFrame) ttlFrame).value() >= 1L);

            // Wait 1200ms for expiration
            Thread.sleep(1200);

            // GET -> NIL
            sendFrame(out, new RcpFrame.ArrayFrame(List.of(
                new RcpFrame.BulkStringFrame("GET".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("temp:key".getBytes(StandardCharsets.UTF_8))
            )));
            RcpFrame getFrame = readFrame(in);
            assertInstanceOf(RcpFrame.BulkStringFrame.class, getFrame);
            assertTrue(((RcpFrame.BulkStringFrame) getFrame).isNil());

            // TTL of expired key -> -2
            sendFrame(out, new RcpFrame.ArrayFrame(List.of(
                new RcpFrame.BulkStringFrame("TTL".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("temp:key".getBytes(StandardCharsets.UTF_8))
            )));
            RcpFrame ttlExpired = readFrame(in);
            assertEquals(-2L, ((RcpFrame.IntegerFrame) ttlExpired).value());
        }
    }

    @Test
    @DisplayName("Atomic Counters & Batch Operations: INCR, DECR, INCRBY, MGET, MSET")
    void testCountersAndBatch() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // 1. INCR counter -> 1
            sendFrame(out, new RcpFrame.ArrayFrame(List.of(
                new RcpFrame.BulkStringFrame("INCR".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("counter".getBytes(StandardCharsets.UTF_8))
            )));
            assertEquals(1L, ((RcpFrame.IntegerFrame) readFrame(in)).value());

            // 2. INCRBY counter 5 -> 6
            sendFrame(out, new RcpFrame.ArrayFrame(List.of(
                new RcpFrame.BulkStringFrame("INCRBY".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("counter".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("5".getBytes(StandardCharsets.UTF_8))
            )));
            assertEquals(6L, ((RcpFrame.IntegerFrame) readFrame(in)).value());

            // 3. DECR counter -> 5
            sendFrame(out, new RcpFrame.ArrayFrame(List.of(
                new RcpFrame.BulkStringFrame("DECR".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("counter".getBytes(StandardCharsets.UTF_8))
            )));
            assertEquals(5L, ((RcpFrame.IntegerFrame) readFrame(in)).value());

            // 4. MSET k1 v1 k2 v2
            sendFrame(out, new RcpFrame.ArrayFrame(List.of(
                new RcpFrame.BulkStringFrame("MSET".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("k1".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("v1".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("k2".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("v2".getBytes(StandardCharsets.UTF_8))
            )));
            assertEquals("OK", ((RcpFrame.SimpleStringFrame) readFrame(in)).content());

            // 5. MGET k1 missing k2
            sendFrame(out, new RcpFrame.ArrayFrame(List.of(
                new RcpFrame.BulkStringFrame("MGET".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("k1".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("missing".getBytes(StandardCharsets.UTF_8)),
                new RcpFrame.BulkStringFrame("k2".getBytes(StandardCharsets.UTF_8))
            )));
            RcpFrame mgetResp = readFrame(in);
            assertInstanceOf(RcpFrame.ArrayFrame.class, mgetResp);
            List<RcpFrame> elems = ((RcpFrame.ArrayFrame) mgetResp).elements();
            assertEquals(3, elems.size());
            assertEquals("v1", ((RcpFrame.BulkStringFrame) elems.get(0)).asUtf8String());
            assertTrue(((RcpFrame.BulkStringFrame) elems.get(1)).isNil());
            assertEquals("v2", ((RcpFrame.BulkStringFrame) elems.get(2)).asUtf8String());
        }
    }

    private void sendFrame(OutputStream out, RcpFrame frame) throws Exception {
        byte[] bytes = RcpCodec.encode(frame);
        out.write(bytes);
        out.flush();
    }

    private RcpFrame readFrame(InputStream in) throws Exception {
        ByteBuffer buf = ByteBuffer.allocate(8192);
        while (true) {
            int b = in.read();
            if (b == -1) throw new IllegalStateException("Socket closed unexpectedly");
            buf.put((byte) b);
            ByteBuffer readView = buf.duplicate();
            readView.flip();
            RcpFrame frame = RcpCodec.decode(readView);
            if (frame != null) {
                // If extra unparsed bytes remained, shift them back
                int consumed = readView.position();
                buf.flip();
                buf.position(consumed);
                buf.compact();
                return frame;
            }
        }
    }
}
