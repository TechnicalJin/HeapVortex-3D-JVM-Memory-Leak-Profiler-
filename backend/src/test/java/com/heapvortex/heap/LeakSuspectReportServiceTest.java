
package com.heapvortex.heap;

import com.heapvortex.dto.GcRootPathDto;
import com.heapvortex.dto.HeapDumpComparisonDto;
import com.heapvortex.dto.ObjectSummaryDto;
import com.heapvortex.service.HeapGraphService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeakSuspectReportServiceTest {

    @Mock
    private HeapDumpComparisonService comparisonService;

    @Mock
    private HeapGraphService graphService;

    private LeakSuspectReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new LeakSuspectReportService(
                comparisonService, graphService);
    }

    @Test
    void generateIncludesGrowthEvidenceAndGcRootPath() {
        var growth = new HeapDumpComparisonDto.ClassGrowthDto(
                "byte[]",
                100, 120, 20,
                1000, 1500, 500);

        when(comparisonService.compare("base", "current", 500))
                .thenReturn(new HeapDumpComparisonDto(
                        "base", "current", List.of(growth)));

        when(graphService.instancesOf("current", "byte[]", 2))
                .thenReturn(List.of(
                        new ObjectSummaryDto("0x1234", "byte[]", 512, 1)));

        var path = new GcRootPathDto(
                "0x1234",
                true,
                "STICKY_CLASS",
                List.of(new GcRootPathDto.PathNode(
                        "0x1234", "byte[]", null)));

        when(graphService.pathToGcRoot("current", "0x1234", 50))
                .thenReturn(path);

        var result = reportService.generate(
                "base", "current", 10, 2, 50);

        assertEquals("base", result.baselineDumpId());
        assertEquals("current", result.comparisonDumpId());
        assertEquals(1, result.suspects().size());

        var suspect = result.suspects().get(0);
        assertEquals("byte[]", suspect.className());
        assertEquals(20, suspect.instanceCountGrowth());
        assertEquals(500, suspect.dataBytesGrowth());
        assertTrue(suspect.evidence().contains("both increased"));

        assertEquals(1, suspect.sampleObjects().size());
        assertEquals("0x1234", suspect.sampleObjects().get(0).objectId());
        assertEquals(512, suspect.sampleObjects().get(0).shallowBytes());
        assertTrue(suspect.sampleObjects().get(0).gcRootPath().pathFound());

        verify(graphService).pathToGcRoot("current", "0x1234", 50);
    }

    @Test
    void generateExcludesClassesWithNoPositiveGrowth() {
        var shrinking = new HeapDumpComparisonDto.ClassGrowthDto(
                "java.lang.String",
                100, 80, -20,
                1000, 800, -200);

        when(comparisonService.compare("base", "current", 500))
                .thenReturn(new HeapDumpComparisonDto(
                        "base", "current", List.of(shrinking)));

        var result = reportService.generate(
                "base", "current", 10, 3, 100);

        assertTrue(result.suspects().isEmpty());
        verifyNoInteractions(graphService);
    }

    @Test
    void generateRejectsInvalidLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> reportService.generate(
                        "base", "current", 0, 3, 100));

        assertThrows(IllegalArgumentException.class,
                () -> reportService.generate(
                        "base", "current", 10, 11, 100));

        assertThrows(IllegalArgumentException.class,
                () -> reportService.generate(
                        "base", "current", 10, 3, 1001));

        verifyNoInteractions(comparisonService, graphService);
    }
}
