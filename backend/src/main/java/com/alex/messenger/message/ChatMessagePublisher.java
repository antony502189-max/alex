package com.alex.messenger.message;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(ChatMessagePublisher.class);

    private final KafkaTemplate<String, MessageEvent> kafkaTemplate;

    @Value("${alex.kafka.chat-messages-topic}")
    private String chatMessagesTopic;

    public void publish(MessageEvent event) {
        kafkaTemplate.send(chatMessagesTopic, event.chatId().toString(), event)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to publish message {} for chat {}", event.messageId(), event.chatId(), throwable);
                    }
                });
    }
}
