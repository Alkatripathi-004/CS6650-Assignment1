package com.chat.cs6650assignment1.client2;

import com.chat.cs6650assignment1.messagegenerator.MessageGenerator;
import com.chat.cs6650assignment1.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class PerformanceClient {

    private static final String SERVER_URL = "ws://localhost:8080/chat";
    private static final String CSV_FILE_PATH_FORMAT = "results/performance_metrics_%d.csv";

    public static final int MAIN_PHASE_THREADS = 64;
    private static final int TOTAL_MAIN_MESSAGES = 500000;
    private static final int MESSAGE_QUEUE_CAPACITY = 20000;
    public static final ChatMessage POISON_PILL = new ChatMessage();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("\n=========== STARTING MAIN PERFORMANCE PHASE ===========");
        runMainPhase();
        System.out.println("=========== MAIN PERFORMANCE PHASE COMPLETE ===========");
    }

    private static void runMainPhase() throws InterruptedException {
        String csvFilePath = String.format(CSV_FILE_PATH_FORMAT, System.currentTimeMillis());
        BlockingQueue<ChatMessage> messageQueue = new LinkedBlockingQueue<>(MESSAGE_QUEUE_CAPACITY);
        PerformanceReporter reporter = new PerformanceReporter(csvFilePath);

        ExecutorService executor = Executors.newFixedThreadPool(MAIN_PHASE_THREADS + 1);

        MessageGenerator generator = new MessageGenerator(messageQueue, TOTAL_MAIN_MESSAGES, MAIN_PHASE_THREADS, POISON_PILL);
        List<WebSocketSenderTask> senderTasks = new ArrayList<>();
        for (int i = 0; i < MAIN_PHASE_THREADS; i++) {
            senderTasks.add(new WebSocketSenderTask(SERVER_URL, i, messageQueue, reporter, POISON_PILL));
        }

        long startTime = System.currentTimeMillis();

        System.out.println("Submitting generator and all sender tasks to run concurrently...");
        executor.submit(generator);
        senderTasks.forEach(executor::submit);

        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
                System.err.println("Executor did not terminate in the allotted time!");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("All tasks have completed execution.");

        int totalSuccess = senderTasks.stream().mapToInt(WebSocketSenderTask::getSuccessfulCount).sum();
        int totalFailed = senderTasks.stream().mapToInt(WebSocketSenderTask::getFailedCount).sum();
        int totalInitialConnections =  senderTasks.stream().mapToInt(WebSocketSenderTask::getTotalConnections).sum();
        int totalReconnections =  senderTasks.stream().mapToInt(WebSocketSenderTask::getTotalReconnections).sum();
        List<Long> allLatencies = senderTasks.stream()
                .flatMap(task -> task.getLatencies().stream())
                .collect(Collectors.toList());

        double durationSeconds = (endTime - startTime) / 1000.0;
        double throughput = (durationSeconds > 0) ? totalSuccess / durationSeconds : 0;

        System.out.println("\n--- Final Results ---");
        System.out.println("Number of Threads: " + MAIN_PHASE_THREADS);
        System.out.println("Total Successful Messages: " + totalSuccess);
        System.out.println("Total Failed Messages: " + totalFailed);
        System.out.println("Total Connections: " + (totalReconnections + totalInitialConnections));
        System.out.println("Total Reconnections: " + totalReconnections);
        System.out.printf("Total Runtime: %.2f seconds%n", durationSeconds);
        System.out.printf("Overall Throughput: %.2f messages/second%n", throughput);

        reporter.writeToCsv();
        reporter.printStatistics(allLatencies);
    }
}