package com.example.cortex.service;

import com.example.cortex.buffer.BufferManager;
import com.example.cortex.config.CortexConfig;
import com.example.cortex.format.MetricFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Health check service for multiple formats
 */
public class HealthCheckService {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckService.class);
    
    private final List<MetricFormat> formats;
    private final BufferManager bufferManager;
    private final CortexConfig config;
    private final ScheduledExecutorService scheduler;

    public HealthCheckService(List<MetricFormat> formats, 
                             BufferManager bufferManager, 
                             CortexConfig config) {
        this.formats = formats;
        this.bufferManager = bufferManager;
        this.config = config;
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-check-thread");
            t.setDaemon(true);
            return t;
        });
        
        // Start periodic health checks
        scheduler.scheduleWithFixedDelay(
            this::performHealthCheck,
            0,
            config.healthCheckInterval().toSeconds(),
            TimeUnit.SECONDS
        );
        
        logger.info("HealthCheckService started for {} formats with interval: {}", 
                   formats.size(), config.healthCheckInterval());
    }

    private void performHealthCheck() {
        try {
            logger.debug("=== Health Check Report ===");
            
            // Check each format
            for (MetricFormat format : formats) {
                boolean enabled = format.isEnabled();
                boolean available = format.isEndpointAvailable();
                
                logger.debug("Format: {} - Enabled: {}, Available: {}", 
                           format.getFormatName(), enabled, available);
                
                if (enabled && !available) {
                    logger.warn("Format {} is enabled but endpoint is not available", 
                               format.getFormatName());
                }
            }
            
            // Check buffer status
            if (bufferManager != null) {
                var bufferStats = bufferManager.getStats();
                logger.debug("Buffer - Files: {}, Size: {} MB, Recovery: {}", 
                           bufferStats.bufferedFileCount(),
                           bufferStats.totalBufferedSize() / 1024 / 1024,
                           bufferStats.isRecovering());
                
                if (bufferStats.bufferedFileCount() > 20) {
                    logger.warn("High number of buffered files: {}", bufferStats.bufferedFileCount());
                }
            }
            
            logger.debug("=========================");
            
        } catch (Exception e) {
            logger.error("Error during health check", e);
        }
    }

    public OverallHealthStatus getOverallHealthStatus() {
        int enabledFormats = 0;
        int availableFormats = 0;
        
        for (MetricFormat format : formats) {
            if (format.isEnabled()) {
                enabledFormats++;
                if (format.isEndpointAvailable()) {
                    availableFormats++;
                }
            }
        }
        
        BufferManager.BufferStats bufferStats = bufferManager != null ? 
            bufferManager.getStats() : 
            new BufferManager.BufferStats(0, 0, false);
        
        return new OverallHealthStatus(
            enabledFormats,
            availableFormats,
            formats.stream().map(MetricFormat::getFormatName).toList(),
            bufferStats.bufferedFileCount(),
            bufferStats.totalBufferedSize(),
            bufferStats.isRecovering()
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
        logger.info("HealthCheckService shut down");
    }

    public record OverallHealthStatus(
        int enabledFormats,
        int availableFormats,
        List<String> formatNames,
        int bufferedFileCount,
        long totalBufferedSize,
        boolean recoveryInProgress
    ) {}
}