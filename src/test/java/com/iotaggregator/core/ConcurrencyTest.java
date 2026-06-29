package com.iotaggregator.core;

import com.iotaggregator.core.aggregator.DataAggregator;
import com.iotaggregator.core.aggregator.RunningMetrics;
import com.iotaggregator.core.model.MetricSnapshot;
import com.iotaggregator.core.model.SensorType;
import com.iotaggregator.core.model.TelemetryPacket;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency tests verifying that our J2SE data structures, read-write locking,
 * thread pool queue consumer, and observer dispatch remain 100% thread-safe under heavy load.
 */
public class ConcurrencyTest {

    /**
     * Verifies that the RunningMetrics accumulator is thread-safe.
     * Spins up 16 concurrent worker threads to perform a total of 16,000 updates.
     * Uses a startLatch to coordinate threads to execute updates concurrently.
     */
    @Test
    public void testRunningMetricsThreadSafety() throws InterruptedException {
        int threadCount = 16;
        int updatesPerThread = 1000;
        int totalUpdates = threadCount * updatesPerThread;
        
        RunningMetrics metrics = new RunningMetrics("test-sensor", SensorType.TEMPERATURE, "°C");
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        double targetValue = 25.0;

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize start
                    for (int j = 0; j < updatesPerThread; j++) {
                        metrics.update(targetValue, System.currentTimeMillis());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Release all threads at once
        startLatch.countDown();
        
        // Wait for execution to finish (timeout after 5 seconds to prevent hangs)
        boolean finished = finishLatch.await(5, TimeUnit.SECONDS);
        assertTrue(finished, "Threads did not complete execution in time!");
        executor.shutdown();

        // Fetch snapshot under read lock
        MetricSnapshot snapshot = metrics.getSnapshot();

        // Assertions: check that count and aggregates match exactly
        assertEquals(totalUpdates, snapshot.getCount(), "Total update count should match");
        assertEquals(targetValue, snapshot.getMean(), 0.00001, "Mean value should be exactly targetValue");
        assertEquals(targetValue, snapshot.getMin(), 0.00001, "Min value should match target");
        assertEquals(targetValue, snapshot.getMax(), 0.00001, "Max value should match target");
        assertEquals(0.0, snapshot.getStandardDeviation(), 0.00001, "Standard deviation of constant inputs should be zero");
    }

    /**
     * Verifies that the DataAggregator pipeline correctly consumes, aggregates,
     * and notifies observers without dropping packets when fed by multiple concurrent producers.
     */
    @Test
    public void testDataAggregatorPipelineSafety() throws InterruptedException {
        int producerCount = 8;
        int packetsPerProducer = 250;
        int totalPackets = producerCount * packetsPerProducer;

        // Initalize aggregator with 4 worker threads
        DataAggregator aggregator = new DataAggregator(4);
        
        // Count processed packets notified to observer
        AtomicInteger notificationCount = new AtomicInteger(0);
        CountDownLatch observerLatch = new CountDownLatch(totalPackets);

        // Register custom test observer
        aggregator.registerObserver((packet, snapshot) -> {
            notificationCount.incrementAndGet();
            observerLatch.countDown();
        });

        ExecutorService producerExecutor = Executors.newFixedThreadPool(producerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch producerFinishLatch = new CountDownLatch(producerCount);

        // Submit concurrent publishers sending packets to the aggregator
        for (int p = 0; p < producerCount; p++) {
            final int id = p;
            producerExecutor.submit(() -> {
                try {
                    startLatch.await(); // Sync start
                    for (int i = 0; i < packetsPerProducer; i++) {
                        // All threads write to the same sensor to simulate concurrent packet race conditions
                        TelemetryPacket packet = new TelemetryPacket(
                                "shared-sensor",
                                SensorType.TEMPERATURE,
                                20.0 + (i % 10), // variable reading
                                "°C",
                                System.currentTimeMillis()
                        );
                        
                        // Submit to MQTT callback of the aggregator
                        aggregator.onMessage("sensor/temperature/shared-sensor", 
                                String.format("{\"sensorId\":\"%s\",\"sensorType\":\"%s\",\"value\":%.2f,\"unit\":\"%s\",\"timestamp\":%d}",
                                        packet.getSensorId(), packet.getSensorType().name(), packet.getValue(), packet.getUnit(), packet.getTimestamp()));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    producerFinishLatch.countDown();
                }
            });
        }

        // Release producers
        startLatch.countDown();
        
        // Wait for producers to finish publishing
        boolean producersDone = producerFinishLatch.await(5, TimeUnit.SECONDS);
        assertTrue(producersDone, "Producers failed to submit packets in time!");
        producerExecutor.shutdown();

        // Wait for the consumer threads in the aggregator thread pool to process queue and notify observers
        boolean aggregatorDone = observerLatch.await(8, TimeUnit.SECONDS);
        assertTrue(aggregatorDone, "DataAggregator did not process all messages in time! Processed: " + notificationCount.get() + "/" + totalPackets);

        assertEquals(totalPackets, notificationCount.get(), "Observer should have received notifications for every processed packet");
        
        MetricSnapshot snapshot = aggregator.getSensorSnapshot("shared-sensor");
        assertEquals(totalPackets, snapshot.getCount(), "Aggregated metrics count should match total published packets");

        aggregator.shutdown();
    }
}
