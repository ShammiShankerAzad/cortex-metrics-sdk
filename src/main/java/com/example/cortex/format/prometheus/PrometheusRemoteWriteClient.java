package com.example.cortex.format.prometheus;

import com.example.cortex.config.CortexConfig;
import io.micrometer.core.instrument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xerial.snappy.Snappy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simplified Prometheus Remote Write client
 */
public class PrometheusRemoteWriteClient {
    
    private static final Logger logger = LoggerFactory.getLogger(PrometheusRemoteWriteClient.class);
    
    private final CortexConfig config;
    private final HttpClient httpClient;
    private final AtomicBoolean available = new AtomicBoolean(true);
    
    private static final String CONTENT_TYPE = "application/x-protobuf";
    private static final String CONTENT_ENCODING = "snappy";
    private static final String USER_AGENT = "universal-cortex-sdk/3.0.0";

    public PrometheusRemoteWriteClient(CortexConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .version(HttpClient.Version.HTTP_2)
                .build();
        
        logger.info("PrometheusRemoteWriteClient initialized with endpoint: {}", config.endpoint());
    }

    public CompletableFuture<Boolean> sendMetrics(List<Meter> meters) {
        try {
            byte[] data = serializeMetrics(meters);
            return sendRawMetrics(data);
        } catch (Exception e) {
            logger.error("Failed to serialize metrics", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    public CompletableFuture<Boolean> sendRawMetrics(byte[] compressedData) {
        if (!available.get()) {
            logger.debug("Endpoint not available, skipping metrics send");
            return CompletableFuture.completedFuture(false);
        }

        HttpRequest request = buildHttpRequest(compressedData);
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (isSuccessResponse(response)) {
                        available.set(true);
                        logger.debug("Successfully sent metrics to Cortex");
                        return true;
                    } else {
                        logger.warn("Cortex endpoint returned error: {}", response.statusCode());
                        available.set(false);
                        return false;
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to send metrics to Cortex", throwable);
                    available.set(false);
                    return false;
                });
    }

    public byte[] serializeMetrics(List<Meter> meters) {
        try {
            // Simplified serialization - convert to Prometheus text format
            String metricsData = buildPrometheusFormat(meters);
            byte[] data = metricsData.getBytes();
            
            return config.compressionEnabled() ? Snappy.compress(data) : data;
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize metrics", e);
        }
    }

    private String buildPrometheusFormat(List<Meter> meters) {
        StringBuilder sb = new StringBuilder();
        long timestamp = System.currentTimeMillis();
        
        for (Meter meter : meters) {
            switch (meter.getId().getType()) {
                case COUNTER -> {
                    Counter counter = (Counter) meter;
                    sb.append(buildMetricLine(
                        sanitizeName(counter.getId().getName()) + "_total", 
                        counter.count(), 
                        counter.getId().getTags(), 
                        timestamp));
                }
                case GAUGE -> {
                    Gauge gauge = (Gauge) meter;
                    if (Double.isFinite(gauge.value())) {
                        sb.append(buildMetricLine(
                            sanitizeName(gauge.getId().getName()), 
                            gauge.value(), 
                            gauge.getId().getTags(), 
                            timestamp));
                    }
                }
                case TIMER -> {
                    Timer timer = (Timer) meter;
                    String baseName = sanitizeName(timer.getId().getName());
                    List<Tag> tags = timer.getId().getTags();
                    
                    sb.append(buildMetricLine(baseName + "_count", timer.count(), tags, timestamp));
                    sb.append(buildMetricLine(baseName + "_sum", 
                        timer.totalTime(timer.baseTimeUnit()) / 1_000_000_000.0, tags, timestamp));
                }
                case DISTRIBUTION_SUMMARY -> {
                    DistributionSummary summary = (DistributionSummary) meter;
                    String baseName = sanitizeName(summary.getId().getName());
                    List<Tag> tags = summary.getId().getTags();
                    
                    sb.append(buildMetricLine(baseName + "_count", summary.count(), tags, timestamp));
                    sb.append(buildMetricLine(baseName + "_sum", summary.totalAmount(), tags, timestamp));
                }
            }
        }
        
        return sb.toString();
    }

    private String buildMetricLine(String name, double value, List<Tag> tags, long timestamp) {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        
        // Add tags
        if (!tags.isEmpty()) {
            sb.append("{");
            boolean first = true;
            for (Tag tag : tags) {
                if (!first) sb.append(",");
                sb.append(tag.getKey()).append("=\"").append(tag.getValue()).append("\"");
                first = false;
            }
            
            // Add application labels
            if (config.applicationName() != null) {
                if (!first) sb.append(",");
                sb.append("application=\"").append(config.applicationName()).append("\"");
            }
            if (config.environment() != null) {
                sb.append(",environment=\"").append(config.environment()).append("\"");
            }
            sb.append("}");
        }
        
        sb.append(" ").append(value).append(" ").append(timestamp).append("\n");
        return sb.toString();
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_:]", "_");
    }

    private HttpRequest buildHttpRequest(byte[] data) {
        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(config.endpoint()))
                .timeout(config.readTimeout())
                .header("Content-Type", CONTENT_TYPE)
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(data));

        if (config.compressionEnabled()) {
            requestBuilder.header("Content-Encoding", CONTENT_ENCODING);
        }

        if (config.tenantId() != null) {
            requestBuilder.header("X-Scope-OrgID", config.tenantId());
        }

        if (config.authEnabled()) {
            requestBuilder.header("Authorization", "Bearer " + config.bearerToken());
        }

        return requestBuilder.build();
    }

    private boolean isSuccessResponse(HttpResponse<String> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    public boolean isEndpointAvailable() {
        return available.get();
    }

    public Object getConfiguration() {
        return java.util.Map.of(
            "endpoint", config.endpoint(),
            "compression", config.compressionEnabled(),
            "tenant", config.tenantId() != null ? config.tenantId() : "none",
            "auth", config.authEnabled()
        );
    }

    public void close() {
        logger.info("PrometheusRemoteWriteClient closed");
    }
}