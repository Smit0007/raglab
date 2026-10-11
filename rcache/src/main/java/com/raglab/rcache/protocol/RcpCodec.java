package com.raglab.rcache.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Encodes and decodes RCache Protocol (RCP) frames.
 */
public class RcpCodec {

    public static final byte[] CRLF = new byte[]{'\r', '\n'};
    public static final byte PREFIX_SIMPLE_STRING = '+';
    public static final byte PREFIX_ERROR = '-';
    public static final byte PREFIX_INTEGER = ':';
    public static final byte PREFIX_BULK_STRING = '$';
    public static final byte PREFIX_ARRAY = '*';

    /**
     * Decodes a single frame from a ByteBuffer.
     * Returns null if the buffer does not yet contain a complete frame (partial read).
     */
    public static RcpFrame decode(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            return null;
        }

        buffer.mark();
        byte prefix = buffer.get();

        switch (prefix) {
            case PREFIX_SIMPLE_STRING -> {
                String line = readLine(buffer);
                if (line == null) {
                    buffer.reset();
                    return null;
                }
                return new RcpFrame.SimpleStringFrame(line);
            }
            case PREFIX_ERROR -> {
                String line = readLine(buffer);
                if (line == null) {
                    buffer.reset();
                    return null;
                }
                return new RcpFrame.ErrorFrame(line);
            }
            case PREFIX_INTEGER -> {
                String line = readLine(buffer);
                if (line == null) {
                    buffer.reset();
                    return null;
                }
                try {
                    long val = Long.parseLong(line);
                    return new RcpFrame.IntegerFrame(val);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid integer frame format: " + line);
                }
            }
            case PREFIX_BULK_STRING -> {
                String line = readLine(buffer);
                if (line == null) {
                    buffer.reset();
                    return null;
                }
                int length;
                try {
                    length = Integer.parseInt(line);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid bulk string length: " + line);
                }

                if (length == -1) {
                    return RcpFrame.BulkStringFrame.NIL;
                }

                if (length < 0) {
                    throw new IllegalArgumentException("Negative bulk string length other than -1: " + length);
                }

                // Check if buffer contains length bytes + 2 bytes for CRLF
                if (buffer.remaining() < length + 2) {
                    buffer.reset();
                    return null;
                }

                byte[] data = new byte[length];
                buffer.get(data);
                byte cr = buffer.get();
                byte lf = buffer.get();
                if (cr != '\r' || lf != '\n') {
                    throw new IllegalArgumentException("Malformed bulk string: missing trailing CRLF");
                }
                return new RcpFrame.BulkStringFrame(data);
            }
            case PREFIX_ARRAY -> {
                String line = readLine(buffer);
                if (line == null) {
                    buffer.reset();
                    return null;
                }
                int count;
                try {
                    count = Integer.parseInt(line);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid array length: " + line);
                }

                if (count == -1) {
                    return null; // or null array representation if needed
                }
                if (count < 0) {
                    throw new IllegalArgumentException("Negative array length: " + count);
                }

                List<RcpFrame> elements = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    RcpFrame elem = decode(buffer);
                    if (elem == null) {
                        buffer.reset();
                        return null;
                    }
                    elements.add(elem);
                }
                return new RcpFrame.ArrayFrame(elements);
            }
            default -> throw new IllegalArgumentException("Unknown protocol frame prefix byte: " + (char) prefix + " (0x" + Integer.toHexString(prefix) + ")");
        }
    }

    /**
     * Reads a line ending with \r\n from buffer. Returns null if CRLF not yet reached.
     */
    private static String readLine(ByteBuffer buffer) {
        int startPos = buffer.position();
        boolean foundCr = false;

        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == '\r') {
                foundCr = true;
            } else if (b == '\n' && foundCr) {
                int lineEnd = buffer.position() - 2;
                int length = lineEnd - startPos;
                byte[] bytes = new byte[length];
                int savedPos = buffer.position();
                buffer.position(startPos);
                buffer.get(bytes);
                buffer.position(savedPos);
                return new String(bytes, StandardCharsets.UTF_8);
            } else {
                foundCr = false;
            }
        }

        buffer.position(startPos);
        return null;
    }

    /**
     * Encodes an RcpFrame into raw wire bytes.
     */
    public static byte[] encode(RcpFrame frame) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            encodeToStream(frame, out);
        } catch (IOException e) {
            throw new RuntimeException("Encoding failure", e);
        }
        return out.toByteArray();
    }

    public static void encodeToStream(RcpFrame frame, ByteArrayOutputStream out) throws IOException {
        switch (frame) {
            case RcpFrame.SimpleStringFrame s -> {
                out.write(PREFIX_SIMPLE_STRING);
                out.write(s.content().getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
            }
            case RcpFrame.ErrorFrame err -> {
                out.write(PREFIX_ERROR);
                out.write(err.message().getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
            }
            case RcpFrame.IntegerFrame num -> {
                out.write(PREFIX_INTEGER);
                out.write(Long.toString(num.value()).getBytes(StandardCharsets.US_ASCII));
                out.write(CRLF);
            }
            case RcpFrame.BulkStringFrame b -> {
                out.write(PREFIX_BULK_STRING);
                if (b.isNil()) {
                    out.write(("-1").getBytes(StandardCharsets.US_ASCII));
                    out.write(CRLF);
                } else {
                    byte[] data = b.data();
                    out.write(Integer.toString(data.length).getBytes(StandardCharsets.US_ASCII));
                    out.write(CRLF);
                    out.write(data);
                    out.write(CRLF);
                }
            }
            case RcpFrame.ArrayFrame a -> {
                out.write(PREFIX_ARRAY);
                List<RcpFrame> elements = a.elements();
                out.write(Integer.toString(elements.size()).getBytes(StandardCharsets.US_ASCII));
                out.write(CRLF);
                for (RcpFrame element : elements) {
                    encodeToStream(element, out);
                }
            }
        }
    }
}

