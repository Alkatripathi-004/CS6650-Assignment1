package com.chat.cs6650assignment2.client;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PerformanceReporter {

    private final ConcurrentLinkedQueue<PerformanceEntry> entries = new ConcurrentLinkedQueue<PerformanceEntry>();
    private final String csvFilePath;

    public PerformanceReporter(String csvFilePath) {
        this.csvFilePath = csvFilePath;
        try {
            File csvFile = new File(csvFilePath);
            File parentDir = csvFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) throw new IOException("Failed to create dir");
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile, false))) {
                writer.println("No.,StartTimestamp,EndTimestamp,MessageType,Latency,StatusCode,RoomId");
            }
        } catch (IOException e) {
            System.err.println("Failed to initialize CSV file: " + e.getMessage());
        }
    }

    public void addEntry(PerformanceEntry entry) {
        entries.add(entry);
    }

    public void writeToCsv() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFilePath, true))) {
            int idx = 0;
            PerformanceEntry entry;
            while ((entry = entries.poll()) != null) {
                writer.println(entry.toCsvRow(idx++));
            }
        } catch (IOException e) {
            System.err.println("Error writing performance data to CSV file: " + e.getMessage());
        }
    }

    public void printStatistics(List<Long> latencies) {
        if (latencies.isEmpty()) {
            System.out.println("No successful requests to report statistics on.");
            return;
        }
        Collections.sort(latencies);
        long sum = latencies.stream().mapToLong(Long::longValue).sum();
        double mean = (double) sum / latencies.size();
        double median = latencies.size() > 1 ? latencies.get(latencies.size() / 2) : latencies.get(0);
        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);
        double p95 = latencies.get((int) (latencies.size() * 0.95));
        double p99 = latencies.get((int) (latencies.size() * 0.99));
        System.out.printf("Mean Response Time: %.2f ms%n", mean);
        System.out.printf("Median Response Time: %.2f ms%n", median);
        System.out.println("Min Response Time: " + min + " ms");
        System.out.println("Max Response Time: " + max + " ms");
        System.out.printf("95th Percentile: %.2f ms%n", p95);
        System.out.printf("99th Percentile: %.2f ms%n", p99);
    }
}