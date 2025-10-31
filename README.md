# CS6650 - Assignment 1: Scalable WebSocket Chat Server & Client

## Project Overview

The core components of this project are:
*   **A WebSocket Server:** Built with Java and Spring Boot, it handles WebSocket connections, validates incoming chat messages, and echoes acknowledgments back to the sender. It also includes a `GET /health` endpoint.
*   **Basic Load Testing Client (`client1`):** A multithreaded client designed to "warm up" the server by sending a fixed number of messages from a fixed number of threads and reporting basic metrics.
*   **Performance Analysis Client (`client2`):** A sophisticated, concurrent producer-consumer client designed to send a high volume of messages (500,000+) while collecting detailed per-message metrics. It accurately calculates round-trip latency, generates a CSV report for analysis.

## Project Structure

The repository is organized according to the assignment requirements:

```
.
├── server/              # Spring Boot WebSocket Server Implementation
├── client1/        # Basic "warmup" load testing client
├── client2/        # Advanced client with performance analysis & rate limiting
├── results/             # Directory for test outputs (screenshots, CSV, charts)
└── README.md            # This file
```

## Prerequisites

To build and run this project, you will need:
*   **Java JDK 21**
*   **Apache Maven** 3.6+
*   **An active AWS Account** to deploy the server on an EC2 instance for realistic testing.
*   **Git** for cloning the repository.

---

## How to Run the Code

**IMPORTANT:** For accurate and meaningful performance metrics, the **Server must be run on a dedicated EC2 instance** and the **Clients must be run from a different machine** (e.g., your local laptop or another EC2 instance).

### Step 1: Deploy and Run the Server on EC2

