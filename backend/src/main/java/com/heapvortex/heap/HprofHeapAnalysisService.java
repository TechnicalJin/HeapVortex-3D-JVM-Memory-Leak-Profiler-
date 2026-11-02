package com.heapvortex.heap;

import com.heapvortex.dto.ClassHistogramEntryDto;
import com.heapvortex.dto.HeapSummaryDto;
import com.heapvortex.service.HeapAnalysisService;
import com.heapvortex.service.HistogramSort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Service
public class HprofHeapAnalysisService implements HeapAnalysisService {

    private final HeapDumpStorage storage;

    public HprofHeapAnalysisService(HeapDumpStorage storage) {
        this.storage = storage;
    }

    @Override
    public HeapSummaryDto summary(String dumpId) {
        HeapSummaryBuilder builder = new HeapSummaryBuilder();
        parse(storage.resolve(dumpId), builder);
        return builder.build();
    }

    @Override
    public List<ClassHistogramEntryDto> histogram(String dumpId, int limit, HistogramSort sort) {
        ClassHistogramBuilder builder = new ClassHistogramBuilder();
        parse(storage.resolve(dumpId), builder);

        List<ClassHistogramEntryDto> entries = builder.build();
        if (sort == HistogramSort.COUNT) {
            entries = entries.stream()
                    .sorted(Comparator.comparingLong(ClassHistogramEntryDto::instanceCount).reversed()
                            .thenComparing(ClassHistogramEntryDto::className))
                    .toList();
        }
        return entries.stream().limit(limit).toList();
    }

    private void parse(Path file, HprofVisitor visitor) {
        try {
            HprofReader.read(file, visitor);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read heap dump " + file.getFileName(), e);
        }
    }
}