package com.iotaggregator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application entry point for the Real-time IoT Sensor Data Aggregator.
 * Boots the Spring Boot application context and runs the embedded web container (Tomcat) on port 8080.
 */
@SpringBootApplication
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
        System.out.println("====================================================================");
        System.out.println("    REAL-TIME IOT SENSOR DATA AGGREGATOR STARTED SUCCESSFULLY       ");
        System.out.println("                                                                    ");
        System.out.println("    Local Web Dashboard URL: http://localhost:8080                 ");
        System.out.println("    REST API Base Endpoint:  http://localhost:8080/api              ");
        System.out.println("    SSE Telemetry Stream:    http://localhost:8080/api/live-stream  ");
        System.out.println("====================================================================");
    }
}
