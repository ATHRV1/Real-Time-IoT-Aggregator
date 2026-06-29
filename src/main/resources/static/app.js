// Global State
let sseSource = null;
let activeSensors = [];
let customThresholds = {};
let activeAlertsCount = 0;

// DOM Elements
const connectionStatus = document.getElementById('connection-status');
const activeSensorsList = document.getElementById('active-sensors-list');
const spawnSensorForm = document.getElementById('spawn-sensor-form');
const alertsFeed = document.getElementById('alerts-feed');
const telemetryLog = document.getElementById('telemetry-log');
const queueIndicator = document.getElementById('queue-indicator');
const clearAlertsBtn = document.getElementById('clear-alerts');

// Modal Elements
const thresholdModal = document.getElementById('threshold-modal');
const modalSensorId = document.getElementById('modal-sensor-id');
const modalThresholdVal = document.getElementById('modal-threshold-val');
const modalCancel = document.getElementById('modal-cancel');
const modalSave = document.getElementById('modal-save');
let targetSensorId = null;

// Initialize Dashboard
document.addEventListener('DOMContentLoaded', () => {
    fetchActiveSensors();
    fetchCustomThresholds();
    initSseConnection();
    
    // Form submission
    spawnSensorForm.addEventListener('submit', handleSpawnSensor);
    
    // Clear alerts
    clearAlertsBtn.addEventListener('click', () => {
        alertsFeed.innerHTML = '<div class="empty-log">System running normal. No alarms triggered.</div>';
        activeAlertsCount = 0;
    });
    
    // Modal controls
    modalCancel.addEventListener('click', closeThresholdModal);
    modalSave.addEventListener('click', saveThresholdChange);
});

// --- HTTP API calls ---

async function fetchActiveSensors() {
    try {
        const response = await fetch('/api/sensors');
        if (response.ok) {
            activeSensors = await response.json();
            renderSensorsList();
        }
    } catch (e) {
        console.error("Failed to fetch running sensors list:", e);
    }
}

async function fetchCustomThresholds() {
    try {
        const response = await fetch('/api/thresholds');
        if (response.ok) {
            customThresholds = await response.json();
        }
    } catch (e) {
        console.error("Failed to fetch thresholds:", e);
    }
}

// Render dynamic sensor threads list in table
function renderSensorsList() {
    if (activeSensors.length === 0) {
        activeSensorsList.innerHTML = `
            <tr>
                <td colspan="6" class="empty-message">No active sensor threads running in the J2SE pool.</td>
            </tr>
        `;
        return;
    }

    activeSensorsList.innerHTML = activeSensors.map(sensor => {
        const typeClass = sensor.sensorType.toLowerCase();
        const typeLabel = sensor.sensorType.charAt(0) + sensor.sensorType.slice(1).toLowerCase();
        
        // Determine threshold value to display
        const defaultThreshold = sensor.sensorType === 'TEMPERATURE' ? 40.0 
                              : sensor.sensorType === 'HUMIDITY' ? 80.0 
                              : 1035.0;
        const currentThreshold = customThresholds[sensor.sensorId] !== undefined 
                              ? customThresholds[sensor.sensorId] 
                              : defaultThreshold;

        const unit = sensor.sensorType === 'TEMPERATURE' ? '°C'
                   : sensor.sensorType === 'HUMIDITY' ? '%'
                   : 'hPa';

        return `
            <tr>
                <td class="sensor-id-col">${sensor.sensorId}</td>
                <td><span class="badge-sensor-type ${typeClass}">${typeLabel}</span></td>
                <td>${sensor.baseline} ${unit}</td>
                <td>${(sensor.intervalMs / 1000).toFixed(1)}s</td>
                <td>
                    <span class="threshold-value">${currentThreshold} ${unit}</span>
                    <span class="edit-threshold-link" onclick="openThresholdModal('${sensor.sensorId}', ${currentThreshold})">Edit</span>
                </td>
                <td>
                    <button class="btn btn-danger btn-sm" onclick="terminateSensor('${sensor.sensorId}')">Stop Thread</button>
                </td>
            </tr>
        `;
    }).join('');
}

