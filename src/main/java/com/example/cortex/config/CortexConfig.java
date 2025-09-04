package com.example.cortex.config;

import io.micrometer.core.instrument.step.StepRegistryConfig;
import java.time.Duration;

/**
 * Enhanced configuration interface
 */
public interface CortexConfig extends StepRegistryConfig {
    
    static CortexConfig fromLoader(ConfigurationLoader loader) {
        return new CortexConfig() {
            @Override
            public String get(String key) {
                return loader.get(key);
            }
        };
    }

    @Override
    default String prefix() {
        return "cortex";
    }

    default String endpoint() {
        String endpoint = get(prefix() + ".endpoint");
        if (endpoint == null) {
            throw new IllegalStateException("cortex.endpoint must be configured");
        }
        return endpoint;
    }

    default String tenantId() {
        return get(prefix() + ".tenant-id");
    }

    default String bearerToken() {
        return get(prefix() + ".auth.bearer-token");
    }

    default boolean authEnabled() {
        return bearerToken() != null && !bearerToken().trim().isEmpty();
    }

    default Duration connectTimeout() {
        String timeout = get(prefix() + ".connect-timeout");
        return timeout != null ? Duration.parse(timeout) : Duration.ofSeconds(10);
    }

    default Duration readTimeout() {
        String timeout = get(prefix() + ".read-timeout");
        return timeout != null ? Duration.parse(timeout) : Duration.ofSeconds(30);
    }

    default int maxRetryAttempts() {
        String attempts = get(prefix() + ".retry.max-attempts");
        return attempts != null ? Integer.parseInt(attempts) : 3;
    }

    default Duration initialRetryDelay() {
        String delay = get(prefix() + ".retry.initial-delay");
        return delay != null ? Duration.parse(delay) : Duration.ofSeconds(1);
    }

    default Duration maxRetryDelay() {
        String delay = get(prefix() + ".retry.max-delay");
        return delay != null ? Duration.parse(delay) : Duration.ofSeconds(30);
    }

    default boolean bufferEnabled() {
        String enabled = get(prefix() + ".buffer.enabled");
        return enabled == null || Boolean.parseBoolean(enabled);
    }

    default String bufferDirectory() {
        String dir = get(prefix() + ".buffer.directory");
        return dir != null ? dir : "./metrics-buffer";
    }

    default long maxBufferFileSize() {
        String size = get(prefix() + ".buffer.max-file-size");
        return size != null ? Long.parseLong(size) : 10 * 1024 * 1024;
    }

    default Duration healthCheckInterval() {
        String interval = get(prefix() + ".health-check.interval");
        return interval != null ? Duration.parse(interval) : Duration.ofMinutes(1);
    }

    default int maxBatchSize() {
        String batchSize = get(prefix() + ".batch-size");
        return batchSize != null ? Integer.parseInt(batchSize) : 10000;
    }

    default boolean compressionEnabled() {
        String compression = get(prefix() + ".compression.enabled");
        return compression == null || Boolean.parseBoolean(compression);
    }

    default String applicationName() {
        return get("app.name");
    }

    default String applicationVersion() {
        return get("app.version");
    }

    default String environment() {
        return get("app.environment");
    }
}