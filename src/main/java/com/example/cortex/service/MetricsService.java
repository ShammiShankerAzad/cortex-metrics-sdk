package com.example.cortex.service;

import com.example.cortex.registry.UniversalCortexMeterRegistry;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.system.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Metrics service with configurable JVM metrics
 */
public class MetricsService {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);
    
    private final UniversalCortexMeterRegistry registry;
    private final FeatureFlagService featureFlagService;

    public MetricsService(UniversalCortexMeterRegistry registry, FeatureFlagService featureFlagService) {
        this.registry = registry;
        this.featureFlagService = featureFlagService;
        
        setupMetrics();
        logger.info("MetricsService initialized");
    }

    private void setupMetrics() {
        if (featureFlagService.shouldPublishJvmMetrics()) {
            setupJvmMetrics();
            logger.info("JVM metrics enabled and configured");
        } else {
            logger.info("JVM metrics disabled by feature flag");
        }
        
        setupApplicationInfo();
    }

    private void setupJvmMetrics() {
        // Memory metrics (heap, non-heap, buffer pools)
        new JvmMemoryMetrics().bindTo(registry);
        logger.debug("Registered JVM memory metrics");

        // Garbage Collection metrics
        new JvmGcMetrics().bindTo(registry);
        logger.debug("Registered JVM garbage collection metrics");

        // CPU metrics
        new ProcessorMetrics().bindTo(registry);
        logger.debug("Registered system CPU metrics");

        // Additional JVM metrics
        new JvmThreadMetrics().bindTo(registry);
        new ClassLoaderMetrics().bindTo(registry);
        
        logger.info("Configured {} JVM metrics", getJvmMetricsCount());
    }

    private void setupApplicationInfo() {
        // Application info (always enabled)
        // Using a simple state object to hold our value
        AtomicInteger gaugeValue = new AtomicInteger(1);
        
        registry.gauge("application.info", gaugeValue, 
            value -> value.get(),  // ToDoubleFunction that gets the current value
            "Application information",
            "jvm_version", System.getProperty("java.version"),
            "jvm_vendor", System.getProperty("java.vendor"),
            "app_name", registry.getConfig().applicationName() != null ? 
                       registry.getConfig().applicationName() : "unknown",
            "jvm_metrics_enabled", String.valueOf(featureFlagService.shouldPublishJvmMetrics())
        );
    }

    public void updateMetricsConfiguration() {
        logger.info("Updating metrics configuration due to feature flag changes");
        boolean jvmEnabled = featureFlagService.shouldPublishJvmMetrics();
        logger.info("JVM metrics should be enabled: {} (restart required for changes)", jvmEnabled);
    }

    private int getJvmMetricsCount() {
        return (int) registry.getMeters().stream()
                .filter(meter -> meter.getId().getName().startsWith("jvm.") ||
                                meter.getId().getName().startsWith("system.") ||
                                meter.getId().getName().startsWith("process."))
                .count();
    }

    public UniversalCortexMeterRegistry getRegistry() {
        return registry;
    }

    public int getTotalMetricsCount() {
        return registry.getMeters().size();
    }

    public void close() {
        registry.close();
        logger.info("MetricsService closed");
    }
}