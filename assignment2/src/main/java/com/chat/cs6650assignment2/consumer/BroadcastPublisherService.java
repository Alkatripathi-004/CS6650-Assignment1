package com.chat.cs6650assignment2.consumer;

import com.chat.cs6650assignment2.config.RabbitMQConfig;
import com.chat.cs6650assignment2.model.QueueMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class BroadcastPublisherService {

    private final RabbitTemplate rabbitTemplate;

    public BroadcastPublisherService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishBroadcast(QueueMessage message) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.FANOUT_EXCHANGE_NAME, "", message);
    }
}