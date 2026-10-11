package com.raglab.rcache.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Represents a discrete message frame in the RCache Protocol (RCP).
 * Supports:
 * - Simple String: +<str>\r\n
 * - Error: -<ERR> <msg>\r\n
 * - Integer: :<num>\r\n
 * - Bulk String: $<len>\r\n<bytes>\r\n (or $-1\r\n for NIL)
 * - Array: *<len>\r\n<frames...>
 */
public sealed interface RcpFrame {

    record SimpleStringFrame(String content) implements RcpFrame {
        public SimpleStringFrame {
            Objects.requireNonNull(content, "content cannot be null");
        }
    }

    record ErrorFrame(String message) implements RcpFrame {
        public ErrorFrame {
            Objects.requireNonNull(message, "message cannot be null");
        }
    }

    record IntegerFrame(long value) implements RcpFrame {}

    record BulkStringFrame(byte[] data) implements RcpFrame {
        public static final BulkStringFrame NIL = new BulkStringFrame(null);

        public boolean isNil() {
            return data == null;
        }

        public String asUtf8String() {
            return isNil() ? null : new String(data, StandardCharsets.UTF_8);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BulkStringFrame that = (BulkStringFrame) o;
            return Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }

        @Override
        public String toString() {
            return isNil() ? "BulkStringFrame[NIL]" : "BulkStringFrame[" + asUtf8String() + "]";
        }
    }

    record ArrayFrame(List<RcpFrame> elements) implements RcpFrame {
        public static final ArrayFrame EMPTY = new ArrayFrame(List.of());

        public ArrayFrame {
            elements = elements == null ? List.of() : List.copyOf(elements);
        }
    }
}

