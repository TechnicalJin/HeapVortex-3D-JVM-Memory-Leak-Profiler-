package com.heapvortex.service;

import com.heapvortex.dto.GcRootPathDto;
import com.heapvortex.dto.GraphStatsDto;
import com.heapvortex.dto.ObjectDetailsDto;
import com.heapvortex.dto.ObjectSummaryDto;

import java.util.List;

public interface HeapGraphService {

    GraphStatsDto stats(String dumpId);

    /** Objects of exactly this type, for example java.util.HashMap$Node or byte[]. Class objects are not listed. */
    List<ObjectSummaryDto> instancesOf(String dumpId, String className, int limit);

    ObjectDetailsDto object(String dumpId, String objectId, int maxReferences);

    GcRootPathDto pathToGcRoot(String dumpId, String objectId, int maxDepth);
}