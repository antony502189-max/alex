package com.alex.messenger.message;

import com.alex.messenger.message.dto.AcknowledgeMessageDeliveryRequest;
import com.alex.messenger.message.dto.AcknowledgeMessageDeliveryResponse;
import com.alex.messenger.shared.CurrentSession;
import com.alex.messenger.shared.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageDeliveryController {

    private final MessageDeliveryService messageDeliveryService;

    @PostMapping("/delivery")
    public ResponseEntity<AcknowledgeMessageDeliveryResponse> acknowledgeDelivery(
            @Valid @RequestBody AcknowledgeMessageDeliveryRequest request
    ) {
        return ResponseEntity.ok(
                messageDeliveryService.acknowledgeDelivery(CurrentUser.id(), CurrentSession.id(), request)
        );
    }
}
