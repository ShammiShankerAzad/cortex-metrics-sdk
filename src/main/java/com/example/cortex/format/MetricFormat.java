package com.example.cortex.format;

import io.micrometer.core.instrument.Meter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for different metric formats
 */
public interface MetricFormat {
    
    String getFormatName();
    
    boolean isEnabled();
    
    CompletableFuture<Boolean> sendMetrics(List<Meter> meters);
    
    CompletableFuture<Boolean> sendRawMetrics(byte[] data);
    
    byte[] serializeMetrics(List<Meter> meters);
    
    boolean isEndpointAvailable();
    
    Object getConfiguration();
    
    void close();
}