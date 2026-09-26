package com.example.demo.inventory.controller;

import com.example.demo.inventory.config.RabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @GetMapping("/trigger-replenishment")
    public String triggerReplenishment() {
        // Business logic context passed as JSON string
        String payload = "{\"productId\":\"PROD-9982\",\"name\":\"Enterprise SSD 2TB\",\"currentStock\":3,\"threshold\":10}";

        System.out.println("发送低库存事件到树莓派 400 RabbitMQ...");

        // Push payload to RabbitMQ. The thread returns instantly to the user
        rabbitTemplate.convertAndSend(RabbitConfig.LOW_STOCK_QUEUE, payload);

        return "AI Multi-Agent Replenishment engine successfully triggered in the background!";
    }
}
