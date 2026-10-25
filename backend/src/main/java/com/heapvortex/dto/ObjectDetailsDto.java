package com.heapvortex.dto;

import java.util.List;

/** referenceCount is the real number of references. references can be shorter (the maxReferences cap). */
public record ObjectDetailsDto(
        String objectId,
        String kind,
        String className,
        long shallowBytes,
        int referenceCount,
        List<ReferenceDto> references) {}