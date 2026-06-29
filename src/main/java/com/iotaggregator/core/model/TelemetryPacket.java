package com.iotaggregator.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An immutable telemetry data packet emitted by IoT Sensors.
 * Immutability is a critical concurrency best-practice in Java: once constructed,
 * this packet can be safely shared across thread boundaries without synchronization.
 */
public final class TelemetryPacket {
    private final String sensorId;
    private final SensorType sensorType;
    private final double value;
    private final String unit;
    private final long timestamp;

    @JsonCreator
    public TelemetryPacket(
            @JsonProperty("sensorId") String sensorId,
            @JsonProperty("sensorType") SensorType sensorType,
            @JsonProperty("value") double value,
            @JsonProperty("unit") String unit,
            @JsonProperty("timestamp") long timestamp) {
        this.sensorId = sensorId;
        this.sensorType = sensorType;
        this.value = value;
        this.unit = unit;
        this.timestamp = timestamp;
    }

    public String getSensorId() {
        return sensorId;
    }

    public SensorType getSensorType() {
        return sensorType;
    }

    public double getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TelemetryPacket that = (TelemetryPacket) o;

        if (Double.compare(that.value, value) != 0) return false;
        if (timestamp != that.timestamp) return false;
        if (!sensorId.equals(that.sensorId)) return false;
        return sensorType == that.sensorType;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = sensorId.hashCode();
        result = 31 * result + sensorType.hashCode();
        temp = Double.doubleToLongBits(value);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return String.format("TelemetryPacket[ID=%s, Type=%s, Value=%.2f %s, Time=%d]",
                sensorId, sensorType.name(), value, unit, timestamp);
    }
}
