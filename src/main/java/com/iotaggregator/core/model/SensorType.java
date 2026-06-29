package com.iotaggregator.core.model;

/**
 * Defines the types of IoT sensors in our system, along with their unit of measurement,
 * typical operating baseline ranges, and critical alert thresholds.
 */
public enum SensorType {
    TEMPERATURE("°C", 15.0, 35.0, 40.0),  // Normal: 15-35°C, Alert above 40°C
    HUMIDITY("%", 30.0, 70.0, 80.0),       // Normal: 30-70%, Alert above 80% (high moisture risk)
    PRESSURE("hPa", 980.0, 1020.0, 1035.0); // Normal: 980-1020 hPa, Alert above 1035 hPa

    private final String unit;
    private final double minBaseline;
    private final double maxBaseline;
    private final double alertThreshold;

    SensorType(String unit, double minBaseline, double maxBaseline, double alertThreshold) {
        this.unit = unit;
        this.minBaseline = minBaseline;
        this.maxBaseline = maxBaseline;
        this.alertThreshold = alertThreshold;
    }

    public String getUnit() {
        return unit;
    }

    public double getMinBaseline() {
        return minBaseline;
    }

    public double getMaxBaseline() {
        return maxBaseline;
    }

    public double getAlertThreshold() {
        return alertThreshold;
    }
}
