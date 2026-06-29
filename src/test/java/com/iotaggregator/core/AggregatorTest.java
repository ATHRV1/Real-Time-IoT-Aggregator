package com.iotaggregator.core;

import com.iotaggregator.core.aggregator.RunningMetrics;
import com.iotaggregator.core.model.MetricSnapshot;
import com.iotaggregator.core.model.SensorType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests verifying the mathematical accuracy of our running aggregate statistics calculations,
 * specifically Welford's algorithm for online standard deviation/variance.
 */
public class AggregatorTest {

    /**
     * Feeds a static series of double values to RunningMetrics and validates
     * all statistical properties (count, mean, min, max, stddev) against pre-calculated constants.
     */
    @Test
    public void testWelfordAlgorithmAccuracy() {
        RunningMetrics metrics = new RunningMetrics("temp-sensor-01", SensorType.TEMPERATURE, "°C");

        // Static dataset: [10, 12, 23, 23, 16, 23, 21, 16]
        // Count = 8
        // Sum = 144
        // Mean = 144 / 8 = 18.00
        // Min = 10.00
        // Max = 23.00
        // Expected Sample Variance = Sum of (x_i - mean)^2 / (N - 1)
        // Differences from mean: [-8, -6, 5, 5, -2, 5, 3, -2]
        // Squared: [64, 36, 25, 25, 4, 25, 9, 4] -> Sum of squares = 192
        // Sample Variance = 192 / 7 = 27.42857
        // Sample Standard Deviation = sqrt(27.42857) = 5.237227
        
        double[] dataset = {10.0, 12.0, 23.0, 23.0, 16.0, 23.0, 21.0, 16.0};

        for (double val : dataset) {
            metrics.update(val, System.currentTimeMillis());
        }

        MetricSnapshot snapshot = metrics.getSnapshot();

        assertEquals(8, snapshot.getCount(), "Count should match exactly");
        assertEquals(18.0, snapshot.getMean(), 0.0001, "Mean should be exactly 18.0");
        assertEquals(10.0, snapshot.getMin(), 0.0001, "Min value should be exactly 10.0");
        assertEquals(23.0, snapshot.getMax(), 0.0001, "Max value should be exactly 23.0");
        assertEquals(5.237227, snapshot.getStandardDeviation(), 0.0001, "Standard deviation should match sample calculation");
    }

    /**
     * Validates that RunningMetrics behaves correctly when initialized
     * and when receiving a single data point.
     */
    @Test
    public void testRunningMetricsEdgeCases() {
        RunningMetrics metrics = new RunningMetrics("empty-sensor", SensorType.TEMPERATURE, "°C");

        // Edge Case 1: No telemetry received
        MetricSnapshot emptySnapshot = metrics.getSnapshot();
        assertEquals(0, emptySnapshot.getCount());
        assertEquals(0.0, emptySnapshot.getMean());
        assertEquals(0.0, emptySnapshot.getMin());
        assertEquals(0.0, emptySnapshot.getMax());
        assertEquals(0.0, emptySnapshot.getStandardDeviation());

        // Edge Case 2: Exactly 1 reading
        metrics.update(22.5, System.currentTimeMillis());
        MetricSnapshot singleSnapshot = metrics.getSnapshot();
        assertEquals(1, singleSnapshot.getCount());
        assertEquals(22.5, singleSnapshot.getMean());
        assertEquals(22.5, singleSnapshot.getMin());
        assertEquals(22.5, singleSnapshot.getMax());
        // Sample standard deviation (divided by N-1) for N=1 is mathematically undefined, 
        // our code falls back to 0.0 to prevent NaNs.
        assertEquals(0.0, singleSnapshot.getStandardDeviation());
    }
}
