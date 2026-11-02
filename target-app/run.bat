@echo off
javac LeakyApp.java
java -Xmx256m -XX:+HeapDumpOnOutOfMemoryError LeakyApp %*