package com.example.cortex.registry;

import com.example.cortex.config.CortexConfig;
import com.example.cortex.publisher.MultiFormatPublisher;
import com.example.cortex.service.FeatureFlagService;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Universal meter registry that publishes to multiple formats
 */
public class UniversalCortexMeterRegistry extends StepMeterRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(UniversalCortexMeterRegistry.class);
    
    private final CortexConfig config;
    private final MultiFormatPublisher publisher;
    private final FeatureFlagService featureFlagService;

    public UniversalCortexMeterRegistry(CortexConfig config, 
                                      Clock clock, 
                                      MultiFormatPublisher publisher,
                                      FeatureFlagService featureFlagService) {
        this(config, clock, publisher, featureFlagService, new NamedThreadFactory("universal-metrics-publisher"));
    }

    public UniversalCortexMeterRegistry(CortexConfig config, 
                                      Clock clock, 
                                      MultiFormatPublisher publisher,
                                      FeatureFlagService featureFlagService,
                                      ThreadFactory threadFactory) {
        super(config, clock);
        this.config = config;
        this.publisher = publisher;
        this.featureFlagService = featureFlagService;
        
        // Start publishing only if metrics are enabled
        if (featureFlagService.shouldPublishMetrics()) {
            start(threadFactory);
            logger.info("UniversalCortexMeterRegistry started with {} formats", 
                       publisher.getEnabledFormats().size());
        } else {
            logger.info("UniversalCortexMeterRegistry initialized but publishing disabled by feature flags");
        }
    }

    /**
     * Publish metrics to all enabled formats
     */
    @Override
    protected void publish() {
        // Check if metrics should be published
        if (!featureFlagService.shouldPublishMetrics()) {
            logger.debug("Metrics publishing disabled by feature flags, skipping");
            return;
        }

        List<Meter> validMeters = getMeters().stream()
                .filter(this::isValidMeter)
                .collect(Collectors.toList());

        if (validMeters.isEmpty()) {
            logger.debug("No valid meters to publish");
            return;
        }

        logger.debug("Publishing {} meters to {} enabled formats", 
                    validMeters.size(), publisher.getEnabledFormats().size());
        
        publisher.publishMetrics(validMeters)
                .thenAccept(result -> {
                    if (result.isFullSuccess()) {
                        logger.debug("Successfully published to all {} formats", result.totalCount());
                    } else if (result.isPartialSuccess()) {
                        logger.warn("Partial success: {}/{} formats succeeded", 
                                   result.successCount(), result.totalCount());
                    } else {
                        logger.error("Failed to publish to any format ({} attempts)", result.totalCount());
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Exception occurred during publishing", throwable);
                    return null;
                });
    }

    /**
     * Update configuration when feature flags change
     */
    public void updateConfiguration() {
        logger.info("Updating registry configuration due to feature flag changes");
        
        // Restart or stop publishing based on feature flags
        if (featureFlagService.shouldPublishMetrics() && !isRunning()) {
            start(new NamedThreadFactory("universal-metrics-publisher"));
            logger.info("Started metrics publishing due to feature flag update");
        } else if (!featureFlagService.shouldPublishMetrics() && isRunning()) {
            stop();
            logger.info("Stopped metrics publishing due to feature flag update");
        }
    }

    /**
     * Check if a meter should be published
     */
    private boolean isValidMeter(Meter meter) {
        for (Measurement measurement : meter.measure()) {
            double value = measurement.getValue();
            if (Double.isFinite(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the registry is currently running/publishing
     */
    public boolean isRunning() {
        // This is a simplified check - in reality you'd track the scheduler state
        return featureFlagService.shouldPublishMetrics();
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }
    
    public CortexConfig getConfig() {
        return config;
    }

    @Override
    public void close() {
        super.close();
        publisher.close();
        logger.info("UniversalCortexMeterRegistry closed");
    }

    // Convenience methods for creating meters with validation
    
    public Counter counter(String name, String description, String... tags) {
        if (!featureFlagService.shouldPublishMetrics()) {
            // Return a no-op counter if metrics are disabled
            return new NoOpCounter();
        }
        
        return Counter.builder(name)
                .description(description)
                .tags(tags)
                .register(this);
    }

    public Timer timer(String name, String description, String... tags) {
        if (!featureFlagService.shouldPublishMetrics()) {
            return new NoOpTimer();
        }
        
        return Timer.builder(name)
                .description(description)
                .tags(tags)
                .register(this);
    }

    public <T> Gauge gauge(String name, T stateObject, 
                          java.util.function.ToDoubleFunction<T> valueFunction,
                          String description, String... tags) {
        if (!featureFlagService.shouldPublishMetrics()) {
            return new NoOpGauge();
        }
        
        return Gauge.builder(name, stateObject, valueFunction)
                .description(description)
                .tags(tags)
                .register(this);
    }

    // No-op implementations for when metrics are disabled
    private static class NoOpCounter implements Counter {
        @Override public void increment() {}
        @Override public void increment(double amount) {}
        @Override public double count() { return 0; }
        @Override public Id getId() { return new Id("noop", Tags.empty(), null, null, Type.COUNTER); }
        @Override public Iterable<Measurement> measure() { return List.of(); }
    }

    private static class NoOpTimer implements Timer {
        @Override public void record(long amount, TimeUnit unit) {}
        @Override public <T> T recordCallable(java.util.concurrent.Callable<T> f) throws Exception { return f.call(); }
        @Override public void record(java.time.Duration duration) {}
        @Override public <T> T record(Supplier<T> f) { return f.get(); }
        @Override public void record(Runnable f) { f.run(); }
        @Override public long count() { return 0; }
        @Override public double totalTime(TimeUnit unit) { return 0; }
        @Override public double mean(TimeUnit unit) { return 0; }
        @Override public double max(TimeUnit unit) { return 0; }
        @Override public TimeUnit baseTimeUnit() { return TimeUnit.MILLISECONDS; }
        @Override public Id getId() { return new Id("noop", Tags.empty(), null, null, Type.TIMER); }
        @Override public Iterable<Measurement> measure() { return List.of(); }
        @Override public HistogramSnapshot takeSnapshot() {
            return HistogramSnapshot.empty(0L,0,0);
        }
    }

    private static class NoOpGauge implements Gauge {
        @Override public double value() { return 0; }
        @Override public Id getId() { return new Id("noop", Tags.empty(), null, null, Type.GAUGE); }
        @Override public Iterable<Measurement> measure() { return List.of(); }
    }
}
