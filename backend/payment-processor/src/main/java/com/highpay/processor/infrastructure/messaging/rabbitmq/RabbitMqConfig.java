package com.highpay.processor.infrastructure.messaging.rabbitmq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RabbitMqProperties.class)
public class RabbitMqConfig {

    @Bean
    DirectExchange paymentExchange(RabbitMqProperties properties) {
        return new DirectExchange(properties.paymentExchange(), true, false);
    }

    @Bean
    DirectExchange deadLetterExchange(RabbitMqProperties properties) {
        return new DirectExchange(properties.deadLetterExchange(), true, false);
    }

    @Bean
    Queue paymentCreatedQueue(RabbitMqProperties properties) {
        return QueueBuilder
                .durable(properties.paymentCreatedQueue())
                .withArgument("x-dead-letter-exchange", properties.deadLetterExchange())
                .withArgument("x-dead-letter-routing-key", properties.paymentCreatedDeadLetterRoutingKey())
                .build();
    }

    @Bean
    Queue paymentCreatedDeadLetterQueue(RabbitMqProperties properties) {
        return QueueBuilder
                .durable(properties.paymentCreatedDeadLetterQueue())
                .build();
    }

    @Bean
    Binding paymentCreatedBinding(
            Queue paymentCreatedQueue,
            DirectExchange paymentExchange,
            RabbitMqProperties properties) {

        return BindingBuilder
                .bind(paymentCreatedQueue)
                .to(paymentExchange)
                .with(properties.paymentCreatedRoutingKey());
    }

    @Bean
    Binding paymentCreatedDeadLetterBinding(
            Queue paymentCreatedDeadLetterQueue,
            DirectExchange deadLetterExchange,
            RabbitMqProperties properties) {

        return BindingBuilder
                .bind(paymentCreatedDeadLetterQueue)
                .to(deadLetterExchange)
                .with(properties.paymentCreatedDeadLetterRoutingKey());
    }
}