package com.alex.messenger.call;

import com.alex.messenger.call.dto.CallInboxEventResponse;
import com.alex.messenger.call.dto.CallSessionResponse;
import com.alex.messenger.call.dto.CallSignalEventResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CallRealtimeService {

    private final SimpMessagingTemplate simpMessagingTemplate;

    public void publishSessionEvent(UUID userId, String eventType, CallSessionResponse call) {
        simpMessagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/calls",
                new CallInboxEventResponse(eventType, call, null)
        );
    }

    public void publishSignalEvent(UUID userId, CallSignalEventResponse signal) {
        simpMessagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/calls",
                new CallInboxEventResponse("SIGNAL", null, signal)
        );
    }
}
