package com.example.cortex.buffer;

import com.example.cortex.config.CortexConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple local metrics buffer for when Cortex is unavailable
 */
public class LocalMetricsBuffer {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalMetricsBuffer.class);
    
    private final Path bufferDirectory;
    private final long maxFileSize;
    private final AtomicLong fileCounter = new AtomicLong(0);

    public LocalMetricsBuffer(CortexConfig config) {
        this.bufferDirectory = Paths.get(config.bufferDirectory());
        this.maxFileSize = config.maxBufferFileSize();
        
        try {
            Files.createDirectories(bufferDirectory);
            logger.info("LocalMetricsBuffer initialized with directory: {}", bufferDirectory);
        } catch (IOException e) {
            logger.error("Failed to create buffer directory: {}", bufferDirectory, e);
            throw new RuntimeException("Failed to initialize buffer", e);
        }
    }

    /**
     * Buffer metrics data to local file
     */
    public void bufferMetrics(byte[] data) {
        try {
            String filename = "metrics_" + System.currentTimeMillis() + "_" + fileCounter.incrementAndGet() + ".buf";
            Path filePath = bufferDirectory.resolve(filename);
            
            Files.write(filePath, data, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            logger.debug("Buffered metrics to file: {}", filename);
            
        } catch (IOException e) {
            logger.error("Failed to buffer metrics to file", e);
        }
    }

    /**
     * Get all buffered metric files
     */
    public List<Path> getBufferedFiles() {
        try {
            return Files.list(bufferDirectory)
                    .filter(path -> path.toString().endsWith(".buf"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            logger.error("Failed to list buffered files", e);
            return new ArrayList<>();
        }
    }

    /**
     * Read metrics data from a buffered file
     */
    public byte[] readBufferedFile(Path file) throws IOException {
        return Files.readAllBytes(file);
    }

    /**
     * Delete a buffered file after successful sending
     */
    public void deleteBufferedFile(Path file) {
        try {
            Files.delete(file);
            logger.debug("Deleted buffered file: {}", file.getFileName());
        } catch (IOException e) {
            logger.warn("Failed to delete buffered file: {}", file.getFileName(), e);
        }
    }

    /**
     * Get buffer statistics
     */
    public BufferStats getStats() {
        try {
            List<Path> files = getBufferedFiles();
            long totalSize = files.stream()
                    .mapToLong(this::getFileSize)
                    .sum();
            
            return new BufferStats(files.size(), totalSize, false);
        } catch (Exception e) {
            logger.error("Failed to get buffer stats", e);
            return new BufferStats(0, 0, false);
        }
    }

    private long getFileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }

    public record BufferStats(int bufferedFileCount, long totalBufferedSize, boolean isRecovering) {}
}