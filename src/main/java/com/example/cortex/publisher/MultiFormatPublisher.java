package com.example.cortex.publisher;

import com.example.cortex.format.MetricFormat;
import io.micrometer.core.instrument.Meter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Publisher that can send metrics to multiple formats simultaneously
 */
public class MultiFormatPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(MultiFormatPublisher.class);
    
    private final List<MetricFormat> formats;
    private final ExecutorService executorService;

    public MultiFormatPublisher(List<MetricFormat> formats) {
        this.formats = List.copyOf(formats);
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "multi-format-publisher");
            t.setDaemon(true);
            return t;
        });
        
        logger.info("MultiFormatPublisher initialized with {} formats: {}", 
                   formats.size(), 
                   formats.stream().map(MetricFormat::getFormatName).toList());
    }

    /**
     * Publish metrics to all enabled formats
     */
    public CompletableFuture<PublishResult> publishMetrics(List<Meter> meters) {
        if (formats.isEmpty()) {
            return CompletableFuture.completedFuture(new PublishResult(0, 0));
        }

        List<CompletableFuture<Boolean>> futures = formats.stream()
                .filter(MetricFormat::isEnabled)
                .map(format -> publishToFormat(format, meters))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    int successCount = 0;
                    int totalCount = futures.size();
                    
                    for (CompletableFuture<Boolean> future : futures) {
                        try {
                            if (future.get()) {
                                successCount++;
                            }
                        } catch (Exception e) {
                            logger.debug("Format publishing failed", e);
                        }
                    }
                    
                    return new PublishResult(successCount, totalCount);
                });
    }

    private CompletableFuture<Boolean> publishToFormat(MetricFormat format, List<Meter> meters) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean success = format.sendMetrics(meters).get();
                logger.debug("Published to {} format: {}", format.getFormatName(), success);
                return success;
            } catch (Exception e) {
                logger.warn("Failed to publish to {} format", format.getFormatName(), e);
                return false;
            }
        }, executorService);
    }

    /**
     * Get all formats
     */
    public List<MetricFormat> getFormats() {
        return formats;
    }

    /**
     * Get enabled formats
     */
    public List<MetricFormat> getEnabledFormats() {
        return formats.stream()
                .filter(MetricFormat::isEnabled)
                .toList();
    }

    /**
     * Check if any format is available
     */
    public boolean isAnyFormatAvailable() {
        return formats.stream()
                .anyMatch(format -> format.isEnabled() && format.isEndpointAvailable());
    }

    public void close() {
        executorService.shutdown();
        formats.forEach(MetricFormat::close);
        logger.info("MultiFormatPublisher closed");
    }

    public record PublishResult(int successCount, int totalCount) {
        public boolean isFullSuccess() {
            return successCount == totalCount && totalCount > 0;
        }
        
        public boolean isPartialSuccess() {
            return successCount > 0 && successCount < totalCount;
        }
        
        public boolean isFailure() {
            return successCount == 0;
        }
    }
}