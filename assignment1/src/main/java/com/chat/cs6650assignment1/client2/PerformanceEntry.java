package com.chat.cs6650assignment1.client2;

public class PerformanceEntry {
    private final String startTimestamp;
    private final String endTimestamp;
    private final String messageType;
    private final long latency;
    private final int statusCode;
    private final int roomId;

    public PerformanceEntry(String startTimestamp, String endTimestamp, String messageType, long latency, int statusCode,
                            int roomId) {
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.messageType = messageType;
        this.latency = latency;
        this.statusCode = statusCode;
        this.roomId = roomId;
    }

    public String toCsvRow(int idx) {
        return String.join(",", Integer.toString(idx), startTimestamp, endTimestamp, messageType, String.valueOf(latency), String.valueOf(statusCode), Integer.toString(roomId));
    }
}
