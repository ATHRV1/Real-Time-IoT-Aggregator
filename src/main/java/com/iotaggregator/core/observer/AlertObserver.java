package com.iotaggregator.core.observer;

import com.iotaggregator.core.aggregator.DataAggregator;
import com.iotaggregator.core.model.MetricSnapshot;
import com.iotaggregator.core.model.TelemetryPacket;

/**
 * Concrete observer responsible for identifying and raising alarms when a sensor value
 * breaches its operating threshold.
 * Demonstrates decoupling: the alarm triggering logic is isolated from the main aggregation pipeline.
 */
public class AlertObserver implements TelemetryObserver {
    private final DataAggregator aggregator;

    public AlertObserver(DataAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @Override
    public void onTelemetryReceived(TelemetryPacket packet, MetricSnapshot snapshot) {
        double value = packet.getValue();
        double threshold = aggregator.getActiveThreshold(packet.getSensorId(), packet);

        // Check if value exceeds safety threshold
        if (value > threshold) {
            System.err.printf("[CRITICAL ALERT] Sensor ID: %s (%s) exceeded threshold! Current: %.2f %s (Threshold: %.2f %s)%n",
                    packet.getSensorId(),
                    packet.getSensorType().name(),
                    value,
                    packet.getUnit(),
                    threshold,
                    packet.getUnit()
            );
        }
    }
}
