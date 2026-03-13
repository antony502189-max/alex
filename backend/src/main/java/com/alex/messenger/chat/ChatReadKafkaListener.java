package com.alex.messenger.chat;

import com.alex.messenger.chat.dto.ChatReadEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatReadKafkaListener {

    private final ChatRealtimeService chatRealtimeService;

    @KafkaListener(
            topics = "${alex.kafka.chat-read-events-topic}",
            containerFactory = "readKafkaListenerContainerFactory"
    )
    public void listen(ChatReadEventResponse event) {
        chatRealtimeService.publishReadEvent(event);
    }
}
