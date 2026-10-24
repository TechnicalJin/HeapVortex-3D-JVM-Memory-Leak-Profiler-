package com.heapvortex.heap;

import com.heapvortex.exception.HeapTooLargeException;
import com.heapvortex.support.GraphTestSupport;
import com.heapvortex.support.HprofTestWriter;
import com.heapvortex.support.SampleHeaps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.heapvortex.support.SampleHeaps.*;
import static org.junit.jupiter.api.Assertions.*;

class ObjectGraphBuilderTest {

    private static ObjectGraph graph() throws IOException {
        return GraphTestSupport.load(SampleHeaps.holderAndChildren()).graph();
    }

    private static int node(ObjectGraph graph, long objectId) {
        int node = graph.indexOf(objectId);
        assertTrue(node >= 0, "object " + objectId + " is not in the graph");
        return node;
    }

    private static List<String> labelsOf(ObjectGraph graph, int node) {
        List<String> labels = new ArrayList<>();
        for (int edge = graph.edgeStart(node); edge < graph.edgeEnd(node); edge++) {
            labels.add(graph.labelOf(graph.edgeLabelId(edge)));
        }
        return labels;
    }

    @Test
    void everyObjectBecomesANodeWithItsKind() throws IOException {
        ObjectGraph graph = graph();

        assertEquals(8, graph.size()); // 3 classes, 3 instances, 1 object array, 1 byte array
        assertEquals(ObjectGraph.Kind.CLASS, graph.kindAt(node(graph, HOLDER_CLASS)));
        assertEquals(ObjectGraph.Kind.INSTANCE, graph.kindAt(node(graph, CHILD_1)));
        assertEquals(ObjectGraph.Kind.OBJECT_ARRAY, graph.kindAt(node(graph, OBJECT_ARRAY)));
        assertEquals(ObjectGraph.Kind.PRIMITIVE_ARRAY, graph.kindAt(node(graph, BYTE_ARRAY)));
    }

    @Test
    void primitiveArraysRememberTheirTypeAsANegativeClassId() throws IOException {
        ObjectGraph graph = graph();

        assertEquals(-8, graph.classIdAt(node(graph, BYTE_ARRAY)));
    }

    @Test
    void theNumberOfEdgesIsRight() throws IOException {
        // static INSTANCE, next, baseRef, and two array elements. The null element and the
        // element that is missing from the dump are not edges.
        assertEquals(5, graph().edgeCount());
    }

    @Test
    void fieldsAreReadClassFirstThenSuperClass() throws IOException {
        ObjectGraph graph = graph();

        assertEquals(List.of("next", "baseRef"), labelsOf(graph, node(graph, CHILD_1)));
    }

    @Test
    void theEdgesPointToTheRightObjects() throws IOException {
        ObjectGraph graph = graph();
        int child1 = node(graph, CHILD_1);

        int first = graph.edgeTarget(graph.edgeStart(child1));
        int second = graph.edgeTarget(graph.edgeStart(child1) + 1);

        assertEquals(CHILD_2, graph.idAt(first));
        assertEquals(BASE_1, graph.idAt(second));
    }

    @Test
    void aStaticFieldIsAnEdgeFromTheClass() throws IOException {
        ObjectGraph graph = graph();
        int holder = node(graph, HOLDER_CLASS);

        assertEquals(List.of("static INSTANCE"), labelsOf(graph, holder));
        assertEquals(CHILD_1, graph.idAt(graph.edgeTarget(graph.edgeStart(holder))));
    }

    @Test
    void arrayElementsAreEdgesWithoutALabel() throws IOException {
        ObjectGraph graph = graph();
        int array = node(graph, OBJECT_ARRAY);

        assertEquals(2, graph.edgeEnd(array) - graph.edgeStart(array));
        assertNull(graph.labelOf(graph.edgeLabelId(graph.edgeStart(array))));
    }

    @Test
    void rootsAreDeduplicatedAndKeepTheirKind() throws IOException {
        ObjectGraph graph = graph();

        assertEquals(2, graph.roots().size());
        assertEquals(RootKind.STICKY_CLASS, graph.rootKindOf(node(graph, HOLDER_CLASS)));
        assertEquals(RootKind.THREAD_OBJECT, graph.rootKindOf(node(graph, CHILD_1)));
        assertFalse(graph.isRoot(node(graph, CHILD_2)));
    }

    @Test
    void shallowBytesAreTheDataSizes() throws IOException {
        ObjectGraph graph = graph();

        assertEquals(20, graph.shallowBytesAt(node(graph, CHILD_1)));
        assertEquals(32, graph.shallowBytesAt(node(graph, OBJECT_ARRAY))); // 4 elements of 8 bytes
        assertEquals(4, graph.shallowBytesAt(node(graph, BYTE_ARRAY)));
    }

    @Test
    void unknownObjectsHaveNoNode() throws IOException {
        assertEquals(-1, graph().indexOf(99_999));
    }

    @Test
    void worksWithFourByteIdentifiers() throws IOException {
        HprofTestWriter writer = new HprofTestWriter(4);
        byte[] bytes = writer
                .string(1, "Node")
                .string(2, "next")
                .loadClass(100, 1)
                .classDump(100, 0, new long[]{2}, new int[]{2}, new long[0], new long[0])
                .instance(10, 100, writer.refs(11))
                .instance(11, 100, writer.refs(0))
                .toBytes();

        ObjectGraph graph = GraphTestSupport.load(bytes).graph();
        int first = node(graph, 10);

        assertEquals(List.of("next"), labelsOf(graph, first));
        assertEquals(11, graph.idAt(graph.edgeTarget(graph.edgeStart(first))));
    }

    @Test
    void stopsWhenTheDumpHasMoreObjectsThanTheLimit() {
        assertThrows(HeapTooLargeException.class,
                () -> GraphTestSupport.load(SampleHeaps.holderAndChildren(), 3));
    }
}