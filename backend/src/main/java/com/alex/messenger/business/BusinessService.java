package com.alex.messenger.business;

import com.alex.messenger.business.dto.AssignBusinessOperatorRequest;
import com.alex.messenger.business.dto.BusinessChatTagPayload;
import com.alex.messenger.business.dto.BusinessChatTagResponse;
import com.alex.messenger.business.dto.BusinessHourSlotPayload;
import com.alex.messenger.business.dto.BusinessOperatorAssignmentResponse;
import com.alex.messenger.business.dto.BusinessProfileResponse;
import com.alex.messenger.business.dto.BusinessQuickReplyResponse;
import com.alex.messenger.business.dto.ReplaceBusinessChatTagsRequest;
import com.alex.messenger.business.dto.UpdateBusinessProfileRequest;
import com.alex.messenger.business.dto.UpsertBusinessQuickReplyRequest;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.SendMessageRequest;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BusinessService {

    private static final Set<String> VALID_DAYS = Set.of(
            "MONDAY",
            "TUESDAY",
            "WEDNESDAY",
            "THURSDAY",
            "FRIDAY",
            "SATURDAY",
            "SUNDAY"
    );

    private final BusinessProfileRepository businessProfileRepository;
    private final BusinessQuickReplyRepository businessQuickReplyRepository;
    private final BusinessChatTagRepository businessChatTagRepository;
    private final BusinessOperatorAssignmentRepository businessOperatorAssignmentRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public BusinessProfileResponse getProfile(UUID requesterId) {
        requireUser(requesterId);
        return toProfileResponse(getOrCreateProfile(requesterId));
    }

    @Transactional
    public BusinessProfileResponse updateProfile(UUID requesterId, UpdateBusinessProfileRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business profile payload is required");
        }
        requireUser(requesterId);
        BusinessProfileEntity profile = getOrCreateProfile(requesterId);
        if (request.greetingEnabled() != null) {
            profile.setGreetingEnabled(request.greetingEnabled());
        }
        if (request.awayEnabled() != null) {
            profile.setAwayEnabled(request.awayEnabled());
        }
        if (request.greetingMessage() != null) {
            profile.setGreetingMessage(normalizeOptional(request.greetingMessage(), 1000));
        }
        if (request.awayMessage() != null) {
            profile.setAwayMessage(normalizeOptional(request.awayMessage(), 1000));
        }
        if (request.businessHours() != null) {
            profile.setBusinessHoursJson(serializeBusinessHours(request.businessHours()));
        }
        if (request.timeZone() != null) {
            profile.setTimeZone(normalizeTimeZone(request.timeZone()));
        }
        return toProfileResponse(businessProfileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public List<BusinessQuickReplyResponse> listQuickReplies(UUID requesterId) {
        requireUser(requesterId);
        return businessQuickReplyRepository.findAllByUserIdOrderByPositionAscCreatedAtAsc(requesterId).stream()
                .map(this::toQuickReplyResponse)
                .toList();
    }

    @Transactional
    public List<BusinessQuickReplyResponse> upsertQuickReply(UUID requesterId, UpsertBusinessQuickReplyRequest request) {
        requireUser(requesterId);
        BusinessQuickReplyEntity entity = businessQuickReplyRepository.findAllByUserIdOrderByPositionAscCreatedAtAsc(requesterId)
                .stream()
                .filter(reply -> reply.getShortcut().equalsIgnoreCase(normalizeShortcut(request.shortcut())))
                .findFirst()
                .orElseGet(BusinessQuickReplyEntity::new);
        entity.setUserId(requesterId);
        entity.setShortcut(normalizeShortcut(request.shortcut()));
        entity.setMessageText(normalizeRequired(request.messageText(), "Quick reply text", 1000));
        entity.setPosition(request.position() != null ? Math.max(request.position(), 0) : nextQuickReplyPosition(requesterId));
        businessQuickReplyRepository.save(entity);
        return listQuickReplies(requesterId);
    }

    @Transactional
    public List<BusinessQuickReplyResponse> deleteQuickReply(UUID requesterId, UUID quickReplyId) {
        requireUser(requesterId);
        BusinessQuickReplyEntity entity = businessQuickReplyRepository.findByIdAndUserId(quickReplyId, requesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business quick reply not found"));
        businessQuickReplyRepository.delete(entity);
        return listQuickReplies(requesterId);
    }

    @Transactional
    public ChatMessageResponse sendQuickReply(UUID requesterId, UUID chatId, UUID quickReplyId) {
        chatService.getOwnedChat(requesterId, chatId);
        BusinessQuickReplyEntity quickReply = businessQuickReplyRepository.findByIdAndUserId(quickReplyId, requesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business quick reply not found"));
        return messageService.sendMessage(
                requesterId,
                new SendMessageRequest(chatId, null, null, null, quickReply.getMessageText(), null, null, null,
                        null, null, null, null, false, null)
        );
    }

    @Transactional(readOnly = true)
    public List<BusinessChatTagResponse> listChatTags(UUID requesterId, UUID chatId) {
        chatService.getOwnedChat(requesterId, chatId);
        return businessChatTagRepository.findAllByOwnerUserIdAndChatIdOrderByPositionAscCreatedAtAsc(requesterId, chatId)
                .stream()
                .map(this::toChatTagResponse)
                .toList();
    }

    @Transactional
    public List<BusinessChatTagResponse> replaceChatTags(
            UUID requesterId,
            UUID chatId,
            ReplaceBusinessChatTagsRequest request
    ) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business chat tags payload is required");
        }
        chatService.getOwnedChat(requesterId, chatId);
        List<BusinessChatTagPayload> tags = request.tags() == null ? List.of() : request.tags();
        Set<String> uniqueNames = new LinkedHashSet<>();
        List<BusinessChatTagEntity> entities = new ArrayList<>();
        for (int index = 0; index < tags.size(); index++) {
            BusinessChatTagPayload tag = tags.get(index);
            if (tag == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business chat tags must not contain null");
            }
            String normalizedName = normalizeTagName(tag.tagName());
            if (!uniqueNames.add(normalizedName.toLowerCase(Locale.ROOT))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate business chat tag");
            }
            BusinessChatTagEntity entity = new BusinessChatTagEntity();
            entity.setOwnerUserId(requesterId);
            entity.setChatId(chatId);
            entity.setTagName(normalizedName);
            entity.setColor(normalizeColor(tag.color()));
            entity.setPosition(index);
            entities.add(entity);
        }
        businessChatTagRepository.deleteAllByOwnerUserIdAndChatId(requesterId, chatId);
        if (!entities.isEmpty()) {
            businessChatTagRepository.saveAll(entities);
        }
        return listChatTags(requesterId, chatId);
    }

    @Transactional(readOnly = true)
    public BusinessOperatorAssignmentResponse getOperatorAssignment(UUID requesterId, UUID chatId) {
        chatService.getOwnedChat(requesterId, chatId);
        return businessOperatorAssignmentRepository.findByIdOwnerUserIdAndIdChatId(requesterId, chatId)
                .map(this::toOperatorAssignmentResponse)
                .orElse(null);
    }

    @Transactional
    public BusinessOperatorAssignmentResponse assignOperator(
            UUID requesterId,
            UUID chatId,
            AssignBusinessOperatorRequest request
    ) {
        chatService.getOwnedChat(requesterId, chatId);
        UserEntity operator = requireUser(request.operatorUserId());

        BusinessOperatorAssignmentEntity entity = businessOperatorAssignmentRepository
                .findByIdOwnerUserIdAndIdChatId(requesterId, chatId)
                .orElseGet(BusinessOperatorAssignmentEntity::new);
        entity.setId(new BusinessOperatorAssignmentId(requesterId, chatId));
        entity.setOperatorUserId(operator.getId());
        entity.setNote(normalizeOptional(request.note(), 255));
        return toOperatorAssignmentResponse(businessOperatorAssignmentRepository.save(entity));
    }

    @Transactional
    public void clearOperatorAssignment(UUID requesterId, UUID chatId) {
        chatService.getOwnedChat(requesterId, chatId);
        businessOperatorAssignmentRepository.deleteById(new BusinessOperatorAssignmentId(requesterId, chatId));
    }

    private int nextQuickReplyPosition(UUID userId) {
        return businessQuickReplyRepository.findAllByUserIdOrderByPositionAscCreatedAtAsc(userId).size();
    }

    private BusinessProfileEntity getOrCreateProfile(UUID userId) {
        return businessProfileRepository.findById(userId).orElseGet(() -> {
            BusinessProfileEntity entity = new BusinessProfileEntity();
            entity.setUserId(userId);
            return businessProfileRepository.save(entity);
        });
    }

    private BusinessProfileResponse toProfileResponse(BusinessProfileEntity entity) {
        return new BusinessProfileResponse(
                entity.getUserId(),
                Boolean.TRUE.equals(entity.getGreetingEnabled()),
                entity.getGreetingMessage(),
                Boolean.TRUE.equals(entity.getAwayEnabled()),
                entity.getAwayMessage(),
                deserializeBusinessHours(entity.getBusinessHoursJson()),
                entity.getTimeZone(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private BusinessQuickReplyResponse toQuickReplyResponse(BusinessQuickReplyEntity entity) {
        return new BusinessQuickReplyResponse(
                entity.getId(),
                entity.getShortcut(),
                entity.getMessageText(),
                entity.getPosition(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private BusinessChatTagResponse toChatTagResponse(BusinessChatTagEntity entity) {
        return new BusinessChatTagResponse(
                entity.getId(),
                entity.getChatId(),
                entity.getTagName(),
                entity.getColor(),
                entity.getPosition(),
                entity.getCreatedAt()
        );
    }

    private BusinessOperatorAssignmentResponse toOperatorAssignmentResponse(BusinessOperatorAssignmentEntity entity) {
        UserEntity operator = requireUser(entity.getOperatorUserId());
        return new BusinessOperatorAssignmentResponse(
                entity.getId().getOwnerUserId(),
                entity.getId().getChatId(),
                entity.getOperatorUserId(),
                operator.getDisplayName(),
                operator.getUsername(),
                entity.getNote(),
                entity.getAssignedAt(),
                entity.getUpdatedAt()
        );
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String normalizeRequired(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is too long");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value is too long");
        }
        return normalized;
    }

    private String normalizeShortcut(String value) {
        String normalized = normalizeRequired(value, "Quick reply shortcut", 64).toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.matches("[a-z0-9_\\-]{1,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quick reply shortcut is invalid");
        }
        return normalized;
    }

    private String normalizeTagName(String value) {
        return normalizeRequired(value, "Business tag", 64);
    }

    private String normalizeColor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!normalized.matches("^#[0-9a-fA-F]{6}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business tag color is invalid");
        }
        return normalized;
    }

    private String normalizeTimeZone(String value) {
        String normalized = normalizeRequired(value, "Time zone", 64);
        try {
            return ZoneId.of(normalized).getId();
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business time zone is invalid");
        }
    }

    private String serializeBusinessHours(List<BusinessHourSlotPayload> slots) {
        if (slots.stream().anyMatch(slot -> slot == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business hours must not contain null");
        }
        List<BusinessHourSlotPayload> normalized = slots.stream()
                .map(this::normalizeBusinessHourSlot)
                .toList();
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store business hours", exception);
        }
    }

    private List<BusinessHourSlotPayload> deserializeBusinessHours(String rawValue) {
        try {
            if (rawValue == null || rawValue.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(rawValue, new TypeReference<List<BusinessHourSlotPayload>>() { });
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to load business hours", exception);
        }
    }

    private BusinessHourSlotPayload normalizeBusinessHourSlot(BusinessHourSlotPayload slot) {
        String day = normalizeRequired(slot.dayOfWeek(), "Business day", 16).toUpperCase(Locale.ROOT);
        if (!VALID_DAYS.contains(day)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business day is invalid");
        }
        String from = normalizeRequired(slot.fromTime(), "Business opening time", 5);
        String to = normalizeRequired(slot.toTime(), "Business closing time", 5);
        try {
            LocalTime fromTime = LocalTime.parse(from);
            LocalTime toTime = LocalTime.parse(to);
            if (!fromTime.isBefore(toTime)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business opening time must be before closing time");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business hours time format must be HH:mm");
        }
        return new BusinessHourSlotPayload(day, from, to);
    }
}
