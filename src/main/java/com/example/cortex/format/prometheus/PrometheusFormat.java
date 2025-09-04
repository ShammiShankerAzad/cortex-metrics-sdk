package com.example.cortex.format.prometheus;

import com.example.cortex.config.CortexConfig;
import com.example.cortex.config.FeatureFlags;
import com.example.cortex.format.MetricFormat;
import io.micrometer.core.instrument.Meter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Prometheus Remote Write format implementation
 */
public class PrometheusFormat implements MetricFormat {
    
    private static final Logger logger = LoggerFactory.getLogger(PrometheusFormat.class);
    
    private final PrometheusRemoteWriteClient client;
    private final FeatureFlags featureFlags;

    public PrometheusFormat(CortexConfig config, FeatureFlags featureFlags) {
        this.featureFlags = featureFlags;
        this.client = new PrometheusRemoteWriteClient(config);
        logger.info("PrometheusFormat initialized");
    }

    @Override
    public String getFormatName() {
        return "prometheus";
    }

    @Override
    public boolean isEnabled() {
        return featureFlags.isPrometheusEnabled();
    }

    @Override
    public CompletableFuture<Boolean> sendMetrics(List<Meter> meters) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(false);
        }
        return client.sendMetrics(meters);
    }

    @Override
    public CompletableFuture<Boolean> sendRawMetrics(byte[] data) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(false);
        }
        return client.sendRawMetrics(data);
    }

    @Override
    public byte[] serializeMetrics(List<Meter> meters) {
        return client.serializeMetrics(meters);
    }

    @Override
    public boolean isEndpointAvailable() {
        return client.isEndpointAvailable();
    }

    @Override
    public Object getConfiguration() {
        return client.getConfiguration();
    }

    @Override
    public void close() {
        client.close();
        logger.info("PrometheusFormat closed");
    }
}