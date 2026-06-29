package com.iotaggregator.cloud.controller;

import com.iotaggregator.cloud.service.CloudAggregatorService;
import com.iotaggregator.core.model.MetricSnapshot;
import com.iotaggregator.core.model.TelemetryPacket;
import com.iotaggregator.core.observer.TelemetryObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller exposing a Server-Sent Events (SSE) streaming API (/api/live-stream).
 * Allows the web dashboard to subscribe to a real-time feed of telemetry updates and alarms.
 *
 * Implements architectural isolation: uses a dedicated sseExecutor to broadcast messages,
 * ensuring that network slowdowns on client browsers do not block the core data aggregator's thread pool.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SseController {

    private final CloudAggregatorService service;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    
    // Dedicated single-thread executor to broadcast events asynchronously, preventing aggregator workers from blocking
    private final ExecutorService sseExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sse-broadcast-worker");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    public SseController(CloudAggregatorService service) {
        this.service = service;
    }

    /**
     * Initializes the SSE bridge observer on startup.
     */
    @PostConstruct
    public void registerAsBridge() {
        TelemetryObserver observer = this::broadcastEvent;
        service.addSseObserver(observer);
    }

    /**
     * Establishes a persistent SSE stream with the client browser.
     */
    @GetMapping(value = "/live-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTelemetry() {
        // Create emitter with 10-minute timeout (600,000 ms)
        SseEmitter emitter = new SseEmitter(600_000L);
        
        emitters.add(emitter);

        // Clean up connections on timeout, completion, or error
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((ex) -> emitters.remove(emitter));

        // Send an initial handshake event to confirm the channel is open
        try {
            emitter.send(SseEmitter.event()
                    .name("handshake")
                    .data("Connected to Real-time IoT Aggregator Stream"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * Broadcasts the processed telemetry and running metrics snapshot to all connected clients.
     * Executed asynchronously in the sseExecutor context.
     */
    private void broadcastEvent(TelemetryPacket packet, MetricSnapshot sensorSnapshot, MetricSnapshot typeSnapshot) {
        if (emitters.isEmpty()) return;

        // Submit the broadcast task to the dedicated sse executor
        sseExecutor.submit(() -> {
            double value = packet.getValue();
            double threshold = service.getAggregator().getActiveThreshold(packet.getSensorId(), packet);
            boolean isAlert = value > threshold;
            String alertMessage = isAlert ? 
                    String.format("Critical value of %.2f %s breached threshold (%.2f %s)", value, packet.getUnit(), threshold, packet.getUnit()) 
                    : null;

            TelemetryEvent event = new TelemetryEvent(packet, sensorSnapshot, typeSnapshot, isAlert, alertMessage);

            ListToRemove listToRemove = new ListToRemove();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("telemetry")
                            .data(event, MediaType.APPLICATION_JSON));
                } catch (Exception e) {
                    listToRemove.add(emitter);
                }
            }
            emitters.removeAll(listToRemove.list);
        });
    }

    @PreDestroy
    public void shutdown() {
        sseExecutor.shutdownNow();
    }

    // Helper list holder for bulk removal of dead connections
    private static class ListToRemove {
        final java.util.List<SseEmitter> list = new java.util.ArrayList<>();
        void add(SseEmitter e) { list.add(e); }
    }

    // --- SSE Event Representation DTO ---

    public static class TelemetryEvent {
        private final TelemetryPacket packet;
        private final MetricSnapshot sensorSnapshot;
        private final MetricSnapshot typeSnapshot;
        private final boolean isAlert;
        private final String alertMessage;

        public TelemetryEvent(TelemetryPacket packet, MetricSnapshot sensorSnapshot, MetricSnapshot typeSnapshot, boolean isAlert, String alertMessage) {
            this.packet = packet;
            this.sensorSnapshot = sensorSnapshot;
            this.typeSnapshot = typeSnapshot;
            this.isAlert = isAlert;
            this.alertMessage = alertMessage;
        }

        public TelemetryPacket getPacket() {
            return packet;
        }

        public MetricSnapshot getSensorSnapshot() {
            return sensorSnapshot;
        }

        public MetricSnapshot getTypeSnapshot() {
            return typeSnapshot;
        }

        public boolean getIsAlert() {
            return isAlert;
        }

        public String getAlertMessage() {
            return alertMessage;
        }
    }
}
