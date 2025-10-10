package com.chat.CS66650_Assignment1.client1;

import com.chat.CS66650_Assignment1.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class WarmupClient {

    private static final String SERVER_URL = "ws://52.38.202.150:8080/chat";
    private static final int NUM_THREADS = 32;
    private static final int MESSAGES_PER_THREAD = 1000;

    private static final AtomicInteger successfulMessages = new AtomicInteger(0);
    private static final AtomicInteger failedMessages = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=========== STARTING WARMUP PHASE ===========");

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        CountDownLatch latch = new CountDownLatch(NUM_THREADS);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < NUM_THREADS; i++) {
            executor.submit(new ClientTask(i, latch));
        }

        latch.await(); // Wait for all threads to finish
        long endTime = System.currentTimeMillis();
        executor.shutdown();

        double durationSeconds = (endTime - startTime) / 1000.0;
        double throughput = (durationSeconds > 0) ? successfulMessages.get() / durationSeconds : 0;

        System.out.println("\n--- Warmup Phase Results ---");
        System.out.println("Total Messages Sent: " + (NUM_THREADS * MESSAGES_PER_THREAD));
        System.out.println("Successful Messages (ACKed): " + successfulMessages.get());
        System.out.println("Failed Messages: " + failedMessages.get());
        System.out.printf("Total Runtime: %.2f seconds%n", durationSeconds);
        System.out.printf("Overall Throughput: %.2f messages/second%n", throughput);
        System.out.println("==========================================================");
    }

    static class ClientTask implements Runnable {
        private final int clientId;
        private final CountDownLatch latch;
        private final ObjectMapper objectMapper = new ObjectMapper();

        ClientTask(int clientId, CountDownLatch latch) {
            this.clientId = clientId;
            this.latch = latch;
        }

        @Override
        public void run() {
            String roomId = "room" + (clientId % 20 + 1);
            try {
                URI serverUri = new URI(SERVER_URL + "/" + roomId);
                CountDownLatch messageLatch = new CountDownLatch(MESSAGES_PER_THREAD);

                WebSocketClient client = new WebSocketClient(serverUri) {
                    @Override
                    public void onOpen(ServerHandshake handshakedata) {}
                    @Override
                    public void onMessage(String message) {
                        System.out.println("Client Message Received: " + message);
                        successfulMessages.incrementAndGet();
                        messageLatch.countDown();
                    }
                    @Override
                    public void onClose(int code, String reason, boolean remote) {}
                    @Override
                    public void onError(Exception ex) {
                        System.err.println("Client " + clientId + " error: " + ex.getMessage());
                    }
                };

                if (!client.connectBlocking(5, TimeUnit.SECONDS)) {
                    failedMessages.addAndGet(MESSAGES_PER_THREAD);
                    return;
                }

                for (int i = 0; i < MESSAGES_PER_THREAD; i++) {
                    ChatMessage msg = createSampleMessage();
                    client.send(objectMapper.writeValueAsString(msg));
                }

                // Wait for acks, but with a timeout
                if (!messageLatch.await(30, TimeUnit.SECONDS)) {
                    System.err.println("Client " + clientId + " timed out waiting for all ACKs.");
                    failedMessages.addAndGet((int) messageLatch.getCount());
                }

                client.close();

            } catch (Exception e) {
                failedMessages.addAndGet(MESSAGES_PER_THREAD);
                System.err.println("Client task " + clientId + " failed: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        }

        private ChatMessage createSampleMessage() {
            ChatMessage msg = new ChatMessage();
            msg.setUserId(String.valueOf(clientId));
            msg.setUsername("user" + clientId);
            msg.setMessage("Warmup message");
            msg.setTimestamp(Instant.now().toString());
            msg.setMessageType(ChatMessage.MessageType.TEXT);
            return msg;
        }
    }
}