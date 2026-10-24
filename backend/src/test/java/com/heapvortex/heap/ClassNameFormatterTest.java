package com.heapvortex.heap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassNameFormatterTest {

    @Test
    void plainNamesUseDots() {
        assertEquals("java.lang.String", ClassNameFormatter.readable("java/lang/String"));
    }

    @Test
    void innerClassesKeepTheDollar() {
        assertEquals("java.util.HashMap$Node", ClassNameFormatter.readable("java/util/HashMap$Node"));
    }

    @Test
    void objectArraysGetBrackets() {
        assertEquals("java.lang.String[]", ClassNameFormatter.readable("[Ljava/lang/String;"));
    }

    @Test
    void primitiveArraysGetBrackets() {
        assertEquals("int[]", ClassNameFormatter.readable("[I"));
        assertEquals("byte[][]", ClassNameFormatter.readable("[[B"));
    }

    @Test
    void nullBecomesUnknown() {
        assertEquals("unknown", ClassNameFormatter.readable(null));
    }
}