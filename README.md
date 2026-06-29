# Real-time IoT Sensor Data Aggregator

A high-performance, multi-threaded Java application that simulates concurrent telemetry stream ingestion from multiple IoT sensors, aggregates statistical metrics (average, min/max, standard deviation) in a thread-safe manner, and updates a live web dashboard in real-time.

Designed with **Core Java (J2SE) Concurrency** principles and clean architectural patterns, demonstrating key concepts required for industrial automation networks (like Tridium Niagara) and cloud telemetry platforms (like Honeywell Connected Enterprise/Forge).

---

## 🚀 Key Architectural Highlights

### 1. Concurrency & Concurrency Control
- **Bounded Ingestion Buffer (`LinkedBlockingQueue`):** Prevents `OutOfMemoryError` spikes by decoupling sensor thread ingestion from processor threads.
- **Worker Thread Pool (`ExecutorService`):** Dedicated worker threads poll the queue to execute statistical computations asynchronously.
- **Lock Splitting (`ReentrantReadWriteLock`):** Protects sensor aggregates. Multiple concurrent readers (dashboard, logs) can query snapshots without blocking each other, while a single writer thread blocks other threads to update statistics under a Write Lock.
- **Volatile Control Flow:** Simulators run on dedicated threads using a `volatile boolean` running flag for cooperative, clean thread shutdown without CPU spin locks.

### 2. Numerical Stability
- **Welford’s Algorithm for Online Variance:** Computes standard deviation incrementally in $O(1)$ space. Avoids floating-point precision loss and rounding errors common in standard sum-of-squares formulations.

### 3. Design Patterns
- **Observer Pattern:** Decouples the core processing engine from downstream consumers. Observers (Console Logger, Threshold Alarm, and SSE Web Dispatcher) subscribe to telemetry updates dynamically.
- **Factory Pattern:** Spawns temperature, humidity, and air pressure sensors with distinct physical properties (baselines, cycles, frequencies).
- **Immutability:** Telemetry data packets and statistic snapshots are immutable value objects, making them inherently thread-safe across thread boundaries.

### 4. Enterprise Spring Boot & Web Integration
- **Server-Sent Events (SSE):** Serves persistent push streams to client browsers for zero-latency dashboard updates.
- **Event Loop Isolation:** SSE broadcasting is offloaded to a dedicated, single-threaded executor, preventing network latency or slow client browsers from bottlenecking the J2SE core aggregator thread pool.

---

## 🛠️ Technology Stack
- **Backend:** Java 17 (J2SE)
- **Framework:** Spring Boot 3.3.1 (REST Controllers, Static Resource Serving)
- **Message Broker Simulation:** In-memory Publish-Subscribe Channel (MQTT Topic-like wildcards)
- **JSON Serialization:** Jackson Databind
- **Testing:** JUnit 5 (Concurrency Stress Tests & Mathematical Verification)
- **Deployment:** Docker (Multi-stage build)

---

## 📂 Project Structure

```
.
├── pom.xml                         # Maven dependencies & build configurations
├── Dockerfile                      # Multi-stage production container configuration
└── src/
    ├── main/
    │   ├── java/com/iotaggregator/
    │   │   ├── App.java            # Spring Boot entry bootstrap class
    │   │   ├── core/               # J2SE Core Telemetry Engine
    │   │   │   ├── model/          # Immutable TelemetryPacket, MetricSnapshot
    │   │   │   ├── sensor/         # SensorSimulator (Runnable), SensorFactory
    │   │   │   ├── broker/         # Asynchronous Pub-Sub MQTT Broker Simulator
    │   │   │   ├── aggregator/     # DataAggregator, RunningMetrics (Lock-splitting)
    │   │   │   └── observer/       # TelemetryObserver & TelemetrySubject interfaces
    │   │   └── cloud/              # Spring Boot Cloud Integration
    │   │       ├── controller/     # REST Endpoints & SSE Streaming Controller
    │   │       └── service/        # Lifecycle Manager & Dependency Bridge
    │   └── resources/
    │       ├── application.properties # Server port & log configurations
    │       └── static/             # Real-time Web Dashboard (HTML, CSS, JS)
    └── test/
        └── java/com/iotaggregator/core/
            ├── ConcurrencyTest.java # Stress tests using CountDownLatch
            ├── AggregatorTest.java  # Welford algorithm verification
            └── ObserverPatternTest.java # Observer registration validation
```

---

## ⚙️ How to Build and Run Locally

### Prerequisites
- **Java 17 JDK** or above installed.
- **Maven 3.8+** installed.

### 1. Build the Application
Compile the code and package it into a executable production JAR:
```bash
mvn clean package
```

### 2. Run the JUnit Tests
Execute the thread-safety stress tests and mathematical verifications:
```bash
mvn test
```

### 3. Run the Web Server
Launch the Spring Boot application:
```bash
mvn spring-boot:run
```
Once started, open your web browser and navigate to:  
👉 **[http://localhost:8080](http://localhost:8080)**

---

## 📊 REST API Reference

| Endpoint | Method | Description |
|---|---|---|
| `/api/sensors` | `GET` | List all running simulator threads and parameters |
| `/api/sensors` | `POST` | Spawn a new sensor thread dynamically |
| `/api/sensors/{id}` | `DELETE` | Gracefully terminate a running sensor thread |
| `/api/metrics` | `GET` | Get aggregated metrics (mean, min, max, stddev) |
| `/api/metrics/{id}` | `GET` | Get statistical aggregates for a specific sensor |
| `/api/thresholds` | `GET` | List all active custom alarm thresholds |
| `/api/thresholds/{id}?value=X` | `POST` | Update/set custom alert threshold on-the-fly |
| `/api/thresholds/{id}` | `DELETE` | Remove custom threshold, reverting to sensor type default |
| `/api/live-stream` | `GET` | Establishes Server-Sent Events (SSE) telemetry broadcast stream |

---

## 🐳 Cloud Deployment (Render / Railway)

This project includes a multi-stage `Dockerfile` making it 100% ready for deployment on platforms like **Render** or **Railway**:

1. Create a new repository on GitHub and push the codebase.
2. Log in to [Render](https://render.com) and click **New > Web Service**.
3. Link your GitHub repository.
4. Select **Docker** as the runtime environment.
5. Deploy. Render will automatically read the `Dockerfile`, build the Java application, and bind it to the dynamic `$PORT` assigned in the environment variables.
