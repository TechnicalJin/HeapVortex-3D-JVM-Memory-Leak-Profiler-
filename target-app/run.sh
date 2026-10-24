#!/bin/sh
javac LeakyApp.java
java -Xmx256m -XX:+HeapDumpOnOutOfMemoryError LeakyApp "$@"