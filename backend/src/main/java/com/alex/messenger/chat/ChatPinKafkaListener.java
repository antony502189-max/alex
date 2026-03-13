package com.alex.messenger.chat;

import com.alex.messenger.chat.dto.PinMessageEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatPinKafkaListener {

    private final ChatRealtimeService chatRealtimeService;

    @KafkaListener(
            topics = "${alex.kafka.chat-pin-events-topic}",
            containerFactory = "pinKafkaListenerContainerFactory"
    )
    public void listen(PinMessageEventResponse event) {
        chatRealtimeService.publishPinEvent(event);
    }
}
