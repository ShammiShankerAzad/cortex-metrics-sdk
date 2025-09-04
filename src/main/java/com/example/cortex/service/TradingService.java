package com.example.cortex.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Trading service demonstrating business metrics
 */
public class TradingService {
    
    private static final Logger logger = LoggerFactory.getLogger(TradingService.class);
    
    // Metrics
    private final Counter ordersProcessedCounter;
    private final Counter ordersRejectedCounter;
    private final Timer orderProcessingTimer;
    private final Gauge activeOrdersGauge;
    private final Counter volumeCounter;
    
    // State
    private final AtomicInteger activeOrders = new AtomicInteger(0);
    private final LongAdder totalVolume = new LongAdder();
    private final Random random = new Random();
    private volatile boolean running = true;

    public TradingService(MeterRegistry registry) {
        // Initialize business metrics
        this.ordersProcessedCounter = Counter.builder("trading.orders.processed")
                .description("Total number of trading orders processed successfully")
                .tags("service", "trading",
                      "client", "N/A",
                      "instrument", "N/A",
                      "side", "N/A",
                      "exchange", "N/A")
                .register(registry);

        this.ordersRejectedCounter = Counter.builder("trading.orders.rejected")
                .description("Total number of trading orders rejected")
                .tags("service", "trading",
                      "client", "N/A",
                      "instrument", "N/A",
                      "reason", "N/A",
                      "exchange", "N/A")
                .register(registry);

        this.orderProcessingTimer = Timer.builder("trading.order.processing.duration")
                .description("Time taken to process trading orders")
                .tags("service", "trading")
                .register(registry);

        this.activeOrdersGauge = Gauge.builder("trading.orders.active", activeOrders, AtomicInteger::get)
                .description("Number of currently active orders")
                .tag("service", "trading")
                .register(registry);

        this.volumeCounter = Counter.builder("trading.volume.total")
                .description("Total trading volume processed in USD")
                .tag("service", "trading")
                .baseUnit("USD")
                .register(registry);

        logger.info("TradingService initialized with comprehensive metrics");
    }

    /**
     * Process a trading order with complete metrics tracking
     */
    /**
     * Process a trading order with complete metrics tracking
     * @param clientId The client ID placing the order
     * @param instrument The financial instrument being traded
     * @param quantity The quantity to trade
     * @param price The price per unit
     * @return true if order was processed successfully, false otherwise
     */
    public boolean processOrder(String clientId, String instrument, double quantity, double price) {
        if (clientId == null || instrument == null || quantity <= 0 || price <= 0) {
            logger.warn("Invalid order parameters - clientId: {}, instrument: {}, quantity: {}, price: {}", 
                clientId, instrument, quantity, price);
            return false;
        }

        try {
            return orderProcessingTimer.recordCallable(() -> {
                try {
                    activeOrders.incrementAndGet();
                    
                    // Simulate realistic processing time (10-500ms)
                    try {
                        Thread.sleep(10 + random.nextInt(490));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("Order processing was interrupted", e);
                        return false;
                    }
                    
                    // Simulate 92% success rate (realistic for trading systems)
                    boolean success = random.nextDouble() > 0.08;
                    
                    if (success) {
                    // Record successful order
                    ordersProcessedCounter.increment();
                    // Tags should be specified when creating the counter, not when incrementing
                    // We'll update the counter creation to include these tags
                    
                    // Record volume
                    double volumeUsd = Math.abs(quantity * price);
                    volumeCounter.increment(volumeUsd);
                    totalVolume.add((long) volumeUsd);
                    
                    logger.debug("Processed order: {} {} {} @ ${} for {}", 
                               quantity > 0 ? "BUY" : "SELL", Math.abs(quantity), 
                               instrument, price, clientId);
                } else {
                    // Record rejected order with reason
                    String reason = getRandomRejectionReason();
                    ordersRejectedCounter.increment();
                    // Tags for rejection reasons should be handled through separate counters or dimensions
                    // For now, we'll log the details instead
                    logger.debug("Order rejected - client: {}, instrument: {}, reason: {}", 
                               clientId, instrument, reason);
                    
                    logger.debug("Rejected order for {}: {} ({})", clientId, reason, instrument);
                }
                
                return success;
                
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                ordersRejectedCounter.increment();
                logger.debug("Order processing interrupted - client: {}, instrument: {}", 
                           clientId, instrument);
                return false;
            } finally {
                activeOrders.decrementAndGet();
            }
        });
        } catch (Exception e) {
            logger.error("Error processing order", e);
            return false;
        }
    }

