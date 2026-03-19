package com.alex.messenger.bot;

import com.alex.messenger.bot.dto.BotWebAppLaunchResponse;
import com.alex.messenger.bot.dto.BotWebAppContextResponse;
import com.alex.messenger.bot.dto.BotWebAppDataResponse;
import com.alex.messenger.bot.dto.BotWebAppQueryResponse;
import com.alex.messenger.bot.dto.BotApiAnswerWebAppQueryRequest;
import com.alex.messenger.bot.dto.CreateBotWebAppQueryRequest;
import com.alex.messenger.bot.dto.ResolveBotWebAppRequest;
import com.alex.messenger.bot.dto.SendBotWebAppDataRequest;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberRepository;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.SendMessageRequest;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BotWebAppService {

    private final UserRepository userRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatService chatService;
    private final MessageService messageService;
    private final BotWebAppEventRepository botWebAppEventRepository;
    private final BotWebAppQueryRepository botWebAppQueryRepository;
    private final BotUpdateRepository botUpdateRepository;
    private final ObjectMapper objectMapper;

    @Value("${alex.bots.web-app.init-secret:dev-bot-web-app-secret-change-me}")
    private String initSecret;

    @Value("${alex.bots.web-app.launch-ttl:PT10M}")
    private Duration launchTtl;

    @Value("${alex.bots.web-app.platform:alex-mobile}")
    private String platform;

    @Transactional(readOnly = true)
    public BotWebAppLaunchResponse createLaunch(
            UUID requesterId,
            UUID botUserId,
            UUID chatId,
            String startParameter
    ) {
        UserEntity bot = userRepository.findByIdAndBotTrue(botUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found"));
        if (bot.getBotWebAppUrl() == null || bot.getBotWebAppUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bot does not provide a mini app");
        }

        UserEntity requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (chatId != null) {
            chatService.getOwnedChat(requesterId, chatId);
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(launchTtl);
        String normalizedStartParameter = normalizeStartParameter(startParameter);
        String encodedPayload = encodePayload(new LaunchInitData(
                requesterId,
                requester.getDisplayName(),
                requester.getUsername(),
                bot.getId(),
                bot.getUsername(),
                chatId,
                normalizedStartParameter,
                issuedAt,
                expiresAt,
                platform
        ));
        String signature = sign(encodedPayload);

        Map<String, String> query = new LinkedHashMap<>();
        query.put("tgWebAppPlatform", platform);
        query.put("alexInitData", encodedPayload);
        query.put("alexSignature", signature);
        if (normalizedStartParameter != null) {
            query.put("startapp", normalizedStartParameter);
        }

        return new BotWebAppLaunchResponse(
                bot.getId(),
                bot.getUsername(),
                chatId,
                appendQuery(bot.getBotWebAppUrl(), query),
                issuedAt,
                expiresAt
        );
    }

    @Transactional(readOnly = true)
    public BotWebAppContextResponse resolveContext(UUID requesterId, ResolveBotWebAppRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mini app context payload is required");
        }
        LaunchInitData payload = decodeAndValidate(request.initData(), request.signature(), requesterId);
        return new BotWebAppContextResponse(
                payload.userId(),
                payload.displayName(),
                payload.username(),
                payload.botUserId(),
                payload.botUsername(),
                payload.chatId(),
                payload.startParameter(),
                payload.platform(),
                payload.issuedAt(),
                payload.expiresAt()
        );
    }

    @Transactional
    public BotWebAppDataResponse sendData(UUID requesterId, SendBotWebAppDataRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mini app data payload is required");
        }
        LaunchInitData payload = decodeAndValidate(request.initData(), request.signature(), requesterId);
        if (payload.chatId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mini app launch is not bound to a chat");
        }

        String normalizedData = normalizeRequired(request.data(), "Mini app data", 4096);
        String normalizedButtonText = normalizeOptional(request.buttonText(), 64);
        userRepository.findByIdAndBotTrue(payload.botUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found"));

        ChatEntity chat = chatService.getOwnedChat(requesterId, payload.chatId());
        ensureBotCanAccessChat(chat, requesterId, payload.botUserId());

        var message = messageService.sendInternalServiceMessage(
                requesterId,
                chat.getId(),
                "BOT_WEB_APP_DATA",
                buildServiceText(normalizedButtonText)
        );

        BotWebAppEventEntity event = new BotWebAppEventEntity();
        event.setBotUserId(payload.botUserId());
        event.setChatId(chat.getId());
        event.setMessageId(message.messageId());
        event.setFromUserId(requesterId);
        event.setStartParameter(payload.startParameter());
        event.setPlatform(payload.platform());
        event.setButtonText(normalizedButtonText);
        event.setPayloadData(normalizedData);
        BotWebAppEventEntity savedEvent = botWebAppEventRepository.save(event);

        BotUpdateEntity update = new BotUpdateEntity();
        update.setBotUserId(payload.botUserId());
        update.setChatId(chat.getId());
        update.setMessageId(message.messageId());
        update.setUpdateType("WEB_APP_DATA");
        update.setWebAppEventId(savedEvent.getId());
        botUpdateRepository.save(update);

        return new BotWebAppDataResponse(
                savedEvent.getId(),
                savedEvent.getBotUserId(),
                savedEvent.getChatId(),
                savedEvent.getMessageId(),
                savedEvent.getFromUserId(),
                savedEvent.getButtonText(),
                savedEvent.getPayloadData(),
                savedEvent.getStartParameter(),
                savedEvent.getPlatform(),
                savedEvent.getCreatedAt()
        );
    }

    @Transactional
    public BotWebAppQueryResponse createQuery(UUID requesterId, CreateBotWebAppQueryRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mini app query payload is required");
        }
        LaunchInitData payload = decodeAndValidate(request.initData(), request.signature(), requesterId);
        if (payload.chatId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mini app launch is not bound to a chat");
        }

        userRepository.findByIdAndBotTrue(payload.botUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found"));
        ChatEntity chat = chatService.getOwnedChat(requesterId, payload.chatId());
        ensureBotCanAccessChat(chat, requesterId, payload.botUserId());

        BotWebAppQueryEntity query = new BotWebAppQueryEntity();
        query.setBotUserId(payload.botUserId());
        query.setChatId(chat.getId());
        query.setFromUserId(requesterId);
        query.setStartParameter(payload.startParameter());
        query.setPlatform(payload.platform());
        query.setQueryText(normalizeOptional(request.queryText(), 4000));
        BotWebAppQueryEntity savedQuery = botWebAppQueryRepository.save(query);

        BotUpdateEntity update = new BotUpdateEntity();
        update.setBotUserId(payload.botUserId());
        update.setChatId(chat.getId());
        update.setUpdateType("WEB_APP_QUERY");
        update.setWebAppQueryId(savedQuery.getId());
        botUpdateRepository.save(update);

        return toWebAppQueryResponse(savedQuery);
    }

    @Transactional
    public com.alex.messenger.message.dto.ChatMessageResponse answerQuery(
            UUID botUserId,
            BotApiAnswerWebAppQueryRequest request
    ) {
        requireValidAnswerQueryRequest(request);
        BotWebAppQueryEntity query = botWebAppQueryRepository.findByIdAndBotUserId(request.webAppQueryId(), botUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mini app query not found"));
        if (query.getAnsweredAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mini app query has already been answered");
        }

        var response = messageService.sendMessage(
                botUserId,
                new SendMessageRequest(
                        query.getChatId(),
                        null,
                        null,
                        null,
                        request.text(),
                        request.caption(),
                        request.messageType(),
                        request.entities(),
                        request.location(),
                        request.contactCard(),
                        request.attachmentIds(),
                        request.stickerId(),
                        request.silent(),
                        null
                )
        );
        query.setAnsweredAt(Instant.now());
        query.setResultMessageId(response.messageId());
        botWebAppQueryRepository.save(query);
        return response;
    }

    private void requireValidAnswerQueryRequest(BotApiAnswerWebAppQueryRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mini app answer payload is required");
        }
        if (!request.hasPayload()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Message must contain text, attachments, sticker, or structured payload"
            );
        }
        if (!request.hasAtMostOneStructuredPayload()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Location and contact card payloads cannot be combined"
            );
        }
        if (!request.isPublicMessageType()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Service messages cannot be sent from the bot API"
            );
        }
        if (!request.hasValidStructuredPayloadUsage()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Structured message payload and messageType combination is invalid"
            );
        }
    }

    private String encodePayload(LaunchInitData payload) {
        try {
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(payload));
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to serialize bot web app payload",
                    exception
            );
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(initSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to sign bot web app payload",
                    exception
            );
        }
    }

    private String appendQuery(String baseUrl, Map<String, String> params) {
        StringBuilder builder = new StringBuilder(baseUrl);
        builder.append(baseUrl.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                builder.append('&');
            }
            first = false;
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append('=');
            builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private String normalizeStartParameter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!normalized.matches("[A-Za-z0-9_\\-]{1,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid mini app start parameter");
        }
        return normalized;
    }

    private LaunchInitData decodeAndValidate(String initData, String signature, UUID requesterId) {
        String normalizedInitData = normalizeRequired(initData, "Init data", 8192);
        String normalizedSignature = normalizeRequired(signature, "Signature", 512);
        String expectedSignature = sign(normalizedInitData);
        if (!MessageDigest.isEqual(
                normalizedSignature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid mini app signature");
        }
        LaunchInitData payload = decodePayload(normalizedInitData);
        if (!payload.userId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Mini app launch does not belong to the current user");
        }
        if (payload.expiresAt() == null || payload.expiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mini app launch has expired");
        }
        return payload;
    }

    private LaunchInitData decodePayload(String initData) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(initData);
            LaunchInitData payload = objectMapper.readValue(bytes, LaunchInitData.class);
            if (payload.userId() == null || payload.botUserId() == null || payload.issuedAt() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mini app payload is invalid");
            }
            return payload;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mini app payload is invalid");
        }
    }

    private void ensureBotCanAccessChat(ChatEntity chat, UUID requesterId, UUID botUserId) {
        String chatType = chat.getChatType() != null ? chat.getChatType().trim().toUpperCase(Locale.ROOT) : "";
        if ("DIRECT".equals(chatType)) {
            UUID peerUserId = chatService.getPeerUserId(chat, requesterId);
            if (!botUserId.equals(peerUserId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mini app bot does not match the direct chat peer");
            }
            return;
        }
        if (!chatMemberRepository.existsByIdChatIdAndIdUserId(chat.getId(), botUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mini app bot is not a member of this chat");
        }
    }

    private BotWebAppQueryResponse toWebAppQueryResponse(BotWebAppQueryEntity query) {
        return new BotWebAppQueryResponse(
                query.getId(),
                query.getBotUserId(),
                query.getChatId(),
                query.getFromUserId(),
                query.getStartParameter(),
                query.getPlatform(),
                query.getQueryText(),
                query.getCreatedAt(),
                query.getAnsweredAt(),
                query.getResultMessageId()
        );
    }

    private String buildServiceText(String buttonText) {
        String summary = buttonText != null
                ? "Mini app submitted data using \"%s\"".formatted(buttonText)
                : "Mini app submitted data";
        return summary.length() > 512 ? summary.substring(0, 512) : summary;
    }

    private String normalizeRequired(String value, String fieldName, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
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

    private record LaunchInitData(
            UUID userId,
            String displayName,
            String username,
            UUID botUserId,
            String botUsername,
            UUID chatId,
            String startParameter,
            Instant issuedAt,
            Instant expiresAt,
            String platform
    ) {
    }
}
