package com.example.cortex.service;

import com.example.cortex.config.FeatureFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clean feature flag service - no rollout logic
 */
public class FeatureFlagService {
    
    private static final Logger logger = LoggerFactory.getLogger(FeatureFlagService.class);
    
    private final FeatureFlags featureFlags;

    public FeatureFlagService(FeatureFlags featureFlags) {
        this.featureFlags = featureFlags;
        logger.info("FeatureFlagService initialized");
    }

    public boolean shouldPublishMetrics() {
        return featureFlags.isMetricsEnabled();
    }

    public boolean shouldPublishJvmMetrics() {
        return featureFlags.isJvmMetricsEnabled();
    }

    public boolean shouldUseFormat(String formatName) {
        return switch (formatName.toLowerCase()) {
            case "prometheus" -> featureFlags.isPrometheusEnabled();
            case "opentelemetry", "otel" -> featureFlags.isOpenTelemetryEnabled();
            default -> false;
        };
    }

    public boolean shouldUseBuffering() {
        return featureFlags.isBufferingEnabled();
    }

    public boolean shouldUseRetry() {
        return featureFlags.isRetryEnabled();
    }

    public void updateFlag(String flagName, Object value) {
        featureFlags.updateFlag(flagName, value);
        logger.info("Updated feature flag: {} = {}", flagName, value);
    }

    public java.util.Map<String, Object> getAllFlags() {
        return featureFlags.getAllFlags();
    }
}