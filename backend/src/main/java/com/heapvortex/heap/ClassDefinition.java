package com.heapvortex.heap;

/**
 * What a CLASS_DUMP tells us about a class.
 * Only reference-type static fields are kept (staticReferences), as the other statics are not needed.
 */
public record ClassDefinition(
        long classId,
        long superClassId,
        long[] instanceFieldNameIds,
        int[] instanceFieldTypes,
        long[] staticFieldNameIds,
        long[] staticReferences) {}