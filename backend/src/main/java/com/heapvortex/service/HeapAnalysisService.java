package com.heapvortex.service;

import com.heapvortex.dto.ClassHistogramEntryDto;
import com.heapvortex.dto.HeapSummaryDto;

import java.util.List;

public interface HeapAnalysisService {

    HeapSummaryDto summary(String dumpId);

    List<ClassHistogramEntryDto> histogram(String dumpId, int limit, HistogramSort sort);
}