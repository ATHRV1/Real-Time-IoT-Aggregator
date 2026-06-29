package com.iotaggregator.core.sensor;

import com.iotaggregator.core.broker.MqttBrokerSimulator;
import com.iotaggregator.core.model.SensorType;

/**
 * Factory pattern implementation for creating pre-configured IoT Sensor Simulator instances.
 * Simplifies construction and ensures consistent baselines, intervals, and fluctuation amplitudes
 * for different telemetry categories (Temperature, Humidity, Pressure).
 */
public class SensorFactory {

    /**
     * Creates a SensorSimulator with default realistic baselines and intervals.
     *
     * @param sensorId unique identifier for the sensor (e.g., "temp-lobby-01")
     * @param type     the telemetry type
     * @param broker   reference to the MQTT broker simulator
     * @return a configured SensorSimulator instance
     */
    public static SensorSimulator createSensor(String sensorId, SensorType type, MqttBrokerSimulator broker) {
        switch (type) {
            case TEMPERATURE:
                // Avg temperature of 21°C, fluctuates by up to 5°C, publishes every 1.0 seconds
                return new SensorSimulator(sensorId, type, 21.0, 5.0, 1000, broker);
            case HUMIDITY:
                // Avg humidity of 45%, fluctuates by up to 15%, publishes every 1.5 seconds
                return new SensorSimulator(sensorId, type, 45.0, 15.0, 1500, broker);
            case PRESSURE:
                // Avg pressure of 1013 hPa, fluctuates by up to 10 hPa, publishes every 2.0 seconds
                return new SensorSimulator(sensorId, type, 1013.0, 10.0, 2000, broker);
            default:
                throw new IllegalArgumentException("Unknown sensor type: " + type);
        }
    }

    /**
     * Creates a SensorSimulator with full custom configurations.
     */
    public static SensorSimulator createCustomSensor(String sensorId, SensorType type, double baseline, 
                                                     double amplitude, long intervalMs, MqttBrokerSimulator broker) {
        return new SensorSimulator(sensorId, type, baseline, amplitude, intervalMs, broker);
    }
}
