package com.iotaggregator.core.broker;

/**
 * Interface representing an MQTT subscriber.
 * Components that want to listen to telemetry topics must implement this interface
 * and register themselves with the MqttBrokerSimulator.
 */
@FunctionalInterface
public interface MqttSubscriber {
    /**
     * Callback triggered when a new message is published to a subscribed topic.
     *
     * @param topic   the message topic (e.g. "sensor/temperature/room1")
     * @param payload the message content (JSON telemetry packet)
     */
    void onMessage(String topic, String payload);
}
