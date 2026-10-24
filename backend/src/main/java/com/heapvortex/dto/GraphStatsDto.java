package com.heapvortex.dto;

import java.util.Map;

/** objectCount includes the class objects. objectsByKind has INSTANCE, CLASS, OBJECT_ARRAY, PRIMITIVE_ARRAY. */
public record GraphStatsDto(long objectCount, long edgeCount, long rootCount, Map<String, Long> objectsByKind) {}