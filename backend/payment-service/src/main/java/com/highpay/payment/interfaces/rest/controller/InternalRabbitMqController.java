package com.highpay.payment.interfaces.rest.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.highpay.payment.infrastructure.messaging.rabbitmq.DeadLetterRequeueService;

@RestController
@RequestMapping("/internal/rabbitmq")
public class InternalRabbitMqController {

    private final DeadLetterRequeueService deadLetterRequeueService;

    public InternalRabbitMqController(DeadLetterRequeueService deadLetterRequeueService) {
        this.deadLetterRequeueService = deadLetterRequeueService;
    }

    @PostMapping("/payment-created-dlq/requeue-one")
    public ResponseEntity<Map<String, Object>> requeueOnePaymentCreatedDeadLetter() {
        boolean requeued = deadLetterRequeueService.requeueOnePaymentCreatedMessage();

        return ResponseEntity.ok(Map.of(
                "queue", "payment-created-dlq",
                "requeued", requeued));
    }
}
