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