package com.alex.messenger.business;

import com.alex.messenger.business.dto.BusinessHourSlotPayload;
import com.alex.messenger.feature.FeatureProperties;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BusinessAutomationService {

    private static final Duration GREETING_COOLDOWN = Duration.ofDays(7);
    private static final Duration AWAY_COOLDOWN = Duration.ofHours(1);

    private final FeatureProperties featureProperties;
    private final BusinessProfileRepository businessProfileRepository;
    private final BusinessChatAutomationStateRepository businessChatAutomationStateRepository;
    private final UserRepository userRepository;
    private final com.alex.messenger.chat.ChatService chatService;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handleIncomingDirectMessage(UUID chatId, UUID senderId, Instant messageCreatedAt) {
        if (!featureProperties.isBusiness()) {
            return;
        }
        UserEntity sender = userRepository.findById(senderId).orElse(null);
        if (sender == null || sender.isBot()) {
            return;
        }

        com.alex.messenger.chat.ChatEntity chat = chatService.getOwnedChat(senderId, chatId);
        if (!"DIRECT".equals(chat.getChatType())) {
            return;
        }

        UUID businessOwnerId = chatService.getPeerUserId(chat, senderId);
        BusinessProfileEntity profile = businessProfileRepository.findById(businessOwnerId).orElse(null);
        if (profile == null) {
            return;
        }

        BusinessChatAutomationStateId stateId = new BusinessChatAutomationStateId(businessOwnerId, chatId);
        BusinessChatAutomationStateEntity state = businessChatAutomationStateRepository.findById(stateId)
                .orElseGet(() -> {
                    BusinessChatAutomationStateEntity entity = new BusinessChatAutomationStateEntity();
                    entity.setId(stateId);
                    return entity;
                });

        boolean away = shouldSendAway(profile, state, messageCreatedAt);
        boolean greeting = !away && shouldSendGreeting(profile, state, messageCreatedAt);

        if (state.getFirstCustomerMessageAt() == null) {
            state.setFirstCustomerMessageAt(messageCreatedAt);
        }
        state.setLastCustomerMessageAt(messageCreatedAt);

        if (away) {
            var response = messageService.sendAutomatedBusinessMessage(
                    businessOwnerId,
                    chatId,
                    profile.getAwayMessage()
            );
            state.setLastAwaySentAt(response.createdAt());
            state.setLastAutoResponseAt(response.createdAt());
        } else if (greeting) {
            var response = messageService.sendAutomatedBusinessMessage(
                    businessOwnerId,
                    chatId,
                    profile.getGreetingMessage()
            );
            state.setLastGreetingSentAt(response.createdAt());
            state.setLastAutoResponseAt(response.createdAt());
        }

        businessChatAutomationStateRepository.save(state);
    }

    private boolean shouldSendGreeting(
            BusinessProfileEntity profile,
            BusinessChatAutomationStateEntity state,
            Instant messageCreatedAt
    ) {
        if (!Boolean.TRUE.equals(profile.getGreetingEnabled()) || isBlank(profile.getGreetingMessage())) {
            return false;
        }
        Instant previousCustomerMessageAt = state.getLastCustomerMessageAt();
        return previousCustomerMessageAt == null
                || previousCustomerMessageAt.isBefore(messageCreatedAt.minus(GREETING_COOLDOWN));
    }

    private boolean shouldSendAway(
            BusinessProfileEntity profile,
            BusinessChatAutomationStateEntity state,
            Instant messageCreatedAt
    ) {
        if (!Boolean.TRUE.equals(profile.getAwayEnabled()) || isBlank(profile.getAwayMessage())) {
            return false;
        }
        if (isWithinBusinessHours(profile, messageCreatedAt)) {
            return false;
        }
        Instant lastAwaySentAt = state.getLastAwaySentAt();
        return lastAwaySentAt == null || lastAwaySentAt.isBefore(messageCreatedAt.minus(AWAY_COOLDOWN));
    }

    private boolean isWithinBusinessHours(BusinessProfileEntity profile, Instant instant) {
        List<BusinessHourSlotPayload> slots = deserializeBusinessHours(profile.getBusinessHoursJson());
        if (slots.isEmpty()) {
            return false;
        }
        ZoneId zoneId = resolveZoneId(profile.getTimeZone());
        ZonedDateTime current = instant.atZone(zoneId);
        DayOfWeek currentDay = current.getDayOfWeek();
        DayOfWeek previousDay = currentDay.minus(1);
        LocalTime currentTime = current.toLocalTime();

        for (BusinessHourSlotPayload slot : slots) {
            DayOfWeek slotDay = parseDayOfWeek(slot.dayOfWeek());
            LocalTime fromTime = LocalTime.parse(slot.fromTime());
            LocalTime toTime = LocalTime.parse(slot.toTime());
            if (!fromTime.isAfter(toTime)) {
                if (slotDay == currentDay && !currentTime.isBefore(fromTime) && currentTime.isBefore(toTime)) {
                    return true;
                }
                continue;
            }
            if (slotDay == currentDay && !currentTime.isBefore(fromTime)) {
                return true;
            }
            if (slotDay == previousDay && currentTime.isBefore(toTime)) {
                return true;
            }
        }
        return false;
    }

    private List<BusinessHourSlotPayload> deserializeBusinessHours(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<List<BusinessHourSlotPayload>>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private ZoneId resolveZoneId(String value) {
        try {
            return ZoneId.of(value != null && !value.isBlank() ? value : "UTC");
        } catch (Exception exception) {
            return ZoneId.of("UTC");
        }
    }

    private DayOfWeek parseDayOfWeek(String value) {
        return DayOfWeek.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
