package com.iotaggregator.core;

import com.iotaggregator.core.aggregator.DataAggregator;
import com.iotaggregator.core.model.MetricSnapshot;
import com.iotaggregator.core.model.SensorType;
import com.iotaggregator.core.model.TelemetryPacket;
import com.iotaggregator.core.observer.TelemetryObserver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests verifying the Observer Pattern implementation.
 * Assures dynamic registration, deregistration, and notification delivery work seamlessly.
 */
public class ObserverPatternTest {

    @Test
    public void testObserverRegistrationAndNotification() {
        DataAggregator aggregator = new DataAggregator(1);
        TestTelemetryObserver observer1 = new TestTelemetryObserver();
        TestTelemetryObserver observer2 = new TestTelemetryObserver();

        // 1. Register both observers
        aggregator.registerObserver(observer1);
        aggregator.registerObserver(observer2);

        // Simulate incoming message
        String jsonPayload = "{\"sensorId\":\"temp-01\",\"sensorType\":\"TEMPERATURE\",\"value\":22.5,\"unit\":\"°C\",\"timestamp\":1719662000000}";
        aggregator.onMessage("sensor/temperature/temp-01", jsonPayload);

        // Wait a brief moment for the aggregator worker thread pool to process
        sleep(200);

        // Verify both observers received the packet
        assertEquals(1, observer1.receivedEvents.size(), "Observer 1 should have received 1 notification");
        assertEquals(1, observer2.receivedEvents.size(), "Observer 2 should have received 1 notification");

        TelemetryPacket packet1 = observer1.receivedEvents.get(0).packet;
        MetricSnapshot snapshot1 = observer1.receivedEvents.get(0).snapshot;

        assertEquals("temp-01", packet1.getSensorId());
        assertEquals(22.5, packet1.getValue());
        assertEquals(22.5, snapshot1.getMean());

        // 2. Unregister observer 2
        aggregator.removeObserver(observer2);

        // Send a second packet
        String jsonPayload2 = "{\"sensorId\":\"temp-01\",\"sensorType\":\"TEMPERATURE\",\"value\":24.5,\"unit\":\"°C\",\"timestamp\":1719662001000}";
        aggregator.onMessage("sensor/temperature/temp-01", jsonPayload2);

        sleep(200);

        // Verify observer 1 got the second update, but observer 2 did not
        assertEquals(2, observer1.receivedEvents.size(), "Observer 1 should have received the second notification");
        assertEquals(1, observer2.receivedEvents.size(), "Observer 2 should NOT have received the second notification");

        // Verify aggregates are updated correctly in snapshot
        MetricSnapshot latestSnapshot = observer1.receivedEvents.get(1).snapshot;
        assertEquals(2, latestSnapshot.getCount());
        assertEquals(23.5, latestSnapshot.getMean(), 0.0001, "Mean of 22.5 and 24.5 should be 23.5");

        aggregator.shutdown();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Custom Observer class for testing notifications
    private static class TestTelemetryObserver implements TelemetryObserver {
        final List<EventHolder> receivedEvents = new ArrayList<>();

        @Override
        public void onTelemetryReceived(TelemetryPacket packet, MetricSnapshot snapshot) {
            receivedEvents.add(new EventHolder(packet, snapshot));
        }
    }

    private static class EventHolder {
        final TelemetryPacket packet;
        final MetricSnapshot snapshot;

        EventHolder(TelemetryPacket packet, MetricSnapshot snapshot) {
            this.packet = packet;
            this.snapshot = snapshot;
        }
    }
}
