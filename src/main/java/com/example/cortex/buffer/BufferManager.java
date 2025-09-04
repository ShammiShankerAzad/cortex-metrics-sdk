package com.example.cortex.buffer;

import com.example.cortex.config.CortexConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages buffering and recovery of metrics when Cortex is down
 */
public class BufferManager {
    
    private static final Logger logger = LoggerFactory.getLogger(BufferManager.class);
    
    private final LocalMetricsBuffer buffer;
    private final Object cortexClient;
    private final CortexConfig config;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean recovering = new AtomicBoolean(false);

    public BufferManager(LocalMetricsBuffer buffer, Object cortexClient, CortexConfig config) {
        this.buffer = buffer;
        this.cortexClient = cortexClient;
        this.config = config;
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "buffer-manager");
            t.setDaemon(true);
            return t;
        });
        
        // Start recovery process
        scheduler.scheduleWithFixedDelay(this::attemptRecovery, 30, 30, TimeUnit.SECONDS);
        
        logger.info("BufferManager initialized");
    }

    /**
     * Buffer metrics when Cortex is unavailable
     */
    public void bufferMetrics(byte[] data) {
        buffer.bufferMetrics(data);
        logger.debug("Buffered metrics data ({} bytes)", data.length);
    }

    /**
     * Attempt to recover buffered metrics
     */
    private void attemptRecovery() {
        if (recovering.get()) {
            logger.debug("Recovery already in progress, skipping");
            return;
        }

        List<Path> bufferedFiles = buffer.getBufferedFiles();
        if (bufferedFiles.isEmpty()) {
            return;
        }

        recovering.set(true);
        try {
            logger.info("Starting recovery of {} buffered files", bufferedFiles.size());
            
            int successCount = 0;
            for (Path file : bufferedFiles) {
                try {
                    byte[] data = buffer.readBufferedFile(file);
                    
                    // Try to send the buffered data (simplified - would use actual client)
                    boolean sent = sendBufferedData(data);
                    
                    if (sent) {
                        buffer.deleteBufferedFile(file);
                        successCount++;
                        logger.debug("Successfully recovered and sent file: {}", file.getFileName());
                    } else {
                        logger.debug("Failed to send buffered file: {}", file.getFileName());
                        break; // Stop trying if one fails
                    }
                } catch (Exception e) {
                    logger.error("Error processing buffered file: {}", file.getFileName(), e);
                    break;
                }
            }
            
            if (successCount > 0) {
                logger.info("Successfully recovered {} buffered files", successCount);
            }
        } finally {
            recovering.set(false);
        }
    }

    private boolean sendBufferedData(byte[] data) {
        // Simplified send logic - in real implementation would use the actual client
        try {
            Thread.sleep(10); // Simulate network call
            return Math.random() > 0.1; // 90% success rate for demo
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Get buffer statistics
     */
    public BufferStats getStats() {
        LocalMetricsBuffer.BufferStats bufferStats = buffer.getStats();
        return new BufferStats(
            bufferStats.bufferedFileCount(),
            bufferStats.totalBufferedSize(),
            recovering.get()
        );
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        logger.info("BufferManager shut down");
    }

    public record BufferStats(int bufferedFileCount, long totalBufferedSize, boolean isRecovering) {}
}