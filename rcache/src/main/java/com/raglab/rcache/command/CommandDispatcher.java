package com.raglab.rcache.command;

import com.raglab.rcache.protocol.RcpFrame;
import com.raglab.rcache.storage.RCacheStorageEngine;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses and executes incoming commands against the RCacheStorageEngine.
 * Implements:
 * PING, SET, GET, DEL, EXISTS (Phase 7)
 * EXPIRE, TTL, INCR, DECR, MGET, MSET (Phase 8)
 */
public class CommandDispatcher {

    private final RCacheStorageEngine storageEngine;

    public CommandDispatcher(RCacheStorageEngine storageEngine) {
        this.storageEngine = storageEngine;
    }

    public RcpFrame dispatch(RcpFrame request) {
        if (request == null) {
            return new RcpFrame.ErrorFrame("ERR empty command frame");
        }

        if (!(request instanceof RcpFrame.ArrayFrame arrayFrame)) {
            return new RcpFrame.ErrorFrame("ERR Protocol error: expected Array frame");
        }

        List<RcpFrame> elements = arrayFrame.elements();
        if (elements.isEmpty()) {
            return new RcpFrame.ErrorFrame("ERR empty command array");
        }

        // First argument is the command name
        String commandName = extractString(elements.get(0));
        if (commandName == null) {
            return new RcpFrame.ErrorFrame("ERR invalid command name");
        }

        commandName = commandName.toUpperCase(Locale.ROOT);

        try {
            return switch (commandName) {
                case "PING" -> handlePing(elements);
                case "SET" -> handleSet(elements);
                case "GET" -> handleGet(elements);
                case "DEL" -> handleDel(elements);
                case "EXISTS" -> handleExists(elements);
                case "EXPIRE" -> handleExpire(elements);
                case "TTL" -> handleTtl(elements);
                case "INCR" -> handleIncr(elements, 1);
                case "DECR" -> handleIncr(elements, -1);
                case "INCRBY" -> handleIncrBy(elements);
                case "MGET" -> handleMget(elements);
                case "MSET" -> handleMset(elements);
                default -> new RcpFrame.ErrorFrame("ERR unknown command '" + commandName + "'");
            };
        } catch (IllegalStateException e) {
            return new RcpFrame.ErrorFrame(e.getMessage());
        } catch (Exception e) {
            return new RcpFrame.ErrorFrame("ERR execution error: " + e.getMessage());
        }
    }

    private RcpFrame handlePing(List<RcpFrame> args) {
        if (args.size() == 1) {
            return new RcpFrame.SimpleStringFrame("PONG");
        } else if (args.size() == 2) {
            byte[] msgBytes = extractBytes(args.get(1));
            return new RcpFrame.BulkStringFrame(msgBytes);
        } else {
            return new RcpFrame.ErrorFrame("ERR wrong number of arguments for 'ping' command");
        }
    }

    private RcpFrame handleSet(List<RcpFrame> args) {
        // SET key value [EX seconds]
        if (args.size() < 3) {
            return new RcpFrame.ErrorFrame("ERR wrong number of arguments for 'set' command");
        }

        String key = extractString(args.get(1));
        byte[] value = extractBytes(args.get(2));
        if (key == null || value == null) {
            return new RcpFrame.ErrorFrame("ERR invalid key or value");
        }

        long ttlSeconds = -1L;
        if (args.size() > 3) {
            if (args.size() != 5) {
                return new RcpFrame.ErrorFrame("ERR syntax error in 'set' options");
            }
            String opt = extractString(args.get(3));
            if (opt == null || !opt.equalsIgnoreCase("EX")) {
                return new RcpFrame.ErrorFrame("ERR syntax error, expected 'EX'");
            }
            String ttlStr = extractString(args.get(4));
            try {
                ttlSeconds = Long.parseLong(ttlStr);
                if (ttlSeconds <= 0) {
                    return new RcpFrame.ErrorFrame("ERR invalid expire time in 'set' command");
                }
            } catch (NumberFormatException e) {
                return new RcpFrame.ErrorFrame("ERR value is not an integer or out of range");
            }
        }

        storageEngine.set(key, value, ttlSeconds);
        return new RcpFrame.SimpleStringFrame("OK");
    }

    private RcpFrame handleGet(List<RcpFrame> args) {
        if (args.size() != 2) {
            return new RcpFrame.ErrorFrame("ERR wrong number of arguments for 'get' command");
        }
        String key = extractString(args.get(1));
        if (key == null) {
            return new RcpFrame.ErrorFrame("ERR invalid key");
        }
        byte[] val = storageEngine.get(key);
        if (val == null) {
            return RcpFrame.BulkStringFrame.NIL;
        }
        return new RcpFrame.BulkStringFrame(val);
    }

    private RcpFrame handleDel(List<RcpFrame> args) {
        if (args.size() < 2) {
            return new RcpFrame.ErrorFrame("ERR wrong number of arguments for 'del' command");
        }
        List<String> keys = new ArrayList<>(args.size() - 1);
        for (int i = 1; i < args.size(); i++) {
            String k = extractString(args.get(i));
            if (k != null) keys.add(k);
        }
        int deleted = storageEngine.deleteMulti(keys);
        return new RcpFrame.IntegerFrame(deleted);
    }

