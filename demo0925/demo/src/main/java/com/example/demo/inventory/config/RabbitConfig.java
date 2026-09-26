package com.example.demo.inventory.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String LOW_STOCK_QUEUE = "inventory.low_stock.queue";
    public static final String PURCHASE_ORDER_QUEUE = "inventory.purchase_order.queue";

    @Bean
    public Queue lowStockQueue() {
        // durable = true so messages survive broker restarts
        return new Queue(LOW_STOCK_QUEUE, true);
    }

    @Bean
    public Queue purchaseOrderQueue() {
        return new Queue(PURCHASE_ORDER_QUEUE, true);
    }
}
