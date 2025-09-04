package com.example.cortex;

import com.example.cortex.service.TradingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main application demonstrating the Universal Cortex Metrics SDK
 */
public class CortexMetricsApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(CortexMetricsApplication.class);
    
    private final CortexMetricsSDK sdk;
    private final TradingService tradingService;
    private final ScheduledExecutorService scheduler;

    public CortexMetricsApplication() {
        logger.info("Initializing Cortex Metrics Application...");
        
        this.sdk = CortexMetricsSDK.fromConfiguration();
        this.tradingService = new TradingService(sdk.getRegistry());
        this.scheduler = Executors.newScheduledThreadPool(3);
        
        logger.info("Application initialized successfully");
        logStatus();
    }

    private void logStatus() {
        var status = sdk.getStatus();
        logger.info("=== SDK Status ===");
        logger.info("Metrics Enabled: {}", status.metricsEnabled());
        logger.info("JVM Metrics: {}", status.jvmMetricsEnabled());
        logger.info("Formats: {}", status.enabledFormats());
        logger.info("Total Metrics: {}", status.totalMetrics());
        logger.info("==================");
    }

    public void start() {
        logger.info("Starting application...");

        if (sdk.isMetricsEnabled()) {
            scheduler.submit(() -> {
                logger.info("Starting trading simulation...");
                tradingService.simulateTradingActivity();
            });
        }

        scheduler.scheduleAtFixedRate(this::reportStatus, 30, 30, TimeUnit.SECONDS);
        logger.info("Application started successfully");
    }

    private void reportStatus() {
        var tradingStats = tradingService.getStats();
        var status = sdk.getStatus();
        
        logger.info("Trading Stats - Processed: {}, Active: {}, Volume: ${}M", 
                   (long) tradingStats.ordersProcessed(),
                   tradingStats.activeOrders(),
                   tradingStats.totalVolumeUsd() / 1_000_000);
        
        logger.info("Metrics - Enabled: {}, JVM: {}, Total: {}", 
                   status.metricsEnabled(),
                   status.jvmMetricsEnabled(),
                   status.totalMetrics());
    }

    public void shutdown() {
        logger.info("Shutting down application...");
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        
        sdk.shutdown();
        logger.info("Application shutdown completed");
    }

    public static void main(String[] args) {
        logger.info("=== Universal Cortex Metrics SDK Demo ===");
        
        try {
            CortexMetricsApplication app = new CortexMetricsApplication();
            
            Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
            app.start();
            
            Thread.currentThread().join();
            
        } catch (Exception e) {
            logger.error("Application failed to start", e);
            System.exit(1);
        }
    }
}