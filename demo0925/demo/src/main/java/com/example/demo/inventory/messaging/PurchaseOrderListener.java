package com.example.demo.inventory.messaging;

import com.example.demo.inventory.config.RabbitConfig;
import com.example.demo.inventory.service.SpringAiAgentService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class PurchaseOrderListener {

    @Autowired
    private SpringAiAgentService springAiAgentService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 1. Capture the event from Raspberry Pi and immediately pass it to a decoupled worker thread
     */
    @RabbitListener(queues = RabbitConfig.LOW_STOCK_QUEUE)
    public void receiveLowStockEvent(String message) {
        System.out.println("\n📬 [RabbitMQ Listener Thread] Captured event from Pi. Decoupling workflow...");

        // 💡 CRITICAL FIX: Run the AI Multi-Agent flow in an independent async task pool, completely free of AMQP constraints!
        CompletableFuture.runAsync(() -> {
            try {
                System.out.println("🚀 [Async Worker Thread] Starting clean isolation layer for Groq HTTP network pipeline...");

                // Execute the multi-turn agent debate smoothly inside its own isolated scope
                String finalPoDetails = springAiAgentService.runMultiAgentWorkflow(message);

                // Construct result payload
                String resultPayload = String.format("{\"status\":\"SPRING_AI_APPROVED\",\"purchaseOrderDetails\":\"%s\"}",
                        finalPoDetails.replace("\"", "\\\"").replace("\n", "\\n"));

                // Post the finalized decision back to RabbitMQ broker
                rabbitTemplate.convertAndSend(RabbitConfig.PURCHASE_ORDER_QUEUE, resultPayload);

            } catch (Exception e) {
                System.err.println("❌ Async workflow execution failed: " + e.getMessage());
            }
        });

        // The RabbitMQ listener returns instantly here, freeing up the broker pool context
    }

    /**
     * 2. Print final victory summary once transaction persistence triggers
     */
    @RabbitListener(queues = RabbitConfig.PURCHASE_ORDER_QUEUE)
    public void receiveFinalInvoice(String message) {
        if (message.contains("SPRING_AI_APPROVED")) {
            System.out.println("\n🎉 [SUCCESSFUL CLOSURE] Database node safely persisted AI-negotiated PO via isolated threads!");
        }
    }
}