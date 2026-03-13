package com.alex.messenger.chat;

import com.alex.messenger.chat.dto.ChatReadEventResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatReadEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ChatReadEventPublisher.class);

    private final KafkaTemplate<String, ChatReadEventResponse> readEventKafkaTemplate;

    @Value("${alex.kafka.chat-read-events-topic}")
    private String readEventsTopic;

    public void publish(ChatReadEventResponse event) {
        readEventKafkaTemplate.send(readEventsTopic, event.chatId().toString(), event)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to publish read event for chat {}", event.chatId(), throwable);
                    }
                });
    }
}
