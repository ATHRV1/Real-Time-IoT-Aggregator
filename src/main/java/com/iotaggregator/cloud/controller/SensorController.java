package com.iotaggregator.cloud.controller;

import com.iotaggregator.cloud.service.CloudAggregatorService;
import com.iotaggregator.core.model.MetricSnapshot;
import com.iotaggregator.core.model.SensorType;
import com.iotaggregator.core.sensor.SensorSimulator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller exposing APIs to query telemetry aggregates and control sensor configurations.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow frontend connections
public class SensorController {

    private final CloudAggregatorService service;

    @Autowired
    public SensorController(CloudAggregatorService service) {
        this.service = service;
    }

    // --- Sensor Thread Controls ---

    @GetMapping("/sensors")
    public ResponseEntity<Collection<Map<String, Object>>> getRunningSensors() {
        Collection<Map<String, Object>> sensorsList = service.getActiveSensors().stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("sensorId", s.getSensorId());
            map.put("sensorType", s.getSensorType());
            map.put("baseline", s.getBaseline());
            map.put("fluctuationAmplitude", s.getFluctuationAmplitude());
            map.put("intervalMs", s.getIntervalMs());
            map.put("running", s.isRunning());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(sensorsList);
    }

    @PostMapping("/sensors")
    public ResponseEntity<String> spawnSensor(@RequestBody SensorSpawnRequest request) {
        if (request.getSensorId() == null || request.getSensorId().isBlank()) {
            return ResponseEntity.badRequest().body("Sensor ID is required");
        }
        if (request.getSensorType() == null) {
            return ResponseEntity.badRequest().body("Sensor Type is required (TEMPERATURE, HUMIDITY, PRESSURE)");
        }

        if (request.isCustom()) {
            service.spawnCustomSensor(
                    request.getSensorId(),
                    request.getSensorType(),
                    request.getBaseline(),
                    request.getFluctuationAmplitude(),
                    request.getIntervalMs()
            );
        } else {
            service.spawnSensor(request.getSensorId(), request.getSensorType());
        }

        return ResponseEntity.ok("Sensor " + request.getSensorId() + " spawned successfully");
    }

    @DeleteMapping("/sensors/{id}")
    public ResponseEntity<String> terminateSensor(@PathVariable("id") String sensorId) {
        service.terminateSensor(sensorId);
        return ResponseEntity.ok("Sensor " + sensorId + " terminated successfully");
    }

    // --- Aggregated Metrics Queries ---

    @GetMapping("/metrics")
    public ResponseEntity<Collection<MetricSnapshot>> getLatestMetrics() {
        return ResponseEntity.ok(service.getAggregator().getAllLatestSnapshots());
    }

    @GetMapping("/metrics/{id}")
    public ResponseEntity<MetricSnapshot> getSensorMetrics(@PathVariable("id") String sensorId) {
        MetricSnapshot snapshot = service.getAggregator().getSensorSnapshot(sensorId);
        if (snapshot == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(snapshot);
    }

    // --- Alarm & Threshold Controls ---

    @GetMapping("/thresholds")
    public ResponseEntity<Map<String, Double>> getThresholds() {
        return ResponseEntity.ok(service.getAggregator().getCustomThresholds());
    }

    @PostMapping("/thresholds/{id}")
    public ResponseEntity<String> setCustomThreshold(@PathVariable("id") String sensorId, @RequestParam("value") double value) {
        service.getAggregator().setCustomThreshold(sensorId, value);
        return ResponseEntity.ok("Custom threshold of " + value + " set for sensor " + sensorId);
    }

    @DeleteMapping("/thresholds/{id}")
    public ResponseEntity<String> removeCustomThreshold(@PathVariable("id") String sensorId) {
        service.getAggregator().removeCustomThreshold(sensorId);
        return ResponseEntity.ok("Custom threshold removed for sensor " + sensorId);
    }

    // --- DTO for Spawning Sensors ---

    public static class SensorSpawnRequest {
        private String sensorId;
        private SensorType sensorType;
        private double baseline;
        private double fluctuationAmplitude;
        private long intervalMs;
        private boolean custom = false;

        public String getSensorId() { return sensorId; }
        public void setSensorId(String sensorId) { this.sensorId = sensorId; }

        public SensorType getSensorType() { return sensorType; }
        public void setSensorType(SensorType sensorType) { this.sensorType = sensorType; }

        public double getBaseline() { return baseline; }
        public void setBaseline(double baseline) { this.baseline = baseline; this.custom = true; }

        public double getFluctuationAmplitude() { return fluctuationAmplitude; }
        public void setFluctuationAmplitude(double fluctuationAmplitude) { this.fluctuationAmplitude = fluctuationAmplitude; this.custom = true; }

        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; this.custom = true; }

        public boolean isCustom() { return custom; }
    }
}
