package com.heapvortex.heap;

import com.heapvortex.exception.InvalidRequestException;

/** Object ids travel as hex strings (0x7f3a...), because JSON numbers lose precision above 2^53. */
public final class ObjectIds {

    private ObjectIds() {
    }

    public static String format(long id) {
        return "0x" + Long.toHexString(id);
    }

    /** Accepts the id with or without the 0x prefix. */
    public static long parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("Invalid object id");
        }
        String hex = value.startsWith("0x") || value.startsWith("0X") ? value.substring(2) : value;
        try {
            return Long.parseUnsignedLong(hex, 16);
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("Invalid object id: " + value);
        }
    }
}