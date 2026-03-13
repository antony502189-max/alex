package com.alex.messenger.message;

import com.alex.messenger.message.dto.MessageTextEntityPayload;
import com.alex.messenger.message.dto.MessageContactCardPayload;
import com.alex.messenger.message.dto.MessageLocationPayload;
import com.alex.messenger.message.dto.MessageServicePayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MessageContentCodec {

    private static final int PAYLOAD_VERSION = 3;
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "BOLD",
            "ITALIC",
            "UNDERLINE",
            "STRIKETHROUGH",
            "SPOILER",
            "CODE",
            "PRE",
            "URL",
            "TEXT_LINK",
            "MENTION",
            "MENTION_NAME",
            "HASHTAG",
            "BOT_COMMAND",
            "CASHTAG",
            "PHONE",
            "EMAIL",
            "CUSTOM_EMOJI"
    );
    private static final Set<String> SUPPORTED_MESSAGE_TYPES = Set.of(
            "TEXT",
            "LOCATION",
            "CONTACT_CARD",
            "SERVICE_MESSAGE"
    );

    private final ObjectMapper objectMapper;

    public MessageContentCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MessageTextContent plain(String text) {
        return normalize(text, List.of());
    }

    public MessageTextContent normalize(String text, List<MessageTextEntityPayload> entities) {
        return normalize(text, entities, null, null, null, null, null, false);
    }

    public MessageTextContent normalize(
            String text,
            List<MessageTextEntityPayload> entities,
            String messageType,
            String caption,
            MessageLocationPayload location,
            MessageContactCardPayload contactCard,
            MessageServicePayload serviceMessage,
            Boolean silent
    ) {
        String normalizedText = text != null ? text : "";
        List<MessageTextEntityPayload> normalizedEntities = normalizeEntities(normalizedText, entities);
        String normalizedMessageType = normalizeMessageType(messageType);
        MessageLocationPayload normalizedLocation = normalizeLocation(location);
        MessageContactCardPayload normalizedContactCard = normalizeContactCard(contactCard);
        MessageServicePayload normalizedServiceMessage = normalizeServiceMessage(serviceMessage);
        String normalizedCaption = trimToNull(caption);
        boolean normalizedSilent = Boolean.TRUE.equals(silent);

        if ("LOCATION".equals(normalizedMessageType) && normalizedLocation == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location payload is required");
        }
        if ("CONTACT_CARD".equals(normalizedMessageType) && normalizedContactCard == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact card payload is required");
        }
        if ("SERVICE_MESSAGE".equals(normalizedMessageType) && normalizedServiceMessage == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service message payload is required");
        }

        return new MessageTextContent(
                normalizedText,
                normalizedEntities,
                normalizedMessageType,
                normalizedCaption,
                normalizedLocation,
                normalizedContactCard,
                normalizedServiceMessage,
                normalizedSilent
        );
    }

    public String encode(MessageTextContent content) {
        MessageTextContent normalized = normalize(
                content.text(),
                content.entities(),
                content.messageType(),
                content.caption(),
                content.location(),
                content.contactCard(),
                content.serviceMessage(),
                content.silent()
        );
        try {
            return objectMapper.writeValueAsString(
                    new StoredMessagePayload(
                            PAYLOAD_VERSION,
                            normalized.text(),
                            normalized.entities(),
                            normalized.messageType(),
                            normalized.caption(),
                            normalized.location(),
                            normalized.contactCard(),
                            normalized.serviceMessage(),
                            normalized.silent()
                    )
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to serialize message payload",
                    exception
            );
        }
    }

    public MessageTextContent decode(String rawPayload) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            return new MessageTextContent("", List.of());
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            if (!root.isObject()) {
                return new MessageTextContent(rawPayload, List.of());
            }

            int version = root.path("version").asInt(-1);
            if (version != 1 && version != 2 && version != PAYLOAD_VERSION) {
                return new MessageTextContent(rawPayload, List.of());
            }

            String text = root.path("text").isTextual() ? root.path("text").asText() : "";
            List<MessageTextEntityPayload> entities = new ArrayList<>();
            if (root.path("entities").isArray()) {
                for (JsonNode node : root.path("entities")) {
                    if (!node.isObject()) {
                        continue;
                    }
                    String type = node.path("type").isTextual() ? node.path("type").asText() : "";
                    int offset = node.path("offset").isInt() ? node.path("offset").asInt() : -1;
                    int length = node.path("length").isInt() ? node.path("length").asInt() : -1;
                    String value = node.path("value").isTextual() ? node.path("value").asText() : null;
                    java.util.UUID userId = node.path("userId").isTextual()
                            ? java.util.UUID.fromString(node.path("userId").asText())
                            : null;
                    if (
                            SUPPORTED_TYPES.contains(type) &&
                            offset >= 0 &&
                            length > 0 &&
                            offset + length <= text.length()
                    ) {
                        entities.add(new MessageTextEntityPayload(type, offset, length, value, userId));
                    }
                }
            }
            if (version == 1) {
                return normalize(text, entities);
            }

            String messageType = root.path("messageType").isTextual() ? root.path("messageType").asText() : null;
            String caption = root.path("caption").isTextual() ? root.path("caption").asText() : null;
            MessageLocationPayload location = root.path("location").isObject()
                    ? objectMapper.treeToValue(root.path("location"), MessageLocationPayload.class)
                    : null;
            MessageContactCardPayload contactCard = root.path("contactCard").isObject()
                    ? objectMapper.treeToValue(root.path("contactCard"), MessageContactCardPayload.class)
                    : null;
            MessageServicePayload serviceMessage = root.path("serviceMessage").isObject()
                    ? objectMapper.treeToValue(root.path("serviceMessage"), MessageServicePayload.class)
                    : null;
            boolean silent = root.path("silent").asBoolean(false);
            return normalize(text, entities, messageType, caption, location, contactCard, serviceMessage, silent);
        } catch (Exception ignored) {
            return new MessageTextContent(rawPayload, List.of());
        }
    }

    public String buildSearchText(MessageTextContent content) {
        if (content == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (!content.text().isBlank()) {
            parts.add(content.text());
        }
        if (content.caption() != null && !content.caption().isBlank() && !content.caption().equals(content.text())) {
            parts.add(content.caption());
        }
        if (content.location() != null) {
            if (content.location().title() != null && !content.location().title().isBlank()) {
                parts.add(content.location().title());
            }
            if (content.location().address() != null && !content.location().address().isBlank()) {
                parts.add(content.location().address());
            }
        }
        if (content.contactCard() != null) {
            if (content.contactCard().firstName() != null && !content.contactCard().firstName().isBlank()) {
                parts.add(content.contactCard().firstName());
            }
            if (content.contactCard().lastName() != null && !content.contactCard().lastName().isBlank()) {
                parts.add(content.contactCard().lastName());
            }
            if (content.contactCard().phoneNumber() != null && !content.contactCard().phoneNumber().isBlank()) {
                parts.add(content.contactCard().phoneNumber());
            }
        }
        if (content.serviceMessage() != null && content.serviceMessage().text() != null && !content.serviceMessage().text().isBlank()) {
            parts.add(content.serviceMessage().text());
        }
        return String.join(" ", parts);
    }

    private List<MessageTextEntityPayload> normalizeEntities(String text, List<MessageTextEntityPayload> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<MessageTextEntityPayload> normalized = new ArrayList<>();
        for (MessageTextEntityPayload entity : entities) {
            if (entity == null) {
                continue;
            }
            String type = entity.type() != null ? entity.type().trim().toUpperCase() : "";
            int offset = entity.offset();
            int length = entity.length();
            if (!SUPPORTED_TYPES.contains(type)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported message entity type");
            }
            if (offset < 0 || length <= 0 || offset + length > text.length()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message entity range is invalid");
            }
            String value = trimToNull(entity.value());
            java.util.UUID userId = entity.userId();
            if ("TEXT_LINK".equals(type) && value == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TEXT_LINK requires a value");
            }
            if ("CUSTOM_EMOJI".equals(type) && value == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CUSTOM_EMOJI requires a value");
            }
            if ("MENTION_NAME".equals(type) && userId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MENTION_NAME requires a userId");
            }
            if (value == null && Set.of("URL", "MENTION", "HASHTAG", "BOT_COMMAND", "CASHTAG", "PHONE", "EMAIL").contains(type)) {
                value = text.substring(offset, offset + length);
            }
            String dedupeKey = type + ":" + offset + ":" + length;
            if (seen.add(dedupeKey)) {
                normalized.add(new MessageTextEntityPayload(type, offset, length, value, userId));
            }
        }

        normalized.sort(
                Comparator.comparingInt(MessageTextEntityPayload::offset)
                        .thenComparingInt(MessageTextEntityPayload::length)
                        .thenComparing(MessageTextEntityPayload::type)
        );
        return List.copyOf(normalized);
    }

    private String normalizeMessageType(String messageType) {
        String normalizedMessageType = messageType != null ? messageType.trim().toUpperCase() : "";
        if (normalizedMessageType.isBlank()) {
            return null;
        }
        if (!SUPPORTED_MESSAGE_TYPES.contains(normalizedMessageType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported message type");
        }
        return normalizedMessageType;
    }

    private MessageLocationPayload normalizeLocation(MessageLocationPayload location) {
        if (location == null) {
            return null;
        }
        Double latitude = location.latitude();
        Double longitude = location.longitude();
        if (latitude == null || longitude == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location coordinates are required");
        }
        if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location coordinates are invalid");
        }
        return new MessageLocationPayload(
                latitude,
                longitude,
                trimToNull(location.title()),
                trimToNull(location.address())
        );
    }

    private MessageContactCardPayload normalizeContactCard(MessageContactCardPayload contactCard) {
        if (contactCard == null) {
            return null;
        }
        String firstName = trimToNull(contactCard.firstName());
        String lastName = trimToNull(contactCard.lastName());
        String phoneNumber = trimToNull(contactCard.phoneNumber());
        String vcard = trimToNull(contactCard.vcard());
        if (firstName == null && lastName == null && phoneNumber == null && contactCard.userId() == null && vcard == null) {
            return null;
        }
        if (firstName == null && phoneNumber == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact card must include a name or phone number");
        }
        return new MessageContactCardPayload(firstName, lastName, phoneNumber, contactCard.userId(), vcard);
    }

    private MessageServicePayload normalizeServiceMessage(MessageServicePayload serviceMessage) {
        if (serviceMessage == null) {
            return null;
        }
        String serviceType = trimToNull(serviceMessage.serviceType());
        String text = trimToNull(serviceMessage.text());
        if (serviceType == null && text == null) {
            return null;
        }
        return new MessageServicePayload(serviceType, text);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record StoredMessagePayload(
            int version,
            String text,
            List<MessageTextEntityPayload> entities,
            String messageType,
            String caption,
            MessageLocationPayload location,
            MessageContactCardPayload contactCard,
            MessageServicePayload serviceMessage,
            boolean silent
    ) {
    }
}
