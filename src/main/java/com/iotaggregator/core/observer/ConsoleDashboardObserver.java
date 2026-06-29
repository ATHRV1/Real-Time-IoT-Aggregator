package com.iotaggregator.core.observer;

import com.iotaggregator.core.model.MetricSnapshot;
import com.iotaggregator.core.model.TelemetryPacket;

/**
 * Concrete observer that prints formatted real-time aggregation metrics to the console.
 * Serves as a J2SE terminal UI representation.
 */
public class ConsoleDashboardObserver implements TelemetryObserver {

    @Override
    public void onTelemetryReceived(TelemetryPacket packet, MetricSnapshot snapshot) {
        // Output a clean, single-line telemetry and aggregation summary
        System.out.printf("[Dashboard Observer] ID: %-15s | Cur: %6.2f %s | Avg: %6.2f %s | StdDev: %5.2f | MsgCount: %5d%n",
                packet.getSensorId(),
                packet.getValue(),
                packet.getUnit(),
                snapshot.getMean(),
                snapshot.getUnit(),
                snapshot.getStandardDeviation(),
                snapshot.getCount()
        );
    }
}
