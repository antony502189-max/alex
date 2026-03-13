package com.alex.messenger.chat;

import com.alex.messenger.chat.dto.ChatReadEventResponse;
import com.alex.messenger.chat.dto.PinMessageEventResponse;
import com.alex.messenger.chat.dto.TypingEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatRealtimeService {

    private final SimpMessagingTemplate simpMessagingTemplate;

    public void publishReadEvent(ChatReadEventResponse event) {
        simpMessagingTemplate.convertAndSend("/topic/chats/" + event.chatId() + "/reads", event);
    }

    public void publishTypingEvent(TypingEventResponse event) {
        simpMessagingTemplate.convertAndSend("/topic/chats/" + event.chatId() + "/typing", event);
    }

    public void publishPinEvent(PinMessageEventResponse event) {
        simpMessagingTemplate.convertAndSend("/topic/chats/" + event.chatId() + "/pins", event);
    }
}
