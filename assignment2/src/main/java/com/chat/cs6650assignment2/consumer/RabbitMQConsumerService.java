package com.chat.cs6650assignment2.consumer;

import com.chat.cs6650assignment2.model.QueueMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RabbitMQConsumerService implements ChannelAwareMessageListener {
    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConsumerService.class);

    private final Counter messagesProcessedCounter;
    private final Counter duplicateMessagesCounter;
    private final Counter failedMessagesCounter;

    private final ObjectMapper objectMapper;
    private final Set<String> processedMessageIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final BroadcastPublisherService broadcastPublisher;

    public RabbitMQConsumerService(ObjectMapper objectMapper, MeterRegistry meterRegistry, BroadcastPublisherService broadcastPublisher) {
        this.objectMapper = objectMapper;
        this.broadcastPublisher = broadcastPublisher;

        this.messagesProcessedCounter = Counter.builder("chat.messages.processed")
                .description("Total number of messages processed by the 'work' consumer")
                .register(meterRegistry);
        this.duplicateMessagesCounter = Counter.builder("chat.messages.duplicates")
                .register(meterRegistry);
        this.failedMessagesCounter = Counter.builder("chat.messages.failed")
                .register(meterRegistry);
    }

    @Override
    @Timed("chat.message.processing.time")
    public void onMessage(Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        QueueMessage payload = objectMapper.readValue(message.getBody(), QueueMessage.class);

        logger.info("Thread [{}] won race and consumed message {}. Re-publishing for broadcast.",
                Thread.currentThread().getName(), payload.getMessageId());

        if (!processedMessageIds.add(payload.getMessageId())) {
            logger.warn("Duplicate message received in work queue, ignoring: {}", payload.getMessageId());
            duplicateMessagesCounter.increment();
            channel.basicAck(tag, false);
            return;
        }

        try {
            broadcastPublisher.publishBroadcast(payload);

            logger.info("Successfully processed and re-published message {}. ACKing original.", payload.getMessageId());
            messagesProcessedCounter.increment();
            channel.basicAck(tag, false);

        } catch (Exception e) {
            logger.error("A critical error occurred while processing work message {}. NACKing and re-queueing.", payload.getMessageId(), e);
            failedMessagesCounter.increment();
            channel.basicNack(tag, false, true);
        }
    }
}