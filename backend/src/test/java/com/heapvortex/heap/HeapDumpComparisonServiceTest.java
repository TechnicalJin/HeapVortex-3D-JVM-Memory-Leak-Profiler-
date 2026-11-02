
package com.heapvortex.heap;

import com.heapvortex.dto.ClassHistogramEntryDto;
import com.heapvortex.dto.HeapDumpComparisonDto;
import com.heapvortex.service.HeapAnalysisService;
import com.heapvortex.service.HistogramSort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeapDumpComparisonServiceTest {

    @Mock
    private HeapAnalysisService analysisService;

    private HeapDumpComparisonService comparisonService;

    @BeforeEach
    void setUp() {
        comparisonService = new HeapDumpComparisonService(analysisService);
    }

    @Test
    void compareCalculatesGrowthAndHandlesMissingClasses() {
        when(analysisService.histogram(
                "baseline", Integer.MAX_VALUE, HistogramSort.BYTES))
                .thenReturn(List.of(
                        new ClassHistogramEntryDto("byte[]", 100, 1000),
                        new ClassHistogramEntryDto("java.lang.String", 50, 500)));

        when(analysisService.histogram(
                "comparison", Integer.MAX_VALUE, HistogramSort.BYTES))
                .thenReturn(List.of(
                        new ClassHistogramEntryDto("byte[]", 120, 1500),
                        new ClassHistogramEntryDto("java.lang.Integer", 10, 200)));

        HeapDumpComparisonDto result =
                comparisonService.compare("baseline", "comparison", 20);

        assertEquals("baseline", result.baselineDumpId());
        assertEquals("comparison", result.comparisonDumpId());

        var byteArray = result.classGrowth().stream()
                .filter(row -> row.className().equals("byte[]"))
                .findFirst()
                .orElseThrow();

        assertEquals(100, byteArray.baselineInstanceCount());
        assertEquals(120, byteArray.comparisonInstanceCount());
        assertEquals(20, byteArray.instanceCountGrowth());
        assertEquals(1000, byteArray.baselineDataBytes());
        assertEquals(1500, byteArray.comparisonDataBytes());
        assertEquals(500, byteArray.dataBytesGrowth());

        var removedClass = result.classGrowth().stream()
                .filter(row -> row.className().equals("java.lang.String"))
                .findFirst()
                .orElseThrow();

        assertEquals(50, removedClass.baselineInstanceCount());
        assertEquals(0, removedClass.comparisonInstanceCount());
        assertEquals(-50, removedClass.instanceCountGrowth());
        assertEquals(-500, removedClass.dataBytesGrowth());

        var newClass = result.classGrowth().stream()
                .filter(row -> row.className().equals("java.lang.Integer"))
                .findFirst()
                .orElseThrow();

        assertEquals(0, newClass.baselineInstanceCount());
        assertEquals(10, newClass.comparisonInstanceCount());
        assertEquals(10, newClass.instanceCountGrowth());
        assertEquals(200, newClass.dataBytesGrowth());
    }

    @Test
    void compareSortsByDataByteGrowthDescending() {
        when(analysisService.histogram(
                "baseline", Integer.MAX_VALUE, HistogramSort.BYTES))
                .thenReturn(List.of(
                        new ClassHistogramEntryDto("A", 1, 100),
                        new ClassHistogramEntryDto("B", 1, 100)));

        when(analysisService.histogram(
                "comparison", Integer.MAX_VALUE, HistogramSort.BYTES))
                .thenReturn(List.of(
                        new ClassHistogramEntryDto("A", 2, 300),
                        new ClassHistogramEntryDto("B", 5, 150)));

        var result = comparisonService.compare("baseline", "comparison", 10);

        assertEquals("A", result.classGrowth().get(0).className());
        assertEquals(200, result.classGrowth().get(0).dataBytesGrowth());

        assertEquals("B", result.classGrowth().get(1).className());
        assertEquals(50, result.classGrowth().get(1).dataBytesGrowth());
    }

    @Test
    void compareRespectsLimit() {
        when(analysisService.histogram(
                "baseline", Integer.MAX_VALUE, HistogramSort.BYTES))
                .thenReturn(List.of(
                        new ClassHistogramEntryDto("A", 1, 100),
                        new ClassHistogramEntryDto("B", 1, 200),
                        new ClassHistogramEntryDto("C", 1, 300)));

        when(analysisService.histogram(
                "comparison", Integer.MAX_VALUE, HistogramSort.BYTES))
                .thenReturn(List.of(
                        new ClassHistogramEntryDto("A", 2, 200),
                        new ClassHistogramEntryDto("B", 3, 400),
                        new ClassHistogramEntryDto("C", 4, 600)));

        var result = comparisonService.compare("baseline", "comparison", 2);

        assertEquals(2, result.classGrowth().size());
        assertEquals("C", result.classGrowth().get(0).className());
        assertEquals("B", result.classGrowth().get(1).className());
    }

    @Test
    void compareExcludesClassesWithNoGrowth() {
        when(analysisService.histogram(
                "baseline", Integer.MAX_VALUE, HistogramSort.BYTES))
                .thenReturn(List.of(
                        new ClassHistogramEntryDto("A", 5, 100)));

        when(analysisService.histogram(
                "comparison", Integer.MAX_VALUE, HistogramSort.BYTES))
                .thenReturn(List.of(
                        new ClassHistogramEntryDto("A", 5, 100)));

        var result = comparisonService.compare("baseline", "comparison", 10);

        assertTrue(result.classGrowth().isEmpty());
    }
}
