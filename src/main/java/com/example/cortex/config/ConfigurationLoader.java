package com.example.cortex.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration loader supporting Properties and YAML formats
 */
public class ConfigurationLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationLoader.class);
    
    private final Map<String, String> configuration = new HashMap<>();
    
    public ConfigurationLoader() {
        loadConfiguration();
    }
    
    private void loadConfiguration() {
        loadDefaultConfiguration();
        loadPropertiesFile();
        loadYamlFile();
        loadSystemProperties();
        loadEnvironmentVariables();
        
        logger.info("Configuration loaded with {} properties", configuration.size());
    }
    
    private void loadDefaultConfiguration() {
        configuration.put("cortex.endpoint", "http://localhost:9009/api/v1/push");
        configuration.put("cortex.tenant-id", "default-tenant");
        configuration.put("cortex.step", "PT30S");
        configuration.put("cortex.batch-size", "10000");
        configuration.put("cortex.connect-timeout", "PT10S");
        configuration.put("cortex.read-timeout", "PT30S");
        configuration.put("cortex.compression.enabled", "true");
        configuration.put("cortex.retry.max-attempts", "3");
        configuration.put("cortex.retry.initial-delay", "PT1S");
        configuration.put("cortex.retry.max-delay", "PT30S");
        configuration.put("cortex.buffer.enabled", "true");
        configuration.put("cortex.buffer.directory", "./metrics-buffer");
        configuration.put("cortex.buffer.max-file-size", "10485760");
        configuration.put("cortex.health-check.interval", "PT60S");
        configuration.put("app.name", "cortex-sdk-app");
        configuration.put("app.version", "3.0.0");
        configuration.put("app.environment", "development");
    }
    
    private void loadPropertiesFile() {
        try (InputStream is = getClass().getResourceAsStream("/application.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                props.forEach((key, value) -> configuration.put(key.toString(), value.toString()));
                logger.debug("Loaded properties from application.properties");
            }
        } catch (IOException e) {
            logger.debug("Could not load application.properties: {}", e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadYamlFile() {
        try (InputStream is = getClass().getResourceAsStream("/application.yml")) {
            if (is != null) {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                Map<String, Object> yamlMap = mapper.readValue(is, Map.class);
                flattenMap("", yamlMap, configuration);
                logger.debug("Loaded properties from application.yml");
            }
        } catch (IOException e) {
            logger.debug("Could not load application.yml: {}", e.getMessage());
        }
    }
    
    private void flattenMap(String prefix, Map<String, Object> map, Map<String, String> target) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                flattenMap(key, nestedMap, target);
            } else {
                target.put(key, value.toString());
            }
        }
    }
    
    private void loadSystemProperties() {
        System.getProperties().forEach((key, value) -> 
            configuration.put(key.toString(), value.toString()));
    }
    
    private void loadEnvironmentVariables() {
        System.getenv().forEach((key, value) -> {
            String propKey = key.toLowerCase().replace("_", ".");
            configuration.put(propKey, value);
        });
    }
    
    public String get(String key) {
        return configuration.get(key);
    }
    
    public String get(String key, String defaultValue) {
        return configuration.getOrDefault(key, defaultValue);
    }
    
    public Map<String, String> getAllProperties() {
        return new HashMap<>(configuration);
    }
}