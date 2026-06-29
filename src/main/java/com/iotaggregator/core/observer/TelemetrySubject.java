package com.iotaggregator.core.observer;

import com.iotaggregator.core.model.MetricSnapshot;
import com.iotaggregator.core.model.TelemetryPacket;

/**
 * Subject interface in our custom Observer Pattern implementation.
 * Allows dynamic registration, removal, and notification of TelemetryObservers.
 */
public interface TelemetrySubject {
    /**
     * Registers a new observer.
     */
    void registerObserver(TelemetryObserver observer);

    /**
     * Unregisters an existing observer.
     */
    void removeObserver(TelemetryObserver observer);

    /**
     * Notifies all registered observers of a new processed telemetry event.
     */
    void notifyObservers(TelemetryPacket packet, MetricSnapshot snapshot);
}
