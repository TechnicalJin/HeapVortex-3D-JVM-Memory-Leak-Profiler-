package com.heapvortex.heap;

import com.heapvortex.dto.ClassHistogramEntryDto;
import com.heapvortex.support.HprofTestWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassHistogramBuilderTest {

    private static List<ClassHistogramEntryDto> histogramOf(byte[] bytes) throws IOException {
        ClassHistogramBuilder builder = new ClassHistogramBuilder();
        HprofReader.read(new ByteArrayInputStream(bytes), builder);
        return builder.build();
    }

    @Test
    void countsInstancesAndArraysPerClassAndSortsByBytes() throws IOException {
        byte[] bytes = new HprofTestWriter(8)
                .string(1, "java/lang/String")
                .string(2, "com/example/Foo")
                .string(3, "[Ljava/lang/String;")
                .loadClass(100, 1)
                .loadClass(101, 2)
                .loadClass(102, 3)
                .classDump(100, 0)
                .classDump(101, 0)
                .classDump(102, 0)
                .instance(1, 101, new byte[8])
                .instance(2, 101, new byte[8])
                .instance(3, 101, new byte[8])
                .instance(4, 100, new byte[16])
                .instance(5, 100, new byte[16])
                .objectArray(6, 102, 0, 0, 0, 0, 0)
                .primitiveArray(7, 10, 10)
                .primitiveArray(8, 8, 4)
                .primitiveArray(9, 8, 4)
                .toBytes();

        List<ClassHistogramEntryDto> histogram = histogramOf(bytes);

        assertEquals(List.of(
                new ClassHistogramEntryDto("int[]", 1, 40),
                new ClassHistogramEntryDto("java.lang.String[]", 1, 40),
                new ClassHistogramEntryDto("java.lang.String", 2, 32),
                new ClassHistogramEntryDto("com.example.Foo", 3, 24),
                new ClassHistogramEntryDto("byte[]", 2, 8)),
                histogram);
    }

    @Test
    void sameClassNameFromTwoClassLoadersIsMerged() throws IOException {
        byte[] bytes = new HprofTestWriter(8)
                .string(1, "com/example/Foo")
                .loadClass(100, 1)
                .loadClass(101, 1)
                .instance(1, 100, new byte[8])
                .instance(2, 101, new byte[8])
                .toBytes();

        assertEquals(List.of(new ClassHistogramEntryDto("com.example.Foo", 2, 16)), histogramOf(bytes));
    }

    @Test
    void aClassWithoutNameGetsAPlaceholder() throws IOException {
        byte[] bytes = new HprofTestWriter(8).instance(1, 999, new byte[8]).toBytes();

        assertEquals("unknown-class-0x3e7", histogramOf(bytes).get(0).className());
    }

    @Test
    void anEmptyDumpGivesAnEmptyHistogram() throws IOException {
        assertEquals(List.of(), histogramOf(new HprofTestWriter(8).toBytes()));
    }
}