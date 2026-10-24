package com.heapvortex.dto;

/**
 * One row of the class histogram.
 * dataBytes is the size of the field or element data as stored in the dump.
 * It does not include object headers, so it is lower than the sizes jmap shows.
 */
public record ClassHistogramEntryDto(String className, long instanceCount, long dataBytes) {}