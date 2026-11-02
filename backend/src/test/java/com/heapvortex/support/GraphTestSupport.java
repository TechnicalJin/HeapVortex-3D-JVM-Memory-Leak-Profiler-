package com.heapvortex.support;

import com.heapvortex.heap.ClassIndex;
import com.heapvortex.heap.HprofReader;
import com.heapvortex.heap.LoadedHeap;
import com.heapvortex.heap.ObjectGraphBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;

/** Builds a graph the same way HeapLoader does: pass 1 for the classes, pass 2 for the objects. */
public final class GraphTestSupport {

    private GraphTestSupport() {
    }

    public static LoadedHeap load(byte[] bytes) throws IOException {
        return load(bytes, 1_000_000);
    }

    public static LoadedHeap load(byte[] bytes, int maxObjects) throws IOException {
        ClassIndex classes = new ClassIndex();
        HprofReader.read(new ByteArrayInputStream(bytes), classes);
        ObjectGraphBuilder builder = new ObjectGraphBuilder(classes, maxObjects);
        HprofReader.read(new ByteArrayInputStream(bytes), builder);
        return new LoadedHeap(classes, builder.build());
    }

    public static LoadedHeap load(Path file) throws IOException {
        ClassIndex classes = new ClassIndex();
        HprofReader.read(file, classes);
        ObjectGraphBuilder builder = new ObjectGraphBuilder(classes, 5_000_000);
        HprofReader.read(file, builder);
        return new LoadedHeap(classes, builder.build());
    }
}