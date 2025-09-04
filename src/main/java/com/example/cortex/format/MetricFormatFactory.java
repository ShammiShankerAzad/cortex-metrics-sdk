package com.example.cortex.format;

import com.example.cortex.config.CortexConfig;
import com.example.cortex.config.FeatureFlags;
import com.example.cortex.format.prometheus.PrometheusFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating metric formats
 */
public class MetricFormatFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricFormatFactory.class);

    public static List<MetricFormat> createEnabledFormats(CortexConfig config, FeatureFlags featureFlags) {
        List<MetricFormat> formats = new ArrayList<>();
        
        if (featureFlags.isPrometheusEnabled()) {
            try {
                PrometheusFormat prometheusFormat = new PrometheusFormat(config, featureFlags);
                formats.add(prometheusFormat);
                logger.info("Enabled Prometheus Remote Write format");
            } catch (Exception e) {
                logger.error("Failed to initialize Prometheus format", e);
            }
        }
        
        // Future: OpenTelemetry format would be added here
        
        if (formats.isEmpty()) {
            logger.warn("No metric formats enabled! Metrics will not be published.");
        }
        
        return formats;
    }
}