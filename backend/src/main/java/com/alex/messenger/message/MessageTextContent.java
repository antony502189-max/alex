package com.alex.messenger.message;

import com.alex.messenger.message.dto.MessageTextEntityPayload;
import com.alex.messenger.message.dto.MessageContactCardPayload;
import com.alex.messenger.message.dto.MessageLiveLocationPayload;
import com.alex.messenger.message.dto.MessageLocationPayload;
import com.alex.messenger.message.dto.MessageServicePayload;
import java.util.List;

public record MessageTextContent(
        String text,
        List<MessageTextEntityPayload> entities,
        String messageType,
        String caption,
        MessageLocationPayload location,
        MessageLiveLocationPayload liveLocation,
        MessageContactCardPayload contactCard,
        MessageServicePayload serviceMessage,
        boolean silent
) {
    public MessageTextContent(String text, List<MessageTextEntityPayload> entities) {
        this(text, entities, null, null, null, null, null, null, false);
    }

    public MessageTextContent(
            String text,
            List<MessageTextEntityPayload> entities,
            String messageType,
            String caption,
            MessageLocationPayload location,
            MessageContactCardPayload contactCard,
            MessageServicePayload serviceMessage,
            boolean silent
    ) {
        this(text, entities, messageType, caption, location, null, contactCard, serviceMessage, silent);
    }
}
