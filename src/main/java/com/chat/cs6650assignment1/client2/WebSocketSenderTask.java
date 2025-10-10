package com.chat.cs6650assignment1.client2;


import com.chat.cs6650assignment1.model.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class WebSocketSenderTask implements Runnable {

    private final String serverUrl;
    private final BlockingQueue<ChatMessage> messageQueue;
    private final PerformanceReporter reporter;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, PendingRequest> pendingMessages;
    private WebSocketClient client;
    private final ChatMessage poisonPill;
    private final int workerId;
    private final int roomId;
    private final URI serverUri;

    private final AtomicInteger successfulMessages;
    private final AtomicInteger failedMessages;
    private final List<Long> latencies;
    private final AtomicInteger totalConnections;
    private final AtomicInteger totalReconnections;


    public WebSocketSenderTask(String serverBaseUrl, int workerId, BlockingQueue<ChatMessage> messageQueue, PerformanceReporter reporter, ChatMessage poisonPill) {
        this.serverUrl = serverBaseUrl;
        this.workerId = workerId;
        this.messageQueue = messageQueue;
        this.reporter = reporter;
        this.poisonPill = poisonPill;
        this.roomId = workerId % 20 + 1;
        this.serverUri = URI.create(serverUrl + "/room" + (workerId % 20 + 1));
        this.pendingMessages = new ConcurrentHashMap<>();
        this.successfulMessages = new AtomicInteger(0);
        this.failedMessages = new AtomicInteger(0);
        this.totalConnections = new AtomicInteger(0);
        this.totalReconnections = new AtomicInteger(0);
        this.latencies = new ArrayList<>();
    }

    @Override
    public void run() {
        try {
            while (true) {
                ChatMessage message = messageQueue.take();
                if (message == poisonPill) {
                    break;
                }
                try {
                    ensureConnection(workerId);
                    message.setTimestamp(Instant.now().toString());
                    sendMessageWithRetry(message);
                } catch (Exception e) {
                    failMessage(message, "Unhandled exception in send loop: " + e.getMessage());
                }
            }

            waitForAcks();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[" + Thread.currentThread().getName() + "] was interrupted.");
        } finally {
            closeConnection();
        }
    }

    private void ensureConnection(int workerId) throws URISyntaxException, InterruptedException {
        if (client == null || !client.isOpen()) {
            client = createClient(serverUri);
            if(totalConnections.get() > 0) {
                totalReconnections.incrementAndGet();
            } else {
                totalConnections.incrementAndGet();
            }
            System.out.println("[" + Thread.currentThread().getName() + "] Connecting to " + serverUri);
            if (!client.connectBlocking(5, TimeUnit.SECONDS)) {
                System.err.println("[" + Thread.currentThread().getName() + "] Failed to connect.");
                throw new RuntimeException("Connection failed");
            }
        }
    }

    private void sendMessageWithRetry(ChatMessage message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            int maxRetries = 5;
            long backoff = 100;

            for (int i = 0; i < maxRetries; i++) {
                if (client.isOpen()) {
                    pendingMessages.put(message.getMessageId(), new PendingRequest(message, System.currentTimeMillis()));
                    client.send(jsonMessage);
                    return;
                }
                Thread.sleep(backoff);
                backoff *= 2;
                ensureConnection(workerId);
            }

            failMessage(message, "Failed to send after " + maxRetries + " retries.");

        } catch (Exception e) {
            failMessage(message, "Exception during send: " + e.getMessage());
        }
    }

    private void failMessage(ChatMessage message, String reason) {
        failedMessages.incrementAndGet();
        pendingMessages.remove(message.getMessageId());
        reporter.addEntry(new PerformanceEntry(
                message.getTimestamp(),
                Instant.now().toString(),
                message.getMessageType().toString(),
                -1,
                500,
                roomId
        ));
    }

    private void waitForAcks() {
        System.out.println("[" + Thread.currentThread().getName() + "] Finished sending. Now waiting for " + pendingMessages.size() + " pending ACKs...");

        long waitStartTime = System.currentTimeMillis();
        long timeoutMillis = 100000;

        while (!pendingMessages.isEmpty()) {
            if (System.currentTimeMillis() - waitStartTime > timeoutMillis) {
                System.err.println("[" + Thread.currentThread().getName() + "] TIMED OUT waiting for ACKs. " + pendingMessages.size() + " messages will be marked as failed.");
                pendingMessages.values().forEach(pr -> failMessage(pr.message, "Timeout"));
                pendingMessages.clear();
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[" + Thread.currentThread().getName() + "] Interrupted while waiting for ACKs.");
                break;
            }
        }
    }


    private WebSocketClient createClient(URI serverUri) {
        return new WebSocketClient(serverUri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {}

            @Override
            public void onMessage(String message) {
                try {
                    System.out.println("[" + Thread.currentThread().getName() + "] Received: " + message);
                    JsonNode responseNode = objectMapper.readTree(message);
                    String originalId = responseNode.get("originalMessageId").asText();

                    PendingRequest pendingRequest = pendingMessages.remove(originalId);

                    if (pendingRequest != null) {
                        long endTime = System.currentTimeMillis();
                        long latency = endTime - pendingRequest.startTime;
                        successfulMessages.incrementAndGet();
                        latencies.add(latency);

                        reporter.addEntry(new PerformanceEntry(
                                pendingRequest.message.getTimestamp(),
                                Instant.now().toString(),
                                pendingRequest.message.getMessageType().toString(),
                                latency,
                                200,
                                roomId
                        ));
                    }
                } catch (Exception e) {
                    System.err.println("[" + Thread.currentThread().getName() + "] Error parsing ACK: " + e.getMessage());
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("[" + Thread.currentThread().getName() + "] Connection closed: " + reason);
            }

            @Override
            public void onError(Exception ex) {
                System.err.println("[" + Thread.currentThread().getName() + "] WebSocket error: " + ex.getMessage());
            }
        };
    }

    private void closeConnection() {
        if (client != null && client.isOpen()) {
            client.close();
        }
        System.out.println("[" + Thread.currentThread().getName() + "] Task finished. Success: " + getSuccessfulCount() + ", Failed: " + getFailedCount());
    }

    public int getSuccessfulCount() {
        return successfulMessages.get();
    }

    public int getFailedCount() {
        return failedMessages.get();
    }

    public List<Long> getLatencies() {
        return latencies;
    }

    public int getTotalConnections() {
        return totalConnections.get();
    }

    public int getTotalReconnections() {
        return totalReconnections.get();
    }

    class PendingRequest {
        final ChatMessage message;
        final long startTime;

        public PendingRequest(ChatMessage message, long startTime) {
            this.message = message;
            this.startTime = startTime;
        }
    }
}