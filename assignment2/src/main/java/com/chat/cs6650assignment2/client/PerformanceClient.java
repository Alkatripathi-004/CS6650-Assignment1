package com.chat.cs6650assignment2.client;

import com.chat.cs6650assignment2.messagegenerator.MessageGenerator;
import com.chat.cs6650assignment2.model.ChatMessage;
import com.google.common.util.concurrent.RateLimiter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class PerformanceClient {

    private static final String SERVER_URL = "ws://chat-app-alb-819484957.us-east-1.elb.amazonaws.com/chat";
    //chat-app-alb-819484957.us-east-1.elb.amazonaws.com
    private static int NUM_THREADS = 256;
    private static double RATE_LIMIT_PER_SECOND = 4000;
    private static int TOTAL_MESSAGES = 500000;

    public static final ChatMessage POISON_PILL = new ChatMessage();
    private static final int MESSAGE_QUEUE_CAPACITY = 10000;

    public static void main(String[] args) {
        System.out.println("\n=========== STARTING PERFORMANCE TEST ===========");
        System.out.printf("Configuration: URL=%s, Threads=%d, Messages=%d, Rate Limit=%.2f/s%n",
                SERVER_URL, NUM_THREADS, TOTAL_MESSAGES, RATE_LIMIT_PER_SECOND);

        runTestPhase();

        System.out.println("=========== PERFORMANCE TEST COMPLETE ===========");
    }

    private static void runTestPhase() {
        String csvFilePath = String.format("results/performance_metrics_%d_threads_%d_msgs.csv", NUM_THREADS, TOTAL_MESSAGES);
        BlockingQueue<ChatMessage> messageQueue = new LinkedBlockingQueue<>(MESSAGE_QUEUE_CAPACITY);
        PerformanceReporter reporter = new PerformanceReporter(csvFilePath);
        RateLimiter sharedRateLimiter = RateLimiter.create(RATE_LIMIT_PER_SECOND);

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS + 1);

        MessageGenerator generator = new MessageGenerator(messageQueue, TOTAL_MESSAGES, NUM_THREADS, POISON_PILL);
        List<WebSocketSenderTask> senderTasks = new ArrayList<>();
        for (int i = 0; i < NUM_THREADS; i++) {
            senderTasks.add(new WebSocketSenderTask(SERVER_URL, i, messageQueue, reporter, POISON_PILL, sharedRateLimiter));
        }

        long startTime = System.currentTimeMillis();

        executor.submit(generator);
        senderTasks.forEach(executor::submit);

        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
                System.err.println("Executor did not terminate in the allotted time!");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("All tasks have completed execution.");

        int totalSuccess = senderTasks.stream().mapToInt(WebSocketSenderTask::getSuccessfulCount).sum();
        int totalFailed = senderTasks.stream().mapToInt(WebSocketSenderTask::getFailedCount).sum();
        int totalInitialConnections = senderTasks.stream().mapToInt(WebSocketSenderTask::getTotalConnections).sum();
        int totalReconnections = senderTasks.stream().mapToInt(WebSocketSenderTask::getTotalReconnections).sum();

        // CHANGED: We only collect one list of latencies now.
        List<Long> allLatencies = senderTasks.stream()
                .flatMap(task -> task.getLatencies().stream())
                .collect(Collectors.toList());

        double durationSeconds = (endTime - startTime) / 1000.0;
        double throughput = (durationSeconds > 0) ? totalSuccess / durationSeconds : 0;

        System.out.println("\n--- Final Results ---");
        System.out.println("Number of Threads: " + NUM_THREADS);
        System.out.println("Total Successful Messages (Broadcast Received): " + totalSuccess);
        System.out.println("Total Failed Messages: " + totalFailed);
        System.out.println("Total Connections: " + (totalReconnections + totalInitialConnections));
        System.out.println("Total Reconnections: " + totalReconnections);
        System.out.printf("Total Runtime: %.2f seconds%n", durationSeconds);
        System.out.printf("Overall Throughput: %.2f messages/second%n", throughput);

        reporter.writeToCsv();
        // CHANGED: Simplified reporting output.
        System.out.println("\n--- Broadcast Latency Statistics ---");
        reporter.printStatistics(allLatencies);
    }
}