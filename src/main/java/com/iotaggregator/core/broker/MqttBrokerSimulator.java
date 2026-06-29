package com.iotaggregator.core.broker;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An in-memory, thread-safe MQTT Broker simulator.
 * Enables loose coupling between sensors (publishers) and the data aggregator (subscriber).
 * Delivers messages asynchronously via a thread pool to simulate real-world networking
 * and prevent slow subscribers from blocking sensor telemetry generation.
 */
public class MqttBrokerSimulator {
    // Topic subscriptions map. Thread-safe read/write concurrency.
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<MqttSubscriber>> subscriptions = new ConcurrentHashMap<>();
    
    // Dedicated thread pool for asynchronous message delivery
    private final ExecutorService deliveryExecutor;
    private final AtomicInteger threadCounter = new AtomicInteger(1);

    public MqttBrokerSimulator() {
        // Limit delivery pool to simulate network congestion/concurrency throttling
        this.deliveryExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "mqtt-delivery-worker-" + threadCounter.getAndIncrement());
            t.setDaemon(true); // Daemon threads allow JVM shutdown without block
            return t;
        });
    }

    /**
     * Registers a subscriber to a topic pattern (supports exact match or '#' wildcard).
     */
    public void subscribe(String topicPattern, MqttSubscriber subscriber) {
        subscriptions.computeIfAbsent(topicPattern, k -> new CopyOnWriteArrayList<>()).add(subscriber);
    }

    /**
     * Unregisters a subscriber from a topic pattern.
     */
    public void unsubscribe(String topicPattern, MqttSubscriber subscriber) {
        CopyOnWriteArrayList<MqttSubscriber> subs = subscriptions.get(topicPattern);
        if (subs != null) {
            subs.remove(subscriber);
        }
    }

    /**
     * Publishes a telemetry payload (JSON) to a topic.
     * The publish operation returns immediately; the actual delivery is executed asynchronously.
     */
    public void publish(String topic, String payload) {
        deliveryExecutor.submit(() -> {
            for (Map.Entry<String, CopyOnWriteArrayList<MqttSubscriber>> entry : subscriptions.entrySet()) {
                String pattern = entry.getKey();
                if (matches(pattern, topic)) {
                    for (MqttSubscriber subscriber : entry.getValue()) {
                        try {
                            subscriber.onMessage(topic, payload);
                        } catch (Exception e) {
                            System.err.printf("[MQTT Broker Error] Failed to deliver payload to subscriber on topic %s: %s%n", 
                                    topic, e.getMessage());
                        }
                    }
                }
            }
        });
    }

    /**
     * Evaluates standard MQTT-like topic matching.
     * Supports:
     * - Exact matches: "sensor/temperature/room1" matches "sensor/temperature/room1"
     * - Global wildcard: "#" matches any topic
     * - Multi-level wildcard: "sensor/temperature/#" matches "sensor/temperature/room1", "sensor/temperature/room2/sub"
     */
    private boolean matches(String pattern, String topic) {
        if (pattern.equals(topic) || pattern.equals("#")) {
            return true;
        }
        if (pattern.endsWith("/#")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return topic.startsWith(prefix + "/") || topic.equals(prefix);
        }
        return false;
    }

    /**
     * Gracefully shuts down the message delivery thread pool.
     */
    public void shutdown() {
        deliveryExecutor.shutdown();
        try {
            if (!deliveryExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                deliveryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            deliveryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
