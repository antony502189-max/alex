package com.alex.messenger.chat;

import com.alex.messenger.chat.dto.PinMessageEventResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatPinEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ChatPinEventPublisher.class);

    private final KafkaTemplate<String, PinMessageEventResponse> pinEventKafkaTemplate;

    @Value("${alex.kafka.chat-pin-events-topic}")
    private String pinEventsTopic;

    public void publish(PinMessageEventResponse event) {
        pinEventKafkaTemplate.send(pinEventsTopic, event.chatId().toString(), event)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to publish pin event for chat {}", event.chatId(), throwable);
                    }
                });
    }
}
