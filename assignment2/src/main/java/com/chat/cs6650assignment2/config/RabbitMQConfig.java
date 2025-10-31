package com.chat.cs6650assignment2.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableRabbit
public class RabbitMQConfig {
    public static final String TOPIC_EXCHANGE_NAME = "chat.exchange";
    public static final String FANOUT_EXCHANGE_NAME = "chat.broadcast.exchange";// As per assignment
    public static final String QUEUE_NAME_PREFIX = "room.";
    public static final String ROUTING_KEY_PREFIX = "room.";
    private static final int NUMBER_OF_ROOMS = 20;

    // --- Configuration for Queue TTL and Limits as per assignment ---
    private static final int MESSAGE_TTL_MS = 360000;      // 1 hour
    private static final int MAX_QUEUE_LENGTH = 10000;      // Max 10,000 messages per room

    /**
     * The central topic exchange that receives all messages.
     */
    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(TOPIC_EXCHANGE_NAME);
    }

    /**
     * This bean programmatically creates all the required queues (room.1 to room.20)
     * and their corresponding bindings to the topic exchange.
     * It uses the Declarables wrapper to register them all with Spring AMQP.
     */
    @Bean
    public Declarables amqpDeclarables(TopicExchange topicExchange) {
        List<Declarable> declarables = new ArrayList<>();

        // 1. Declare the 20 durable room queues and their bindings to the TOPIC exchange
        for (int i = 1; i <= NUMBER_OF_ROOMS; i++) {
            String queueName = QUEUE_NAME_PREFIX + i;
            String routingKey = ROUTING_KEY_PREFIX + i;
            Queue queue = QueueBuilder.durable(queueName)
                    .withArgument("x-message-ttl", 360000)
                    .withArgument("x-max-length", 5000)
                    .build();
            declarables.add(queue);

            // Note: We use the 'topicExchange' passed in as an argument
            Binding binding = BindingBuilder.bind(queue).to(topicExchange).with(routingKey);
            declarables.add(binding);
        }

        return new Declarables(declarables);
    }

    @Bean
    public FanoutExchange fanoutExchange() {
        return new FanoutExchange(FANOUT_EXCHANGE_NAME);
    }

    @Bean
    public AnonymousQueue serverBroadcastQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}