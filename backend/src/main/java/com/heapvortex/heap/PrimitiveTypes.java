package com.heapvortex.heap;

import com.heapvortex.exception.HprofFormatException;

/** Type codes used inside heap dumps. Code 2 is an object reference. */
public final class PrimitiveTypes {

    private PrimitiveTypes() {
    }

    /** Readable name of a primitive element type, for example 10 gives int. */
    public static String name(int type) {
        return switch (type) {
            case 4 -> "boolean";
            case 5 -> "char";
            case 6 -> "float";
            case 7 -> "double";
            case 8 -> "byte";
            case 9 -> "short";
            case 10 -> "int";
            case 11 -> "long";
            default -> throw new HprofFormatException("Unknown primitive type code " + type);
        };
    }

    /** Number of bytes a value of this type takes in the dump. */
    public static int size(int type, int idSize) {
        return switch (type) {
            case 2 -> idSize;
            case 4, 8 -> 1;
            case 5, 9 -> 2;
            case 6, 10 -> 4;
            case 7, 11 -> 8;
            default -> throw new HprofFormatException("Unknown value type code " + type);
        };
    }
}