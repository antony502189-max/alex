package com.alex.messenger.lawful;

import com.alex.messenger.message.dto.ChatMessageResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LawfulExportChecksumService {

    public String computeDirectExportChecksum(
            UUID exportId,
            UUID targetUserId,
            String operatorId,
            String reason,
            Instant fromInclusive,
            Instant toExclusive,
            boolean includeAttachmentsMetadata,
            List<ChatMessageResponse> messages
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, exportId);
            update(digest, targetUserId);
            update(digest, operatorId);
            update(digest, reason);
            update(digest, fromInclusive);
            update(digest, toExclusive);
            update(digest, includeAttachmentsMetadata);
            for (ChatMessageResponse message : messages) {
                update(digest, message.chatId());
                update(digest, message.messageId());
                update(digest, message.senderId());
                update(digest, message.recipientId());
                update(digest, message.createdAt());
                update(digest, message.deliveryStatus());
                update(digest, message.text());
                update(digest, message.messageType());
                update(digest, message.caption());
                update(digest, message.location());
                update(digest, message.contactCard());
                update(digest, message.serviceMessage());
                update(digest, message.deletedAt());
                update(digest, message.attachments());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to compute lawful export checksum",
                    exception
            );
        }
    }

    private void update(MessageDigest digest, Object value) {
        digest.update((value != null ? value.toString() : "<null>").getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
    }
}