    private RcpFrame handleExists(List<RcpFrame> args) {
        if (args.size() < 2) {
            return new RcpFrame.ErrorFrame("ERR wrong number of arguments for 'exists' command");
        }
        List<String> keys = new ArrayList<>(args.size() - 1);
        for (int i = 1; i < args.size(); i++) {
            String k = extractString(args.get(i));
            if (k != null) keys.add(k);
        }
        int count = storageEngine.exists(keys);
        return new RcpFrame.IntegerFrame(count);
    }

    private RcpFrame handleExpire(List<RcpFrame> args) {
        if (args.size() != 3) {
            return new RcpFrame.ErrorFrame("ERR wrong number of arguments for 'expire' command");
        }
        String key = extractString(args.get(1));
        String secStr = extractString(args.get(2));
        if (key == null || secStr == null) {
            return new RcpFrame.ErrorFrame("ERR invalid key or ttl");
        }
        long ttl;
        try {
            ttl = Long.parseLong(secStr);
        } catch (NumberFormatException e) {
            return new RcpFrame.ErrorFrame("ERR value is not an integer or out of range");
        }
        boolean ok = storageEngine.expire(key, ttl);
        return new RcpFrame.IntegerFrame(ok ? 1 : 0);
    }

    private RcpFrame handleTtl(List<RcpFrame> args) {
        if (args.size() != 2) {
            return new RcpFrame.ErrorFrame("ERR wrong number of arguments for 'ttl' command");
        }
        String key = extractString(args.get(1));
        if (key == null) {
            return new RcpFrame.ErrorFrame("ERR invalid key");
        }
        long remaining = storageEngine.ttl(key);
        return new RcpFrame.IntegerFrame(remaining);
    }

    private RcpFrame handleIncr(List<RcpFrame> args, long delta) {
        if (args.size() != 2) {
            return new RcpFrame.ErrorFrame("ERR wrong number of arguments for command");
        }
        String key = extractString(args.get(1));
        if (key == null) {
            return new RcpFrame.ErrorFrame("ERR invalid key");
        }
        try {
            long res = storageEngine.incrBy(key, delta);
            return new RcpFrame.IntegerFrame(res);
        } catch (IllegalStateException e) {
            return new RcpFrame.ErrorFrame(e.getMessage());
        } catch (ArithmeticException e) {
            return new RcpFrame.ErrorFrame("ERR increment or decrement would overflow");
        }
    }

    private RcpFrame handleIncrBy(List<RcpFrame> args) {
        if (args.size() != 3) {
            return new RcpFrame.ErrorFrame("ERR wrong number of arguments for 'incrby' command");
        }
        String key = extractString(args.get(1));
        String deltaStr = extractString(args.get(2));
        if (key == null || deltaStr == null) {
            return new RcpFrame.ErrorFrame("ERR invalid key or delta");
        }
        try {
            long delta = Long.parseLong(deltaStr);
            long res = storageEngine.incrBy(key, delta);
            return new RcpFrame.IntegerFrame(res);
        } catch (NumberFormatException e) {
            return new RcpFrame.ErrorFrame("ERR value is not an integer or out of range");
        } catch (ArithmeticException e) {
            return new RcpFrame.ErrorFrame("ERR increment or decrement would overflow");
        }
    }

    private RcpFrame handleMget(List<RcpFrame> args) {
        if (args.size() < 2) {
            return new RcpFrame.ErrorFrame("ERR wrong number of arguments for 'mget' command");
        }
        List<String> keys = new ArrayList<>(args.size() - 1);
        for (int i = 1; i < args.size(); i++) {
            String k = extractString(args.get(i));
            if (k != null) keys.add(k);
        }
        List<byte[]> values = storageEngine.mget(keys);
        List<RcpFrame> frames = new ArrayList<>(values.size());
        for (byte[] val : values) {
            frames.add(val == null ? RcpFrame.BulkStringFrame.NIL : new RcpFrame.BulkStringFrame(val));
        }
        return new RcpFrame.ArrayFrame(frames);
    }

    private RcpFrame handleMset(List<RcpFrame> args) {
        // MSET k1 v1 k2 v2 ...
        int numArgs = args.size() - 1;
        if (numArgs < 2 || numArgs % 2 != 0) {
            return new RcpFrame.ErrorFrame("ERR wrong number of arguments for 'mset' command");
        }
        Map<String, byte[]> map = new LinkedHashMap<>();
        for (int i = 1; i < args.size(); i += 2) {
            String k = extractString(args.get(i));
            byte[] v = extractBytes(args.get(i + 1));
            if (k == null || v == null) {
                return new RcpFrame.ErrorFrame("ERR invalid key or value in 'mset'");
            }
            map.put(k, v);
        }
        storageEngine.mset(map);
        return new RcpFrame.SimpleStringFrame("OK");
    }

    private static String extractString(RcpFrame frame) {
        return switch (frame) {
            case RcpFrame.BulkStringFrame b -> b.asUtf8String();
            case RcpFrame.SimpleStringFrame s -> s.content();
            case RcpFrame.IntegerFrame n -> Long.toString(n.value());
            default -> null;
        };
    }

    private static byte[] extractBytes(RcpFrame frame) {
        return switch (frame) {
            case RcpFrame.BulkStringFrame b -> b.data();
            case RcpFrame.SimpleStringFrame s -> s.content().getBytes(StandardCharsets.UTF_8);
            case RcpFrame.IntegerFrame n -> Long.toString(n.value()).getBytes(StandardCharsets.US_ASCII);
            default -> null;
        };
    }
}

