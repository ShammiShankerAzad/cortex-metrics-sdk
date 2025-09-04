package com.example.cortex;

import com.example.cortex.buffer.BufferManager;
import com.example.cortex.buffer.LocalMetricsBuffer;
import com.example.cortex.config.ConfigurationLoader;
import com.example.cortex.config.CortexConfig;
import com.example.cortex.config.FeatureFlags;
import com.example.cortex.format.MetricFormat;
import com.example.cortex.format.MetricFormatFactory;
import com.example.cortex.publisher.MultiFormatPublisher;
import com.example.cortex.registry.UniversalCortexMeterRegistry;
import com.example.cortex.service.FeatureFlagService;
import com.example.cortex.service.HealthCheckService;
import com.example.cortex.service.MetricsService;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Universal Cortex Metrics SDK - Clean Version (No Rollout Logic)
 * 
 * Usage:
 * var sdk = UniversalCortexMetricsSDK.builder()
 *     .endpoint("https://cortex.example.com/api/v1/push")
 *     .bearerToken("your-token")
 *     .applicationName("trading-app")
 *     .enableJvmMetrics(true)
 *     .build();
 */
public class CortexMetricsSDK {
    
    private static final Logger logger = LoggerFactory.getLogger(CortexMetricsSDK.class);
    
    private final UniversalCortexMeterRegistry registry;
    private final MetricsService metricsService;
    private final FeatureFlagService featureFlagService;
    private final HealthCheckService healthCheckService;
    private final MultiFormatPublisher publisher;
    private final List<MetricFormat> formats;

    private CortexMetricsSDK(CortexConfig config, FeatureFlags featureFlags) {
        logger.info("Initializing UniversalCortexMetricsSDK...");
        
        // Feature flags
        this.featureFlagService = new FeatureFlagService(featureFlags);
        
        // Metric formats
        this.formats = MetricFormatFactory.createEnabledFormats(config, featureFlags);
        this.publisher = new MultiFormatPublisher(formats);
        
        // Registry
        this.registry = new UniversalCortexMeterRegistry(config, Clock.SYSTEM, publisher, featureFlagService);
        
        // Services
        this.metricsService = new MetricsService(registry, featureFlagService);
        
        // Buffer and health check
        BufferManager bufferManager = null;
        if (featureFlags.isBufferingEnabled() && hasPrometheusFormat()) {
            LocalMetricsBuffer buffer = new LocalMetricsBuffer(config);
            bufferManager = new BufferManager(buffer, getPrometheusClient(), config);
        }
        
        this.healthCheckService = new HealthCheckService(formats, bufferManager, config);
        
        logger.info("SDK initialized - Formats: {}, JVM Metrics: {}", 
                   getEnabledFormats(), 
                   featureFlagService.shouldPublishJvmMetrics());
    }

    public Counter counter(String name, String description, String... tags) {
        return Counter.builder(name)
                .description(description)
                .tags(tags)
                .register(registry);
    }

    public Timer timer(String name, String description, String... tags) {
        return Timer.builder(name)
                .description(description)
                .tags(tags)
                .register(registry);
    }

    public <T> Gauge gauge(String name, T stateObject, 
                          java.util.function.ToDoubleFunction<T> valueFunction,
                          String description, String... tags) {
        return Gauge.builder(name, stateObject, valueFunction)
                .description(description)
                .tags(tags)
                .register(registry);
    }

    public boolean isMetricsEnabled() {
        return featureFlagService.shouldPublishMetrics();
    }

    public boolean isJvmMetricsEnabled() {
        return featureFlagService.shouldPublishJvmMetrics();
    }

    public void updateFlag(String flagName, Object value) {
        featureFlagService.updateFlag(flagName, value);
        
        if (flagName.startsWith("metrics.")) {
            metricsService.updateMetricsConfiguration();
            registry.updateConfiguration();
        }
    }

    public List<String> getEnabledFormats() {
        return publisher.getEnabledFormats().stream()
                .map(MetricFormat::getFormatName)
                .toList();
    }

    public SDKStatus getStatus() {
        return new SDKStatus(
            featureFlagService.shouldPublishMetrics(),
            featureFlagService.shouldPublishJvmMetrics(),
            publisher.isAnyFormatAvailable(),
            getEnabledFormats(),
            metricsService.getTotalMetricsCount()
        );
    }

    public java.util.Map<String, Object> getFeatureFlags() {
        return featureFlagService.getAllFlags();
    }

    public UniversalCortexMeterRegistry getRegistry() {
        return registry;
    }

    private boolean hasPrometheusFormat() {
        return formats.stream().anyMatch(f -> f.getFormatName().equals("prometheus"));
    }

    private Object getPrometheusClient() {
        return formats.stream()
                .filter(f -> f.getFormatName().equals("prometheus"))
                .findFirst()
                .orElse(null);
    }

    public void shutdown() {
        logger.info("Shutting down UniversalCortexMetricsSDK...");
        healthCheckService.shutdown();
        metricsService.close();
        publisher.close();
        logger.info("SDK shutdown completed");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CortexMetricsSDK fromConfiguration() {
        ConfigurationLoader configLoader = new ConfigurationLoader();
        CortexConfig config = CortexConfig.fromLoader(configLoader);
        FeatureFlags featureFlags = new FeatureFlags();
        return new CortexMetricsSDK(config, featureFlags);
    }

    public static class Builder {
        private final ConfigurationLoader configLoader = new ConfigurationLoader();
        private final java.util.Map<String, Object> featureFlags = new java.util.HashMap<>();
        
        public Builder endpoint(String endpoint) {
            configLoader.getAllProperties().put("cortex.endpoint", endpoint);
            return this;
        }
        
        public Builder bearerToken(String token) {
            configLoader.getAllProperties().put("cortex.auth.bearer-token", token);
            return this;
        }
        
        public Builder tenantId(String tenantId) {
            configLoader.getAllProperties().put("cortex.tenant-id", tenantId);
            return this;
        }
        
        public Builder applicationName(String name) {
            configLoader.getAllProperties().put("app.name", name);
            return this;
        }
        
        public Builder environment(String environment) {
            configLoader.getAllProperties().put("app.environment", environment);
            return this;
        }
        
        public Builder publishInterval(String interval) {
            configLoader.getAllProperties().put("cortex.step", interval);
            return this;
        }
        
        public Builder enableMetrics(boolean enabled) {
            featureFlags.put("metrics.enabled", enabled);
            return this;
        }
        
        public Builder enableJvmMetrics(boolean enabled) {
            featureFlags.put("metrics.jvm.enabled", enabled);
            return this;
        }
        
        public Builder enablePrometheus(boolean enabled) {
            featureFlags.put("formats.prometheus.enabled", enabled);
            return this;
        }
        
        public CortexMetricsSDK build() {
            CortexConfig config = CortexConfig.fromLoader(configLoader);
            
            FeatureFlags flags = new FeatureFlags() {
                @Override
                public boolean getBooleanFlag(String key, boolean defaultValue) {
                    if (featureFlags.containsKey(key)) {
                        return (Boolean) featureFlags.get(key);
                    }
                    return super.getBooleanFlag(key, defaultValue);
                }
            };
            
            return new CortexMetricsSDK(config, flags);
        }
    }

    public record SDKStatus(
        boolean metricsEnabled,
        boolean jvmMetricsEnabled,
        boolean anyFormatAvailable,
        List<String> enabledFormats,
        int totalMetrics
    ) {}
}