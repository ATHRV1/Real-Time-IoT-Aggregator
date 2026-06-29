package com.iotaggregator.core.aggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iotaggregator.core.broker.MqttSubscriber;
import com.iotaggregator.core.model.MetricSnapshot;
import com.iotaggregator.core.model.TelemetryPacket;
import com.iotaggregator.core.observer.TelemetryObserver;
import com.iotaggregator.core.observer.TelemetrySubject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The heart of the real-time processing engine.
 * Acts as an MQTT Subscriber (ingesting JSON data), buffers it in a BlockingQueue,
 * processes it using a pool of worker threads, calculates aggregates in a thread-safe manner,
 * and notifies all registered observers (Observer Pattern).
 */
public class DataAggregator implements MqttSubscriber, TelemetrySubject {
    private final ConcurrentHashMap<String, RunningMetrics> sensorMetrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> customThresholds = new ConcurrentHashMap<>();
    
    // Bounded queue to absorb pressure peaks and prevent OutOfMemoryErrors
    private final BlockingQueue<TelemetryPacket> ingestionQueue = new LinkedBlockingQueue<>(2000);
    
    // Thread pool for processing ingestion queue packets
    private final ExecutorService processingExecutor;
    
    // Thread-safe observer list
    private final CopyOnWriteArrayList<TelemetryObserver> observers = new CopyOnWriteArrayList<>();
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger threadCounter = new AtomicInteger(1);
    private volatile boolean running = true;

    public DataAggregator(int workerThreadCount) {
        this.processingExecutor = Executors.newFixedThreadPool(workerThreadCount, r -> {
            Thread t = new Thread(r, "aggregator-worker-" + threadCounter.getAndIncrement());
            t.setDaemon(true);
            return t;
        });

        // Start consumer worker threads
        for (int i = 0; i < workerThreadCount; i++) {
            processingExecutor.submit(this::processQueueLoop);
        }
    }

    /**
     * Implements MqttSubscriber. Receives raw JSON payload, parses it,
     * and submits it to the internal queue for processing.
     */
    @Override
    public void onMessage(String topic, String payload) {
        if (!running) return;
        try {
            TelemetryPacket packet = objectMapper.readValue(payload, TelemetryPacket.class);
            boolean accepted = ingestionQueue.offer(packet, 100, TimeUnit.MILLISECONDS);
            if (!accepted) {
                System.err.printf("[Aggregator Warning] Ingestion queue full! Dropped packet: %s%n", packet.getSensorId());
            }
        } catch (Exception e) {
            System.err.printf("[Aggregator Error] Failed to parse message on topic %s: %s%n", topic, e.getMessage());
        }
    }

    /**
     * Ingestion consumer loop. Polls the blocking queue and updates metrics.
     */
    private void processQueueLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // Blocks if queue is empty
                TelemetryPacket packet = ingestionQueue.poll(500, TimeUnit.MILLISECONDS);
                if (packet != null) {
                    processPacket(packet);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Processes a single TelemetryPacket. Thread safety is guaranteed
     * by Using ConcurrentHashMap computeIfAbsent and RunningMetrics locking.
     */
    private void processPacket(TelemetryPacket packet) {
        String sensorId = packet.getSensorId();
        
        // Atomically retrieve or construct RunningMetrics
        RunningMetrics metrics = sensorMetrics.computeIfAbsent(sensorId, id -> 
            new RunningMetrics(id, packet.getSensorType(), packet.getUnit())
        );

        // Thread-safe update under Write Lock
        metrics.update(packet.getValue(), packet.getTimestamp());

        // Thread-safe read under Read Lock
        MetricSnapshot snapshot = metrics.getSnapshot();

        // Notify observers
        notifyObservers(packet, snapshot);
    }

    // --- Dynamic Alarm/Threshold Controls ---

    public void setCustomThreshold(String sensorId, double value) {
        customThresholds.put(sensorId, value);
    }

    public void removeCustomThreshold(String sensorId) {
        customThresholds.remove(sensorId);
    }

    public Double getActiveThreshold(String sensorId, TelemetryPacket packet) {
        return customThresholds.getOrDefault(sensorId, packet.getSensorType().getAlertThreshold());
    }

    public Map<String, Double> getCustomThresholds() {
        return Map.copyOf(customThresholds);
    }

    // --- Observable (TelemetrySubject) Implementation ---

    @Override
    public void registerObserver(TelemetryObserver observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(TelemetryObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers(TelemetryPacket packet, MetricSnapshot snapshot) {
        for (TelemetryObserver observer : observers) {
            try {
                observer.onTelemetryReceived(packet, snapshot);
            } catch (Exception e) {
                System.err.printf("[Observer Notification Error] Observer %s failed: %s%n", 
                        observer.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    // --- Data Access ---

    public List<MetricSnapshot> getAllLatestSnapshots() {
        List<MetricSnapshot> snapshots = new ArrayList<>();
        for (RunningMetrics metrics : sensorMetrics.values()) {
            snapshots.add(metrics.getSnapshot());
        }
        return snapshots;
    }

    public MetricSnapshot getSensorSnapshot(String sensorId) {
        RunningMetrics metrics = sensorMetrics.get(sensorId);
        return metrics != null ? metrics.getSnapshot() : null;
    }

    public void clearMetrics() {
        sensorMetrics.clear();
        ingestionQueue.clear();
    }

    public int getQueueSize() {
        return ingestionQueue.size();
    }

    /**
     * Gracefully shuts down the processing threads.
     */
    public void shutdown() {
        this.running = false;
        processingExecutor.shutdown();
        try {
            if (!processingExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                processingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            processingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
