package com.chat.cs6650assignment1.server;

import com.chat.cs6650assignment1.model.ChatMessage;
import com.chat.cs6650assignment1.model.ServerResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Component
public class ChatWebSocketHandler  extends TextWebSocketHandler {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AtomicLong counter = new AtomicLong(0);

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomId = getRoomId(session);
        System.out.println("Connection established from " + session.getRemoteAddress() + " for room " + roomId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            ChatMessage chatMessage = objectMapper.readValue(message.getPayload(), ChatMessage.class);
            counter.incrementAndGet();
            System.out.println(counter+" Message received "+ chatMessage);
            validateMessage(chatMessage);
            String originalId = chatMessage.getMessageId();
            ServerResponse response = new ServerResponse("OK", Instant.now().toString(), message);
            response.setOriginalMessageId(originalId);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));

        } catch (Exception e) {
            ServerResponse errorResponse = new ServerResponse("ERROR", Instant.now().toString());
            errorResponse.setMessage(e.getMessage());
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        System.out.println("Connection closed from " + session.getRemoteAddress() + " with status " + status);
    }

    private String getRoomId(WebSocketSession session) {
        String path = Objects.requireNonNull(session.getUri()).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private void validateMessage(ChatMessage msg) {
        if (msg.getUserId() == null) throw new IllegalArgumentException("userId is required.");
        try {
            int userIdInt = Integer.parseInt(msg.getUserId());
            if (userIdInt < 1 || userIdInt > 100000) {
                throw new IllegalArgumentException("userId must be between 1 and 100000.");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("userId must be a valid integer string.");
        }

        if (msg.getUsername() == null || !USERNAME_PATTERN.matcher(msg.getUsername()).matches()) {
            throw new IllegalArgumentException("username must be 3-20 alphanumeric characters.");
        }

        if (msg.getMessage() == null || msg.getMessage().length() < 1 || msg.getMessage().length() > 500) {
            throw new IllegalArgumentException("message must be between 1 and 500 characters.");
        }

        if (msg.getTimestamp() == null) throw new IllegalArgumentException("timestamp is required.");
        try {
            Instant.parse(msg.getTimestamp());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("timestamp must be a valid ISO-8601 timestamp.");
        }

        if (msg.getMessageType() == null) {
            throw new IllegalArgumentException("messageType must be TEXT, JOIN, or LEAVE.");
        }
    }
}