    /**
     * Simulate realistic trading activity
     */
    public void simulateTradingActivity() {
        // Realistic client distribution (institutional + retail)
        String[] institutionalClients = {"goldman_sachs", "jpmorgan", "citadel", "two_sigma", "renaissance"};
        String[] retailClients = {"client_001", "client_002", "client_003", "client_004", "client_005"};
        
        // Popular trading instruments
        String[] instruments = {"AAPL", "GOOGL", "MSFT", "TSLA", "AMZN", "NVDA", "META", "NFLX", "SPY", "QQQ"};
        
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // Determine client type (80% retail, 20% institutional)
                String[] clientPool = random.nextDouble() > 0.2 ? retailClients : institutionalClients;
                String client = clientPool[random.nextInt(clientPool.length)];
                String instrument = instruments[random.nextInt(instruments.length)];
                
                // Institutional orders are typically larger
                boolean isInstitutional = java.util.Arrays.asList(institutionalClients).contains(client);
                double baseQuantity = isInstitutional ? 
                    1000 + random.nextInt(49000) :  // 1K-50K shares
                    10 + random.nextInt(490);       // 10-500 shares
                
                double quantity = baseQuantity * (random.nextBoolean() ? 1 : -1);
                
                // Simulate realistic stock prices with volatility
                double basePrice = getBasePrice(instrument);
                double price = basePrice * (1 + random.nextGaussian() * 0.02); // 2% volatility
                price = Math.max(price, 1.0); // Minimum $1
                
                processOrder(client, instrument, quantity, price);
                
                // Variable order frequency (busier during "market hours")
                int hour = LocalDateTime.now().getHour();
                boolean isMarketHours = hour >= 9 && hour <= 16;
                int maxDelay = isMarketHours ? 200 : 2000; // Faster during market hours
                
                Thread.sleep(50 + random.nextInt(maxDelay));
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in trading simulation", e);
                try {
                    Thread.sleep(1000); // Backoff on error
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        logger.info("Trading simulation stopped");
    }

    private String getRandomExchange() {
        String[] exchanges = {"NYSE", "NASDAQ", "BATS", "IEX", "ARCA"};
        return exchanges[random.nextInt(exchanges.length)];
    }

    private String getRandomRejectionReason() {
        String[] reasons = {
            "insufficient_funds", "invalid_instrument", "market_closed", 
            "price_limit_exceeded", "position_limit_exceeded", "system_error"
        };
        return reasons[random.nextInt(reasons.length)];
    }

    private double getBasePrice(String instrument) {
        // Realistic base prices for demonstration
        return switch (instrument) {
            case "AAPL" -> 175.0;
            case "GOOGL" -> 135.0;
            case "MSFT" -> 415.0;
            case "TSLA" -> 250.0;
            case "AMZN" -> 145.0;
            case "NVDA" -> 480.0;
            case "META" -> 320.0;
            case "NFLX" -> 450.0;
            case "SPY" -> 450.0;
            case "QQQ" -> 380.0;
            default -> 100.0 + random.nextDouble() * 400.0;
        };
    }

    /**
     * Stop the trading simulation
     */
    public void stop() {
        running = false;
        logger.info("Trading service stop requested");
    }

    /**
     * Get current trading statistics
     */
    public TradingStats getStats() {
        return new TradingStats(
            ordersProcessedCounter.count(),
            ordersRejectedCounter.count(),
            activeOrders.get(),
            totalVolume.longValue()
        );
    }

    public record TradingStats(
        double ordersProcessed,
        double ordersRejected,
        int activeOrders,
        long totalVolumeUsd
    ) {
        public double getSuccessRate() {
            double total = ordersProcessed + ordersRejected;
            return total > 0 ? (ordersProcessed / total) * 100.0 : 0.0;
        }
    }
}