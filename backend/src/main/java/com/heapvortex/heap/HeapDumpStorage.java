package com.heapvortex.heap;

import com.heapvortex.dto.HeapDumpInfoDto;
import com.heapvortex.exception.HeapDumpNotFoundException;
import com.heapvortex.exception.InvalidRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class HeapDumpStorage {

    private static final String EXTENSION = ".hprof";
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path directory;

    public HeapDumpStorage(@Value("${heapvortex.dumps.dir:dumps}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create heap dump directory " + this.directory, e);
        }
    }

    public Path directory() {
        return directory;
    }

    /** A new, not yet existing file path for a dump of the given JVM. */
    public Path newDumpPath(String pid) {
        if (pid == null || pid.isEmpty() || !pid.chars().allMatch(Character::isDigit)) {
            throw new InvalidRequestException("Invalid PID: " + pid);
        }
        String base = "heap-" + pid + "-" + LocalDateTime.now().format(STAMP);
        Path path = directory.resolve(base + EXTENSION);
        int counter = 1;
        while (Files.exists(path)) {
            path = directory.resolve(base + "-" + counter++ + EXTENSION);
        }
        return path;
    }

    /** All dumps in the folder, newest first. */
    public List<HeapDumpInfoDto> list() {
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(file -> file.getFileName().toString().endsWith(EXTENSION))
                    .map(this::describe)
                    .sorted(Comparator.comparing(HeapDumpInfoDto::createdAt).reversed())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public HeapDumpInfoDto get(String id) {
        return describe(resolve(id));
    }

    /** Turns an id from the URL into a file path, or fails if it is invalid or unknown. */
    public Path resolve(String id) {
        if (id == null || !VALID_ID.matcher(id).matches()) {
            throw new InvalidRequestException("Invalid heap dump id");
        }
        Path file = directory.resolve(id + EXTENSION).normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file)) {
            throw new HeapDumpNotFoundException(id);
        }
        return file;
    }

    public void delete(String id) {
        try {
            Files.delete(resolve(id));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public HeapDumpInfoDto describe(Path file) {
        try {
            String fileName = file.getFileName().toString();
            String id = fileName.substring(0, fileName.length() - EXTENSION.length());
            return new HeapDumpInfoDto(
                    id,
                    pidOf(id),
                    fileName,
                    Files.size(file),
                    Files.getLastModifiedTime(file).toInstant());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Our own files are named heap-PID-DATE-TIME. Any other file has an unknown PID. */
    private static String pidOf(String id) {
        String[] parts = id.split("-");
        return parts.length >= 3 && parts[0].equals("heap") ? parts[1] : "unknown";
    }
}