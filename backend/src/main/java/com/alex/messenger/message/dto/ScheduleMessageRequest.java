package com.alex.messenger.message.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ScheduleMessageRequest(
        UUID chatId,
        UUID recipientUserId,
        UUID topicId,
        UUID replyToMessageId,
        @Size(max = 4000) String text,
        @Size(max = 4000) String caption,
        String messageType,
        List<@NotNull @Valid MessageTextEntityPayload> entities,
        @Valid MessageLocationPayload location,
        @Valid MessageLiveLocationPayload liveLocation,
        @Valid MessageContactCardPayload contactCard,
        List<@NotNull UUID> attachmentIds,
        UUID stickerId,
        Boolean silent,
        UUID clientMessageId,
        Boolean disableLinkPreview,
        @NotNull @Future Instant scheduledAt
) {
    public ScheduleMessageRequest(
            UUID chatId,
            UUID recipientUserId,
            UUID topicId,
            UUID replyToMessageId,
            String text,
            String caption,
            String messageType,
            List<MessageTextEntityPayload> entities,
            MessageLocationPayload location,
            MessageContactCardPayload contactCard,
            List<UUID> attachmentIds,
            UUID stickerId,
            Boolean silent,
            UUID clientMessageId,
            Instant scheduledAt
    ) {
        this(
                chatId,
                recipientUserId,
                topicId,
                replyToMessageId,
                text,
                caption,
                messageType,
                entities,
                location,
                null,
                contactCard,
                attachmentIds,
                stickerId,
                silent,
                clientMessageId,
                null,
                scheduledAt
        );
    }

    @JsonIgnore
    @AssertTrue(message = "chatId or recipientUserId is required")
    public boolean isTargetSpecified() {
        return MessageRequestValidationSupport.hasTarget(chatId, recipientUserId);
    }

    @JsonIgnore
    @AssertTrue(message = "Message must contain text, attachments, sticker, or structured payload")
    public boolean hasPayload() {
        return MessageRequestValidationSupport.hasPayload(
                text,
                caption,
                location,
                liveLocation,
                contactCard,
                attachmentIds,
                stickerId
        );
    }

    @JsonIgnore
    @AssertTrue(message = "Location, live location, and contact card payloads cannot be combined")
    public boolean hasAtMostOneStructuredPayload() {
        return MessageRequestValidationSupport.hasAtMostOneStructuredPayload(location, liveLocation, contactCard);
    }

    @JsonIgnore
    @AssertTrue(message = "Service messages cannot be sent from the public API")
    public boolean isPublicMessageType() {
        return MessageRequestValidationSupport.isPublicMessageType(messageType);
    }

    @JsonIgnore
    @AssertTrue(message = "Structured message payload and messageType combination is invalid")
    public boolean hasValidStructuredPayloadUsage() {
        return MessageRequestValidationSupport.hasValidStructuredPayloadUsage(
                messageType,
                location,
                liveLocation,
                contactCard,
                attachmentIds,
                stickerId
        );
    }
}
