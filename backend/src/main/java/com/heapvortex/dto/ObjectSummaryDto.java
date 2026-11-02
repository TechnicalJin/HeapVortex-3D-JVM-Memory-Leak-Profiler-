package com.heapvortex.dto;

public record ObjectSummaryDto(String objectId, String className, long shallowBytes, int referenceCount) {}