package com.alex.messenger.chat;

import com.alex.messenger.chat.dto.TypingEventResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TypingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TypingEventPublisher.class);

    private final KafkaTemplate<String, TypingEventResponse> typingEventKafkaTemplate;

    @Value("${alex.kafka.chat-typing-events-topic}")
    private String typingEventsTopic;

    public void publish(TypingEventResponse event) {
        typingEventKafkaTemplate.send(typingEventsTopic, event.chatId().toString(), event)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to publish typing event for chat {}", event.chatId(), throwable);
                    }
                });
    }
}
