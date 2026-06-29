package com.iotaggregator.cloud.service;

import com.iotaggregator.core.aggregator.DataAggregator;
import com.iotaggregator.core.broker.MqttBrokerSimulator;
import com.iotaggregator.core.model.SensorType;
import com.iotaggregator.core.observer.AlertObserver;
import com.iotaggregator.core.observer.ConsoleDashboardObserver;
import com.iotaggregator.core.observer.TelemetryObserver;
import com.iotaggregator.core.sensor.SensorFactory;
import com.iotaggregator.core.sensor.SensorSimulator;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Collection;
import java.util.concurrent.*;

/**
 * Spring service serving as the integration bridge between the core J2SE telemetry engine
 * and the Spring Boot cloud controllers.
 * Coordinates system startup, dynamic sensor lifecycle controls, and events routing.
 */
@Service
public class CloudAggregatorService {
    private final MqttBrokerSimulator broker = new MqttBrokerSimulator();
    private final DataAggregator aggregator = new DataAggregator(4); // 4 concurrent processing threads

    // Tracks simulated sensors and their execution handles
    private final ConcurrentHashMap<String, SensorSimulator> activeSensors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Future<?>> sensorTaskFutures = new ConcurrentHashMap<>();
    
    // Thread pool dedicated to running sensor simulators
    private final ExecutorService sensorExecutor = Executors.newCachedThreadPool(r -> {
        int count = activeSensors.size() + 1;
        Thread t = new Thread(r, "sensor-sim-" + count);
        t.setDaemon(true);
        return t;
    });

    // Holds observers specifically created by the SSE controller for real-time web streaming
    private final CopyOnWriteArrayList<TelemetryObserver> sseObservers = new CopyOnWriteArrayList<>();

    /**
     * Initializes the aggregation pipeline and registers core J2SE observers.
     */
    @PostConstruct
    public void startEngine() {
        // Connect the MQTT Broker to the Aggregator
        broker.subscribe("sensor/#", aggregator);

        // Register default J2SE observers
        aggregator.registerObserver(new ConsoleDashboardObserver());
        aggregator.registerObserver(new AlertObserver(aggregator));

        // Register a bridge observer to route updates to dynamic Spring SSE clients
        aggregator.registerObserver((packet, snapshot) -> {
            for (TelemetryObserver observer : sseObservers) {
                try {
                    observer.onTelemetryReceived(packet, snapshot);
                } catch (Exception e) {
                    // Failures in SSE connections are handled by the controller removing them
                }
            }
        });

        // Spawn a default suite of sensors on startup to simulate a smart building lobby
        spawnSensor("temp-lobby-01", SensorType.TEMPERATURE);
        spawnSensor("hum-lobby-01", SensorType.HUMIDITY);
        spawnSensor("press-lobby-01", SensorType.PRESSURE);
        
        // Spawn a critical server room temperature monitor (runs at higher frequency)
        spawnCustomSensor("temp-server-room", SensorType.TEMPERATURE, 19.5, 2.5, 750);
    }

    /**
     * Spawns a new sensor using default factory baselines.
     */
    public synchronized void spawnSensor(String sensorId, SensorType type) {
        if (activeSensors.containsKey(sensorId) && activeSensors.get(sensorId).isRunning()) {
            return; // Already running
        }
        SensorSimulator simulator = SensorFactory.createSensor(sensorId, type, broker);
        submitSensorTask(sensorId, simulator);
    }

    /**
     * Spawns a new sensor with custom baselines.
     */
    public synchronized void spawnCustomSensor(String sensorId, SensorType type, double baseline, 
                                               double amplitude, long intervalMs) {
        if (activeSensors.containsKey(sensorId) && activeSensors.get(sensorId).isRunning()) {
            return; // Already running
        }
        SensorSimulator simulator = SensorFactory.createCustomSensor(sensorId, type, baseline, amplitude, intervalMs, broker);
        submitSensorTask(sensorId, simulator);
    }

    private void submitSensorTask(String sensorId, SensorSimulator simulator) {
        activeSensors.put(sensorId, simulator);
        Future<?> future = sensorExecutor.submit(simulator);
        sensorTaskFutures.put(sensorId, future);
    }

    /**
     * Safely terminates a sensor simulation thread.
     */
    public synchronized void terminateSensor(String sensorId) {
        SensorSimulator simulator = activeSensors.remove(sensorId);
        if (simulator != null) {
            simulator.stop(); // sets run flag to false
        }
        Future<?> future = sensorTaskFutures.remove(sensorId);
        if (future != null) {
            future.cancel(true); // interrupts the thread if blocked in Thread.sleep
        }
    }

    // --- Dynamic Sse Observer Registry ---

    public void addSseObserver(TelemetryObserver observer) {
        sseObservers.add(observer);
    }

    public void removeSseObserver(TelemetryObserver observer) {
        sseObservers.remove(observer);
    }

    // --- Core Aggregator Accessors ---

    public DataAggregator getAggregator() {
        return aggregator;
    }

    public Collection<SensorSimulator> getActiveSensors() {
        return activeSensors.values();
    }

    /**
     * Gracefully stops the entire system, shutting down threads in order:
     * Sensors -> Aggregator Queue Consumers -> MQTT Broker Delivery Threads.
     */
    @PreDestroy
    public void stopEngine() {
        System.out.println("[Engine Shutdown] Terminating all sensor threads...");
        for (String id : activeSensors.keySet()) {
            terminateSensor(id);
        }
        sensorExecutor.shutdownNow();
        
        System.out.println("[Engine Shutdown] Stopping aggregator workers...");
        aggregator.shutdown();
        
        System.out.println("[Engine Shutdown] Stopping MQTT Broker...");
        broker.shutdown();
    }
}
