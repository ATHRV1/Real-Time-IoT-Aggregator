package com.iotaggregator.core.sensor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iotaggregator.core.broker.MqttBrokerSimulator;
import com.iotaggregator.core.model.SensorType;
import com.iotaggregator.core.model.TelemetryPacket;

import java.util.Random;

/**
 * Simulates a hardware IoT sensor emitting telemetry at configured intervals.
 * Implements Runnable to be executed within its own thread context.
 * Uses a volatile boolean flag to ensure safe, cooperative thread termination.
 */
public class SensorSimulator implements Runnable {
    private final String sensorId;
    private final SensorType sensorType;
    private final double baseline;
    private final double fluctuationAmplitude;
    private final long intervalMs;
    private final MqttBrokerSimulator broker;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    private volatile boolean running = true;
    private long tick = 0;

    public SensorSimulator(String sensorId, SensorType sensorType, double baseline, 
                           double fluctuationAmplitude, long intervalMs, MqttBrokerSimulator broker) {
        this.sensorId = sensorId;
        this.sensorType = sensorType;
        this.baseline = baseline;
        this.fluctuationAmplitude = fluctuationAmplitude;
        this.intervalMs = intervalMs;
        this.broker = broker;
    }

    @Override
    public void run() {
        System.out.printf("[Sensor Starting] Sensor %s (Type: %s) is now active.%n", sensorId, sensorType);
        
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                double value = generateReading();
                TelemetryPacket packet = new TelemetryPacket(
                        sensorId,
                        sensorType,
                        value,
                        sensorType.getUnit(),
                        System.currentTimeMillis()
                );

                String payload = objectMapper.writeValueAsString(packet);
                String topic = "sensor/" + sensorType.name().toLowerCase() + "/" + sensorId;
                
                // Publish to mock broker
                broker.publish(topic, payload);

                // Wait for the next telemetry cycle
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                // Restore interrupted status and break the loop
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.printf("[Sensor Error] Sensor %s encountered an error: %s%n", sensorId, e.getMessage());
            }
        }
        System.out.printf("[Sensor Stopped] Sensor %s has shutdown.%n", sensorId);
    }

    /**
     * Generates a realistic telemetry reading using a baseline value,
     * a sine-wave oscillation (simulating diurnal cycles), and Gaussian noise.
     */
    private double generateReading() {
        tick++;
        double cyclicVariation = Math.sin(tick * 0.1) * fluctuationAmplitude;
        double randomNoise = random.nextGaussian() * (fluctuationAmplitude * 0.15);
        double result = baseline + cyclicVariation + randomNoise;

        // Clean values to 2 decimal places
        return Math.round(result * 100.0) / 100.0;
    }

    /**
     * Safely triggers simulator shutdown by toggling the volatile loop control flag.
     */
    public void stop() {
        this.running = false;
    }

    // --- Getters for configuration and state ---

    public String getSensorId() {
        return sensorId;
    }

    public SensorType getSensorType() {
        return sensorType;
    }

    public double getBaseline() {
        return baseline;
    }

    public double getFluctuationAmplitude() {
        return fluctuationAmplitude;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public boolean isRunning() {
        return running;
    }
}
