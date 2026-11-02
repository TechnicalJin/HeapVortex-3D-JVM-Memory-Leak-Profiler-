package com.heapvortex.service;

import com.heapvortex.exception.InvalidRequestException;

public enum HistogramSort {
    BYTES,
    COUNT;

    public static HistogramSort from(String value) {
        for (HistogramSort sort : values()) {
            if (sort.name().equalsIgnoreCase(value)) {
                return sort;
            }
        }
        throw new InvalidRequestException("Unknown sort '" + value + "', use bytes or count");
    }
}