// Handler to spawn sensor thread
async function handleSpawnSensor(e) {
    e.preventDefault();
    const id = document.getElementById('sensor-id').value.trim();
    const type = document.getElementById('sensor-type').value;
    const baselineInput = document.getElementById('sensor-baseline').value;
    const amplitudeInput = document.getElementById('sensor-amplitude').value;
    const intervalInput = document.getElementById('sensor-interval').value;

    const payload = {
        sensorId: id,
        sensorType: type
    };

    // If custom values are provided, add them to body
    if (baselineInput !== "") payload.baseline = parseFloat(baselineInput);
    if (amplitudeInput !== "") payload.fluctuationAmplitude = parseFloat(amplitudeInput);
    if (intervalInput !== "") payload.intervalMs = parseInt(intervalInput);

    try {
        const response = await fetch('/api/sensors', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (response.ok) {
            spawnSensorForm.reset();
            fetchActiveSensors();
        } else {
            const errorMsg = await response.text();
            alert("Error: " + errorMsg);
        }
    } catch (e) {
        alert("Failed to connect to the backend to spawn sensor thread.");
    }
}

// Terminate sensor simulation thread
async function terminateSensor(sensorId) {
    if (!confirm(`Are you sure you want to stop sensor thread "${sensorId}"?`)) return;

    try {
        const response = await fetch(`/api/sensors/${sensorId}`, { method: 'DELETE' });
        if (response.ok) {
            fetchActiveSensors();
        }
    } catch (e) {
        console.error("Failed to terminate sensor thread:", e);
    }
}

// --- Threshold Modal Controls ---

function openThresholdModal(sensorId, currentVal) {
    targetSensorId = sensorId;
    modalSensorId.textContent = sensorId;
    modalThresholdVal.value = currentVal;
    thresholdModal.classList.add('open');
}

function closeThresholdModal() {
    thresholdModal.classList.remove('open');
    targetSensorId = null;
}

async function saveThresholdChange() {
    if (!targetSensorId) return;
    const newVal = parseFloat(modalThresholdVal.value);
    if (isNaN(newVal)) return;

    try {
        const response = await fetch(`/api/thresholds/${targetSensorId}?value=${newVal}`, {
            method: 'POST'
        });

        if (response.ok) {
            customThresholds[targetSensorId] = newVal;
            closeThresholdModal();
            fetchActiveSensors();
        }
    } catch (e) {
        console.error("Failed to update alarm threshold:", e);
    }
}

// --- Server Sent Events (SSE) Live Feed ---

function initSseConnection() {
    updateConnectionUI('reconnecting', 'Connecting...');
    
    if (sseSource) {
        sseSource.close();
    }

    sseSource = new EventSource('/api/live-stream');

    sseSource.addEventListener('handshake', (e) => {
        updateConnectionUI('online', 'Connected');
        logTerminalMsg(`// Broker handshake successful: ${e.data}`, 'system');
    });

    sseSource.addEventListener('telemetry', (e) => {
        const eventData = JSON.parse(e.data);
        handleTelemetryUpdate(eventData);
    });

    sseSource.onerror = (err) => {
        console.warn("SSE stream disconnected. Retrying...");
        updateConnectionUI('offline', 'Disconnected');
        logTerminalMsg(`// Connection lost. Retrying to connect in 3s...`, 'error');
        
        // Retry connection after 3 seconds
        sseSource.close();
        setTimeout(initSseConnection, 3000);
    };
}

function updateConnectionUI(status, label) {
    connectionStatus.className = `status-indicator ${status}`;
    connectionStatus.querySelector('.status-text').textContent = label;
}

// Handles incoming JSON update message
function handleTelemetryUpdate(event) {
    const { packet, sensorSnapshot, typeSnapshot, isAlert, alertMessage } = event;
    const type = packet.sensorType.toLowerCase(); // 'temperature', 'humidity', 'pressure'
    
    // 1. Update Aggregate Cards using global type aggregates
    updateAggregateCard(type, typeSnapshot);

    // 2. Log in Terminal View
    const cleanPayload = JSON.stringify({
        sensorId: packet.sensorId,
        value: packet.value,
        timestamp: packet.timestamp
    });
    logTerminalMsg(`[IN] topic: sensor/${type}/${packet.sensorId} | payload: ${cleanPayload}`, type);

    // 3. Process Alerts
    if (isAlert) {
        triggerAlertNotification(packet.sensorId, alertMessage);
    }
}

// Update DOM elements on the aggregate cards
function updateAggregateCard(type, snapshot) {
    let cardPrefix = '';
    if (type === 'temperature') cardPrefix = 'temp';
    else if (type === 'humidity') cardPrefix = 'hum';
    else if (type === 'pressure') cardPrefix = 'press';

    if (!cardPrefix) return;

    // Update values
    document.getElementById(`${cardPrefix}-avg`).textContent = snapshot.mean.toFixed(2);
    document.getElementById(`${cardPrefix}-min`).textContent = snapshot.min.toFixed(2);
    document.getElementById(`${cardPrefix}-max`).textContent = snapshot.max.toFixed(2);
    document.getElementById(`${cardPrefix}-stddev`).textContent = snapshot.standardDeviation.toFixed(2);
    document.getElementById(`${cardPrefix}-count`).textContent = snapshot.count;

    // Trigger blinking indicator
    const card = document.querySelector(`.${cardPrefix}-card`);
    if (card) {
        card.classList.add('card-update-blink');
        setTimeout(() => {
            card.classList.remove('card-update-blink');
        }, 150);
    }
}

// Append logs to terminal viewer
function logTerminalMsg(message, type) {
    const line = document.createElement('div');
    line.className = `terminal-line ${type}`;
    
    const now = new Date();
    const timeStr = now.toTimeString().split(' ')[0] + '.' + String(now.getMilliseconds()).padStart(3, '0');
    line.textContent = `[${timeStr}] ${message}`;
    
    telemetryLog.appendChild(line);

    // Prune excessive lines to keep browser fast
    while (telemetryLog.children.length > 100) {
        telemetryLog.removeChild(telemetryLog.firstChild);
    }

    // Scroll to bottom
    telemetryLog.scrollTop = telemetryLog.scrollHeight;
}

// Append breach notifications in alerts list
function triggerAlertNotification(sensorId, message) {
    // Remove default empty status message
    const emptyLog = alertsFeed.querySelector('.empty-log');
    if (emptyLog) {
        alertsFeed.removeChild(emptyLog);
    }

    const alert = document.createElement('div');
    alert.className = 'alert-item';
    
    const now = new Date();
    const timeStr = now.toTimeString().split(' ')[0];

    alert.innerHTML = `
        <div class="alert-time">[ALERT AT ${timeStr}]</div>
        <div class="alert-msg"><strong>${sensorId}</strong>: ${message}</div>
    `;

    // Add to the top of the alerts feed
    alertsFeed.insertBefore(alert, alertsFeed.firstChild);

    // Limit to 20 alarms shown
    while (alertsFeed.children.length > 20) {
        alertsFeed.removeChild(alertsFeed.lastChild);
    }
}
