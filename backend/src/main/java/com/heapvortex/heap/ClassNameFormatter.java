package com.heapvortex.heap;

/** Turns JVM class names from the dump into readable names. */
public final class ClassNameFormatter {

    private ClassNameFormatter() {
    }

    /**
     * java/lang/String gives java.lang.String,
     * [Ljava/lang/String; gives java.lang.String[],
     * [[I gives int[][].
     */
    public static String readable(String jvmName) {
        if (jvmName == null) {
            return "unknown";
        }
        int dimensions = 0;
        while (dimensions < jvmName.length() && jvmName.charAt(dimensions) == '[') {
            dimensions++;
        }
        if (dimensions == 0) {
            return jvmName.replace('/', '.');
        }

        String element = jvmName.substring(dimensions);
        String base;
        if (element.startsWith("L") && element.endsWith(";")) {
            base = element.substring(1, element.length() - 1).replace('/', '.');
        } else {
            base = switch (element) {
                case "B" -> "byte";
                case "C" -> "char";
                case "D" -> "double";
                case "F" -> "float";
                case "I" -> "int";
                case "J" -> "long";
                case "S" -> "short";
                case "Z" -> "boolean";
                default -> element;
            };
        }
        return base + "[]".repeat(dimensions);
    }
}