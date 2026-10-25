package com.heapvortex.heap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HeapLoader {

    private final HeapDumpStorage storage;
    private final int maxObjects;
    private final Map<String, LoadedHeap> cache;

    public HeapLoader(HeapDumpStorage storage,
                      @Value("${heapvortex.analysis.max-objects:2000000}") int maxObjects,
                      @Value("${heapvortex.analysis.cache-size:2}") int cacheSize) {
        this.storage = storage;
        this.maxObjects = maxObjects;
        // access order: the entry that was used longest ago is removed first
        this.cache = new LinkedHashMap<>(4, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, LoadedHeap> eldest) {
                return size() > cacheSize;
            }
        };
    }

    public synchronized LoadedHeap load(String dumpId) {
        Path file = storage.resolve(dumpId);
        String key = cacheKey(dumpId, file);
        LoadedHeap cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        LoadedHeap loaded = read(file);
        cache.put(key, loaded);
        return loaded;
    }

    private LoadedHeap read(Path file) {
        try {
            ClassIndex classes = new ClassIndex();
            HprofReader.read(file, classes);                       // pass 1
            ObjectGraphBuilder builder = new ObjectGraphBuilder(classes, maxObjects);
            HprofReader.read(file, builder);                       // pass 2
            return new LoadedHeap(classes, builder.build());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read heap dump " + file.getFileName(), e);
        }
    }

    /** A changed file (size or time) gets a new key, so an old graph is never reused for it. */
    private String cacheKey(String dumpId, Path file) {
        try {
            return dumpId + "|" + Files.size(file) + "|" + Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}