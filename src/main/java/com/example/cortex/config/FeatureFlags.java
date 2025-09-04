package com.example.cortex.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Clean feature flags - no rollout logic
 */
public class FeatureFlags {
    
    private static final Logger logger = LoggerFactory.getLogger(FeatureFlags.class);
    
    private final Map<String, Object> flags = new HashMap<>();

    public FeatureFlags() {
        loadFeatureFlags();
    }

    private void loadFeatureFlags() {
        setDefaults();
        
        try (InputStream is = getClass().getResourceAsStream("/feature-flags.yml")) {
            if (is != null) {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                @SuppressWarnings("unchecked")
                Map<String, Object> yamlFlags = mapper.readValue(is, Map.class);
                flags.putAll(yamlFlags);
                logger.info("Loaded feature flags from YAML");
            }
        } catch (Exception e) {
            logger.warn("Could not load feature-flags.yml, using defaults", e);
        }

        overrideWithEnvironment();
        logger.info("Feature flags initialized: {}", flags);
    }

    private void setDefaults() {
        flags.put("metrics.enabled", true);
        flags.put("metrics.jvm.enabled", true);
        flags.put("formats.prometheus.enabled", true);
        flags.put("formats.opentelemetry.enabled", false);
        flags.put("buffer.enabled", true);
        flags.put("retry.enabled", true);
        flags.put("health.check.enabled", true);
    }

    private void overrideWithEnvironment() {
        System.getenv().forEach((key, value) -> {
            if (key.startsWith("FEATURE_")) {
                String flagKey = key.substring(8).toLowerCase().replace("_", ".");
                if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                    flags.put(flagKey, Boolean.parseBoolean(value));
                } else {
                    flags.put(flagKey, value);
                }
            }
        });
    }

    public boolean isMetricsEnabled() {
        return getBooleanFlag("metrics.enabled", true);
    }

    public boolean isJvmMetricsEnabled() {
        return getBooleanFlag("metrics.jvm.enabled", true);
    }

    public boolean isPrometheusEnabled() {
        return getBooleanFlag("formats.prometheus.enabled", true);
    }

    public boolean isOpenTelemetryEnabled() {
        return getBooleanFlag("formats.opentelemetry.enabled", false);
    }

    public boolean isBufferingEnabled() {
        return getBooleanFlag("buffer.enabled", true);
    }

    public boolean isRetryEnabled() {
        return getBooleanFlag("retry.enabled", true);
    }

    public boolean isHealthCheckEnabled() {
        return getBooleanFlag("health.check.enabled", true);
    }

    public boolean getBooleanFlag(String key, boolean defaultValue) {
        Object value = flags.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return defaultValue;
    }

    public String getStringFlag(String key, String defaultValue) {
        Object value = flags.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    public void updateFlag(String key, Object value) {
        flags.put(key, value);
        logger.info("Updated feature flag: {} = {}", key, value);
    }

    public Map<String, Object> getAllFlags() {
        return new HashMap<>(flags);
    }
}