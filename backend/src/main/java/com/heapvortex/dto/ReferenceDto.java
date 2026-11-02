package com.heapvortex.dto;

/** field is the field name, "static NAME" for a static field, or "[element]" for an array element. */
public record ReferenceDto(String field, String objectId, String className, long shallowBytes) {}