package com.alex.messenger.bot.dto;

import com.alex.messenger.message.dto.MessageContactCardPayload;
import com.alex.messenger.message.dto.MessageLocationPayload;
import com.alex.messenger.message.dto.MessageTextEntityPayload;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BotApiSendMessageRequest(
        UUID chatId,
        UUID recipientUserId,
        UUID topicId,
        UUID replyToMessageId,
        @Size(max = 4000) String text,
        @Size(max = 4000) String caption,
        String messageType,
        List<@NotNull @Valid MessageTextEntityPayload> entities,
        @Valid MessageLocationPayload location,
        @Valid MessageContactCardPayload contactCard,
        List<@NotNull UUID> attachmentIds,
        UUID stickerId,
        Boolean silent,
        UUID clientMessageId,
        List<@NotNull @Valid BotApiMessageActionRequest> actions
) {

    @JsonIgnore
    @AssertTrue(message = "chatId or recipientUserId is required")
    public boolean hasTarget() {
        return chatId != null || recipientUserId != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Message must contain text, attachments, sticker, or structured payload")
    public boolean hasPayload() {
        return BotSendMessageValidationSupport.hasPayload(
                text,
                caption,
                location,
                contactCard,
                attachmentIds,
                stickerId
        );
    }

    @JsonIgnore
    @AssertTrue(message = "Location and contact card payloads cannot be combined")
    public boolean hasAtMostOneStructuredPayload() {
        return BotSendMessageValidationSupport.hasAtMostOneStructuredPayload(location, contactCard);
    }

    @JsonIgnore
    @AssertTrue(message = "Service messages cannot be sent from the bot API")
    public boolean isPublicMessageType() {
        return BotSendMessageValidationSupport.isPublicMessageType(messageType);
    }

    @JsonIgnore
    @AssertTrue(message = "Structured message payload and messageType combination is invalid")
    public boolean hasValidStructuredPayloadUsage() {
        return BotSendMessageValidationSupport.hasValidStructuredPayloadUsage(
                messageType,
                location,
                contactCard,
                attachmentIds,
                stickerId
        );
    }
}
