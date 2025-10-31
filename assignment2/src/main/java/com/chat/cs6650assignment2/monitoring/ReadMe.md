## Accessing Application Metrics

This application uses the **Spring Boot Actuator** framework to expose key health and performance metrics over HTTP.

### Prerequisites

1.  The Spring Boot application must be running.
2.  You need command-line access (SSH) to the EC2 instance where the application is running.

### Instructions

All commands are to be run from the terminal of the EC2 instance hosting the application.

#### 1. List All Available Metrics

To see a list of all metric names that the application is tracking, query the main metrics endpoint. This is useful for discovering what can be monitored.

```bash
curl -s http://localhost:8080/actuator/metrics
```


#### 2. View a Specific Metric's Value

To get the detailed value of a specific metric, append its name to the metrics URL.

**A. To Check a Counter (e.g., Total Messages Processed):**

This metric shows the total number of messages successfully processed by the consumer since the application started.

```bash
curl -s http://localhost:8080/actuator/metrics/chat.messages.processed
```

**Example Output:**
```json
{
  "name": "chat.messages.processed",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 500000.0
    }
  ]
}
```

**Other available counters:**
*   `chat.messages.duplicates`: Total duplicate messages detected and ignored.
*   `chat.messages.failed`: Total messages that failed processing and were re-queued.

#### B. To Check a Timer (e.g., Message Processing Latency):**

This metric provides statistics on how long the `onMessage` method takes to execute, which is a key indicator of consumer performance.

```bash
curl -s http://localhost:8080/actuator/metrics/chat.message.processing.time
```

**Example Output:**
```json
{
  "name": "chat.message.processing.time",
  "baseUnit": "seconds",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 500000.0
    },
    {
      "statistic": "TOTAL_TIME",
      "value": 150.12345
    },
    {
      "statistic": "MAX",
      "value": 0.09876
    }
  ],
}