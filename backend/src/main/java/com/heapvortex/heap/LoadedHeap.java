package com.heapvortex.heap;

/** A parsed heap dump: the class information and the object graph. */
public final class LoadedHeap {

    private final ClassIndex classes;
    private final ObjectGraph graph;

    public LoadedHeap(ClassIndex classes, ObjectGraph graph) {
        this.classes = classes;
        this.graph = graph;
    }

    public ClassIndex classes() {
        return classes;
    }

    public ObjectGraph graph() {
        return graph;
    }

    /** The type of an object. A class object is an instance of java.lang.Class. */
    public String typeName(int node) {
        long classId = graph.classIdAt(node);
        return switch (graph.kindAt(node)) {
            case CLASS -> "java.lang.Class";
            case PRIMITIVE_ARRAY -> PrimitiveTypes.name((int) -classId) + "[]";
            default -> classes.className(classId);
        };
    }

    /** Like typeName, but a class object is shown as: class com.example.Foo */
    public String displayName(int node) {
        if (graph.kindAt(node) == ObjectGraph.Kind.CLASS) {
            return "class " + classes.className(graph.idAt(node));
        }
        return typeName(node);
    }
}