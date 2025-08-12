package com.example.digital.human.rabbitmq;

import jakarta.annotation.Resource;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;

@Service
public class MessageService {
    @Resource
    private AmqpTemplate amqpTemplate;

    //rabbitmq发送消息
    public void sendMessage(String message) {
        amqpTemplate.convertAndSend("bootDirectExchange", "bootDirectRoutingKey", message);
    }
}
