package com.alex.messenger.chat;

import com.alex.messenger.chat.dto.TypingEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TypingKafkaListener {

    private final ChatRealtimeService chatRealtimeService;

    @KafkaListener(
            topics = "${alex.kafka.chat-typing-events-topic}",
            containerFactory = "typingKafkaListenerContainerFactory"
    )
    public void listen(TypingEventResponse event) {
        chatRealtimeService.publishTypingEvent(event);
    }
}
