package com.raglab.rcache.client;

import com.raglab.rcache.protocol.RcpCodec;
import com.raglab.rcache.protocol.RcpFrame;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Resilient, connection-pooled typed client for RCache Server.
 * Supports:
 * - Direct connection pool
 * - Configurable connect/socket timeouts
 * - Sliding-window fail-open circuit breaking
 * - Typed commands: ping, get, set, del, exists, expire, ttl, incr, decr, mget, mset
 */
public class RCacheClient implements AutoCloseable {

    private final String host;
    private final int port;
    private final int poolSize;
    private final int socketTimeoutMs;
    private final int connectTimeoutMs;
    private final RCacheCircuitBreaker circuitBreaker;
    private final BlockingQueue<PooledConnection> pool;
    private volatile boolean closed = false;

    public RCacheClient(String host, int port) {
        this(host, port, 8, 2000, 2000, 3, 5000);
    }

    public RCacheClient(String host, int port, int poolSize, int socketTimeoutMs, int connectTimeoutMs, int cbFailureThreshold, long cbResetTimeoutMs) {
        this.host = host;
        this.port = port;
        this.poolSize = poolSize;
        this.socketTimeoutMs = socketTimeoutMs;
        this.connectTimeoutMs = connectTimeoutMs;
        this.circuitBreaker = new RCacheCircuitBreaker(cbFailureThreshold, cbResetTimeoutMs);
        this.pool = new ArrayBlockingQueue<>(poolSize);
    }

    public boolean isCircuitBreakerOpen() {
        return circuitBreaker.getState() == RCacheCircuitBreaker.State.OPEN;
    }

    public String ping() {
        RcpFrame resp = executeSafe(new RcpFrame.ArrayFrame(List.of(
            new RcpFrame.BulkStringFrame("PING".getBytes(StandardCharsets.UTF_8))
        )), false);

        if (resp instanceof RcpFrame.SimpleStringFrame s) {
            return s.content();
        } else if (resp instanceof RcpFrame.BulkStringFrame b) {
            return b.asUtf8String();
        }
        return null;
    }

    public boolean set(String key, String value) {
        return set(key, value.getBytes(StandardCharsets.UTF_8), -1L);
    }

    public boolean set(String key, byte[] value, long ttlSeconds) {
        List<RcpFrame> args = new ArrayList<>(5);
        args.add(new RcpFrame.BulkStringFrame("SET".getBytes(StandardCharsets.UTF_8)));
        args.add(new RcpFrame.BulkStringFrame(key.getBytes(StandardCharsets.UTF_8)));
        args.add(new RcpFrame.BulkStringFrame(value));
        if (ttlSeconds > 0) {
            args.add(new RcpFrame.BulkStringFrame("EX".getBytes(StandardCharsets.UTF_8)));
            args.add(new RcpFrame.BulkStringFrame(Long.toString(ttlSeconds).getBytes(StandardCharsets.UTF_8)));
        }

        RcpFrame resp = executeSafe(new RcpFrame.ArrayFrame(args), true);
        if (resp instanceof RcpFrame.SimpleStringFrame s) {
            return "OK".equalsIgnoreCase(s.content());
        }
        return false;
    }

    public Optional<byte[]> get(String key) {
        RcpFrame resp = executeSafe(new RcpFrame.ArrayFrame(List.of(
            new RcpFrame.BulkStringFrame("GET".getBytes(StandardCharsets.UTF_8)),
            new RcpFrame.BulkStringFrame(key.getBytes(StandardCharsets.UTF_8))
        )), true);

        if (resp instanceof RcpFrame.BulkStringFrame b) {
            if (b.isNil()) return Optional.empty();
            return Optional.of(b.data());
        }
        return Optional.empty();
    }