Follow the detailed **[EC2 Deployment Guide](#ec2-deployment-guide)** at the bottom of this README to launch an instance, install Java, copy the server JAR, and run it.

Once deployed, your server will be accessible at `ws://<YOUR_EC2_PUBLIC_IP>:8080/chat/{roomId}`.

### Step 2: Run the Warmup Client (client-part1)

This client warms up the server's JVM and performs a basic load test.

1.  **Configure Server URL:**  
    Open the file `src/main/java/com/CS66650_Assignment1/client1/WarmupClient.java`.
    Update the `SERVER_URL` constant with your EC2 instance's public IP address.
    ```java
    private static final String SERVER_URL = "ws://YOUR_EC2_PUBLIC_IP:8080/chat";
    ```
    
2. **Run the Client:**
    You can run the client using intelliJ
    The client will run for a short time and print a summary of the warmup phase results to the console.

### Step 3: Run the Performance Analysis Client (client-part2)

This is the main test client that generates the detailed performance report.

1.  **Configure Server URL and Load:**  
    Open the file `src/main/java/com/CS66650_Assignment1/client2/PerformanceClient.java`.
    *   Update the `SERVER_URL` constant with your EC2 instance's public IP

    ```java
    // Update the IP address
    private static final String SERVER_URL = "ws://YOUR_EC2_PUBLIC_IP:8080/chat";

3.  **Run the Client:**
    You can run the client using IntelliJ after building the project using mvn clean install

### Step 4: Analyze the Results

After `PerformanceClient` finishes, you will have two outputs:

1.  **Console Output:** A summary of the final results, including total runtime, overall throughput, and key statistical metrics (mean/median/percentile latency).
2.  **CSV Report:** A new CSV file will be created in the `/results` directory (e.g., `results/performance_metrics_20250214_183055.csv`). This file contains the detailed round-trip latency for every single message, which can be used to generate charts and perform deeper analysis.

---

## EC2 Deployment Guide

Follow these steps to deploy the server to a production-like environment.

**1. Launch EC2 Instance**
*   Log in to your AWS Console and navigate to the EC2 dashboard.
*   Launch a new instance.
    *   **AMI:** Select **Amazon Linux 2** or **Amazon Linux 2023**.
    *   **Instance Type:** A `t2.micro` will work, but for better performance testing, a **`t3.medium`** or a **`c5.large`** (Compute Optimized) is highly recommended.
    *   **CPU Credits (for t3 instances):** For best results, enable **T3 Unlimited** mode in the instance settings to avoid CPU throttling during the test.
*   **Key Pair:** Create or select an existing `.pem` key pair to access your instance.

**2. Configure Security Group**
*   Create a new security group or modify the existing one.
*   Add two **Inbound rules**:
    *   **Type:** `SSH`, Port: `22`, Source: `My IP` (for secure access).
    *   **Type:** `Custom TCP`, Port: `8080`, Source: `Anywhere` (`0.0.0.0/0`) (so your client can connect).

**3. Build the Server JAR**
*   On your local machine, navigate to the `/server` directory.
*   Run `mvn clean package`. This will create the executable JAR file in `server/target/`.

**4. Install Java on EC2**
*   Connect to your instance using SSH:
    ```bash
    ssh -i /path/to/your/key.pem ec2-user@<YOUR_EC2_PUBLIC_IP>
    ```
*   Update the package manager and install Java 21:
    ```bash
    sudo yum update -y
    sudo yum install java-21-amazon-corretto-devel -y
    ```*   Verify the installation with `java -version`.

**5. Copy the JAR to EC2**
*   From your **local machine's terminal**, use the `scp` command to upload the server JAR:
    ```bash
    scp -i /path/to/your/key.pem server/target/chat-server-0.0.1-SNAPSHOT.jar ec2-user@<YOUR_EC2_PUBLIC_IP>:~/
    ```

**6. Run the Server on EC2**
*   In your SSH session on the EC2 instance, run the server using `nohup` to keep it running after you disconnect:
    ```bash
    nohup java -jar chat-server-0.0.1-SNAPSHOT.jar > server.log 2>&1 &
    ```
*   Your server is now live! You can check its logs with `tail -f server.log`.


# Assignment 2

# CS6650 - Assignment 2: Distributed Chat Application

This project implements a scalable, distributed real-time chat application using Java, Spring Boot, WebSockets, and RabbitMQ. 
It builds upon the foundational concepts of Assignment 1 by introducing a robust message queueing system for message distribution, 
ensuring reliability and scalability across multiple server instances.

## System Architecture

The application is designed as a distributed system, capable of running multiple instances behind a load balancer. 
It uses a sophisticated two-phase messaging pattern to ensure both efficient processing and reliable broadcasting.

### Core Components:
1.  **WebSocket Server Instances:** Lightweight gateways responsible for managing persistent WebSocket connections from clients.
2.  **RabbitMQ:** The central message broker, handling the flow of messages between system components.
3.  **Client Application:** A multi-threaded performance client used for load testing the system.

### Message Flow (Fan-in, Fan-out)

The system employs a two-exchange model to correctly handle message processing and broadcasting in a distributed environment:

1.  **Phase 1: Fan-in (Work Distribution)**
    *   A client sends a message to its connected WebSocket server instance.
    *   The server publishes the message to a **Topic Exchange** (`chat.exchange`) with a routing key based on the room ID (e.g., `room.5`).
    *   The message is routed to one of 20 durable, shared **"work" queues** (`room.1` through `room.20`).
    *   One of the running consumer threads from any server instance consumes the message (Competing Consumer pattern), guaranteeing that each message is processed **exactly once**.

2.  **Phase 2: Fan-out (Broadcast)**
    *   After processing, the winning consumer re-publishes the message to a **Fanout Exchange** (`chat.broadcast.exchange`).
    *   This exchange broadcasts a copy of the message to **every running server instance** via their private, anonymous queues.
    *   Each server instance receives the broadcast and pushes the message to any locally connected clients in the target room.

This architecture successfully decouples message processing from message delivery, allowing for independent scaling and enhanced resilience.

## Features Implemented

### Server-Side
- **Distributed WebSocket Handling:** Manages WebSocket connections across multiple server instances.
- **RabbitMQ Integration:** Utilizes a Topic Exchange for work distribution and a Fanout Exchange for broadcasting.
- **Durable Queues:** Adheres to the requirement of one durable queue per room (`room.1` - `room.20`) with configured TTL and max-length limits to protect the broker.
- **Multi-threaded, Partitioned Consumer:** A configurable pool of consumer threads where each thread is responsible for a specific subset of rooms. This design choice **guarantees message ordering within each room** at the consumer level.
- **At-Least-Once Delivery:** Employs manual acknowledgements (`ack`/`nack`) to ensure messages are not lost if a consumer fails.
- **Idempotent Consumer:** Tracks processed message IDs to safely handle duplicate message deliveries.
- **Resilience Patterns:**
    - **Circuit Breaker:** A circuit breaker on the message producer prevents the application from being overwhelmed by a disconnected message broker.
    - **Flow Control:** Uses RabbitMQ's prefetch mechanism to prevent consumers from being overloaded.
- **Health Metrics:** Exposes key application metrics (e.g., messages processed, duplicates, failures) via Spring Boot Actuator endpoints (`/actuator/metrics`).
- **Latency Monitoring:** Uses Micrometer's `@Timed` annotation to measure the processing latency of the consumer.

### Client-Side
- **Multi-threaded Performance Client:** A configurable load testing client capable of simulating a high number of concurrent users.
- **Rate Limiting:** Uses a shared rate limiter to generate a stable, predictable load on the server.
- **End-to-End Latency Measurement:** Accurately measures the full round-trip time of a message, from client send to broadcast receive.
- **Statistical Reporting:** Generates and prints key performance statistics (mean, median, p95, p99, throughput) upon test completion.

## How to Build and Run

### Prerequisites
- Java 21 (or your configured version)
- Apache Maven 3.6+
- A running RabbitMQ instance

### Build
The project is structured as a multi-module Maven project. To build, navigate to the parent directory and run:

```bash
mvn clean install
```

This will create two self-contained, executable JAR files in the `target` directory of each module (`assignment1/target/` and `assignment2/target/`). We will use the `assignment2` JAR.

### Run
The application is designed to run in multiple instances with identical code.

1.  **Configure RabbitMQ Connection:**
    Ensure your `src/main/resources/application.properties` file is configured with the correct host, port, and credentials for your RabbitMQ server.

2.  **Run the Server Instances:**
    Upload the `assignment2-x.x.x-SNAPSHOT.jar` file to your EC2 instances. Launch the application from the command line.

    ```bash
    # Run on all instances
    # The application will start and all instances will compete to consume messages
    # and will also listen for broadcasts.
    nohup java -jar assignment2-x.x.x-SNAPSHOT.jar > app.log 2>&1 &
    ```

3.  **Run the Performance Client:**
    The performance client is included within the project. To run it from your IDE, configure the `PerformanceClient` class with your server's URL (e.g., your ALB endpoint) and desired test parameters, then execute the `main` method.

## Architectural Trade-offs and Discussion

- **Competing Consumer vs. Broadcast:** A key challenge was adhering to the "one queue per room" requirement while ensuring all clients received messages. This was solved by implementing a two-phase "Fan-in, Fan-out" pattern. While this introduces a second hop through RabbitMQ, it correctly decouples the "single writer" processing logic (e.g., saving to a database) from the broadcast mechanism.
- **Message Ordering:** The partitioned multi-threaded consumer guarantees message ordering *at the consumer level* for each room. However, true end-to-end ordering is not guaranteed due to network race conditions.