    public Optional<String> getString(String key) {
        return get(key).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    public boolean delete(String key) {
        RcpFrame resp = executeSafe(new RcpFrame.ArrayFrame(List.of(
            new RcpFrame.BulkStringFrame("DEL".getBytes(StandardCharsets.UTF_8)),
            new RcpFrame.BulkStringFrame(key.getBytes(StandardCharsets.UTF_8))
        )), true);

        if (resp instanceof RcpFrame.IntegerFrame n) {
            return n.value() > 0;
        }
        return false;
    }

    public boolean exists(String key) {
        RcpFrame resp = executeSafe(new RcpFrame.ArrayFrame(List.of(
            new RcpFrame.BulkStringFrame("EXISTS".getBytes(StandardCharsets.UTF_8)),
            new RcpFrame.BulkStringFrame(key.getBytes(StandardCharsets.UTF_8))
        )), true);

        if (resp instanceof RcpFrame.IntegerFrame n) {
            return n.value() > 0;
        }
        return false;
    }

    public boolean expire(String key, long seconds) {
        RcpFrame resp = executeSafe(new RcpFrame.ArrayFrame(List.of(
            new RcpFrame.BulkStringFrame("EXPIRE".getBytes(StandardCharsets.UTF_8)),
            new RcpFrame.BulkStringFrame(key.getBytes(StandardCharsets.UTF_8)),
            new RcpFrame.BulkStringFrame(Long.toString(seconds).getBytes(StandardCharsets.UTF_8))
        )), true);

        if (resp instanceof RcpFrame.IntegerFrame n) {
            return n.value() > 0;
        }
        return false;
    }

    public long ttl(String key) {
        RcpFrame resp = executeSafe(new RcpFrame.ArrayFrame(List.of(
            new RcpFrame.BulkStringFrame("TTL".getBytes(StandardCharsets.UTF_8)),
            new RcpFrame.BulkStringFrame(key.getBytes(StandardCharsets.UTF_8))
        )), true);

        if (resp instanceof RcpFrame.IntegerFrame n) {
            return n.value();
        }
        return -2L;
    }

    public Long incr(String key) {
        return incrBy(key, 1L);
    }

    public Long decr(String key) {
        return incrBy(key, -1L);
    }

    public Long incrBy(String key, long delta) {
        // INCR/DECR are non-idempotent, retryable = false
        RcpFrame resp = executeSafe(new RcpFrame.ArrayFrame(List.of(
            new RcpFrame.BulkStringFrame("INCRBY".getBytes(StandardCharsets.UTF_8)),
            new RcpFrame.BulkStringFrame(key.getBytes(StandardCharsets.UTF_8)),
            new RcpFrame.BulkStringFrame(Long.toString(delta).getBytes(StandardCharsets.UTF_8))
        )), false);

        if (resp instanceof RcpFrame.IntegerFrame n) {
            return n.value();
        }
        return null;
    }

    public Map<String, byte[]> mget(List<String> keys) {
        if (keys == null || keys.isEmpty()) return Collections.emptyMap();
        List<RcpFrame> args = new ArrayList<>(keys.size() + 1);
        args.add(new RcpFrame.BulkStringFrame("MGET".getBytes(StandardCharsets.UTF_8)));
        for (String k : keys) {
            args.add(new RcpFrame.BulkStringFrame(k.getBytes(StandardCharsets.UTF_8)));
        }

        RcpFrame resp = executeSafe(new RcpFrame.ArrayFrame(args), true);
        if (resp instanceof RcpFrame.ArrayFrame arr) {
            Map<String, byte[]> result = new LinkedHashMap<>();
            List<RcpFrame> elems = arr.elements();
            for (int i = 0; i < keys.size() && i < elems.size(); i++) {
                if (elems.get(i) instanceof RcpFrame.BulkStringFrame b && !b.isNil()) {
                    result.put(keys.get(i), b.data());
                }
            }
            return result;
        }
        return Collections.emptyMap();
    }

    public boolean mset(Map<String, byte[]> map) {
        if (map == null || map.isEmpty()) return true;
        List<RcpFrame> args = new ArrayList<>(map.size() * 2 + 1);
        args.add(new RcpFrame.BulkStringFrame("MSET".getBytes(StandardCharsets.UTF_8)));
        for (Map.Entry<String, byte[]> e : map.entrySet()) {
            args.add(new RcpFrame.BulkStringFrame(e.getKey().getBytes(StandardCharsets.UTF_8)));
            args.add(new RcpFrame.BulkStringFrame(e.getValue()));
        }

        RcpFrame resp = executeSafe(new RcpFrame.ArrayFrame(args), true);
        if (resp instanceof RcpFrame.SimpleStringFrame s) {
            return "OK".equalsIgnoreCase(s.content());
        }
        return false;
    }

    /**
     * Executes frame with circuit breaker check and single retry if idempotent.
     */
    private RcpFrame executeSafe(RcpFrame request, boolean idempotent) {
        if (closed) {
            throw new RCacheException("RCache client is closed");
        }

        if (!circuitBreaker.allowRequest()) {
            // Fail open / circuit broken
            return null;
        }

        int maxAttempts = idempotent ? 2 : 1;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            PooledConnection conn = null;
            try {
                conn = borrowConnection();
                conn.sendFrame(request);
                RcpFrame response = conn.readFrame();
                circuitBreaker.recordSuccess();
                returnConnection(conn);
                return response;
            } catch (Exception e) {
                lastException = e;
                if (conn != null) {
                    conn.close();
                }
                circuitBreaker.recordFailure();
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        return null; // Return null on failure to allow fail-open
    }

    private PooledConnection borrowConnection() throws IOException {
        PooledConnection conn = pool.poll();
        if (conn != null && conn.isValid()) {
            return conn;
        }
        if (conn != null) {
            conn.close();
        }
        return new PooledConnection(host, port, connectTimeoutMs, socketTimeoutMs);
    }

    private void returnConnection(PooledConnection conn) {
        if (closed || conn == null || !conn.isValid()) {
            if (conn != null) conn.close();
            return;
        }
        if (!pool.offer(conn)) {
            conn.close();
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        PooledConnection conn;
        while ((conn = pool.poll()) != null) {
            conn.close();
        }
    }

    private static class PooledConnection implements AutoCloseable {
        private final Socket socket;
        private final OutputStream out;
        private final InputStream in;
        private final ByteBuffer readBuffer = ByteBuffer.allocate(64 * 1024);

        PooledConnection(String host, int port, int connectTimeoutMs, int socketTimeoutMs) throws IOException {
            this.socket = new Socket();
            this.socket.setTcpNoDelay(true);
            this.socket.setSoTimeout(socketTimeoutMs);
            this.socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            this.out = socket.getOutputStream();
            this.in = socket.getInputStream();
        }

        boolean isValid() {
            return socket.isConnected() && !socket.isClosed() && !socket.isInputShutdown() && !socket.isOutputShutdown();
        }

        void sendFrame(RcpFrame frame) throws IOException {
            byte[] bytes = RcpCodec.encode(frame);
            out.write(bytes);
            out.flush();
        }

        RcpFrame readFrame() throws IOException {
            while (true) {
                int b = in.read();
                if (b == -1) {
                    throw new IOException("Remote server closed socket connection");
                }
                readBuffer.put((byte) b);
                ByteBuffer dup = readBuffer.duplicate();
                dup.flip();
                RcpFrame frame = RcpCodec.decode(dup);
                if (frame != null) {
                    int consumed = dup.position();
                    readBuffer.flip();
                    readBuffer.position(consumed);
                    readBuffer.compact();
                    return frame;
                }
            }
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
}

