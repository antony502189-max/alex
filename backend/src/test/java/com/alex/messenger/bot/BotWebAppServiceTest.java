package com.alex.messenger.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.bot.dto.BotApiAnswerWebAppQueryRequest;
import com.alex.messenger.bot.dto.CreateBotWebAppQueryRequest;
import com.alex.messenger.bot.dto.ResolveBotWebAppRequest;
import com.alex.messenger.bot.dto.SendBotWebAppDataRequest;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberRepository;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BotWebAppServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatMemberRepository chatMemberRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private MessageService messageService;

    @Mock
    private BotWebAppEventRepository botWebAppEventRepository;

    @Mock
    private BotWebAppQueryRepository botWebAppQueryRepository;

    @Mock
    private BotUpdateRepository botUpdateRepository;

    private BotWebAppService botWebAppService;

    @BeforeEach
    void setUp() {
        botWebAppService = new BotWebAppService(
                userRepository,
                chatMemberRepository,
                chatService,
                messageService,
                botWebAppEventRepository,
                botWebAppQueryRepository,
                botUpdateRepository,
                new ObjectMapper().findAndRegisterModules()
        );
        ReflectionTestUtils.setField(botWebAppService, "initSecret", "test-bot-web-app-secret");
        ReflectionTestUtils.setField(botWebAppService, "launchTtl", Duration.ofMinutes(10));
        ReflectionTestUtils.setField(botWebAppService, "platform", "alex-mobile");
    }

    @Test
    void createLaunchCanBeResolvedBackIntoContext() {
        UUID requesterId = UUID.randomUUID();
        UUID botUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        when(userRepository.findByIdAndBotTrue(botUserId)).thenReturn(Optional.of(bot(botUserId, "samplebot")));
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user(requesterId, "Alice")));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "DIRECT"));

        var launch = botWebAppService.createLaunch(requesterId, botUserId, chatId, "promo");
        Map<String, String> query = parseQuery(launch.launchUrl());

        var context = botWebAppService.resolveContext(
                requesterId,
                new ResolveBotWebAppRequest(query.get("alexInitData"), query.get("alexSignature"))
        );

        assertThat(context.userId()).isEqualTo(requesterId);
        assertThat(context.botUserId()).isEqualTo(botUserId);
        assertThat(context.chatId()).isEqualTo(chatId);
        assertThat(context.startParameter()).isEqualTo("promo");
        assertThat(context.platform()).isEqualTo("alex-mobile");
    }

    @Test
    void sendDataCreatesEventAndBotUpdate() {
        UUID requesterId = UUID.randomUUID();
        UUID botUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        when(userRepository.findByIdAndBotTrue(botUserId)).thenReturn(Optional.of(bot(botUserId, "samplebot")));
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user(requesterId, "Alice")));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "DIRECT"));
        when(chatService.getPeerUserId(any(ChatEntity.class), org.mockito.ArgumentMatchers.eq(requesterId))).thenReturn(botUserId);
        when(messageService.sendInternalServiceMessage(requesterId, chatId, "BOT_WEB_APP_DATA", "Mini app submitted data using \"Send\""))
                .thenReturn(message(chatId, messageId, requesterId));
        when(botWebAppEventRepository.save(any(BotWebAppEventEntity.class))).thenAnswer(invocation -> {
            BotWebAppEventEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(Instant.parse("2026-03-14T13:00:00Z"));
            return entity;
        });
        when(botUpdateRepository.save(any(BotUpdateEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var launch = botWebAppService.createLaunch(requesterId, botUserId, chatId, "promo");
        Map<String, String> query = parseQuery(launch.launchUrl());

        var response = botWebAppService.sendData(
                requesterId,
                new SendBotWebAppDataRequest(query.get("alexInitData"), query.get("alexSignature"), "{\"theme\":\"dark\"}", "Send")
        );

        ArgumentCaptor<BotUpdateEntity> updateCaptor = ArgumentCaptor.forClass(BotUpdateEntity.class);
        verify(botUpdateRepository).save(updateCaptor.capture());
        BotUpdateEntity savedUpdate = updateCaptor.getValue();

        assertThat(response.botUserId()).isEqualTo(botUserId);
        assertThat(response.chatId()).isEqualTo(chatId);
        assertThat(response.messageId()).isEqualTo(messageId);
        assertThat(response.buttonText()).isEqualTo("Send");
        assertThat(response.data()).isEqualTo("{\"theme\":\"dark\"}");
        assertThat(savedUpdate.getUpdateType()).isEqualTo("WEB_APP_DATA");
        assertThat(savedUpdate.getBotUserId()).isEqualTo(botUserId);
        assertThat(savedUpdate.getChatId()).isEqualTo(chatId);
        assertThat(savedUpdate.getMessageId()).isEqualTo(messageId);
        assertThat(savedUpdate.getWebAppEventId()).isEqualTo(response.eventId());
    }

    @Test
    void createQueryCreatesPendingBotUpdateWithoutMessage() {
        UUID requesterId = UUID.randomUUID();
        UUID botUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        when(userRepository.findByIdAndBotTrue(botUserId)).thenReturn(Optional.of(bot(botUserId, "samplebot")));
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user(requesterId, "Alice")));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "DIRECT"));
        when(chatService.getPeerUserId(any(ChatEntity.class), eq(requesterId))).thenReturn(botUserId);
        when(botWebAppQueryRepository.save(any(BotWebAppQueryEntity.class))).thenAnswer(invocation -> {
            BotWebAppQueryEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(Instant.parse("2026-03-14T13:10:00Z"));
            return entity;
        });
        when(botUpdateRepository.save(any(BotUpdateEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var launch = botWebAppService.createLaunch(requesterId, botUserId, chatId, "promo");
        Map<String, String> query = parseQuery(launch.launchUrl());
        var response = botWebAppService.createQuery(
                requesterId,
                new CreateBotWebAppQueryRequest(query.get("alexInitData"), query.get("alexSignature"), "share this")
        );

        ArgumentCaptor<BotUpdateEntity> updateCaptor = ArgumentCaptor.forClass(BotUpdateEntity.class);
        verify(botUpdateRepository).save(updateCaptor.capture());
        BotUpdateEntity savedUpdate = updateCaptor.getValue();

        assertThat(response.botUserId()).isEqualTo(botUserId);
        assertThat(response.chatId()).isEqualTo(chatId);
        assertThat(response.queryText()).isEqualTo("share this");
        assertThat(savedUpdate.getUpdateType()).isEqualTo("WEB_APP_QUERY");
        assertThat(savedUpdate.getMessageId()).isNull();
        assertThat(savedUpdate.getWebAppQueryId()).isEqualTo(response.queryId());
    }

    @Test
    void answerQuerySendsMessageAndMarksQueryAnswered() {
        UUID botUserId = UUID.randomUUID();
        UUID queryId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        BotWebAppQueryEntity query = new BotWebAppQueryEntity();
        query.setId(queryId);
        query.setBotUserId(botUserId);
        query.setChatId(chatId);
        query.setFromUserId(UUID.randomUUID());
        query.setPlatform("alex-mobile");
        query.setCreatedAt(Instant.parse("2026-03-14T13:15:00Z"));

        when(botWebAppQueryRepository.findByIdAndBotUserId(queryId, botUserId)).thenReturn(Optional.of(query));
        when(messageService.sendMessage(eq(botUserId), any())).thenReturn(message(chatId, messageId, botUserId));
        when(botWebAppQueryRepository.save(any(BotWebAppQueryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = botWebAppService.answerQuery(
                botUserId,
                new BotApiAnswerWebAppQueryRequest(queryId, "result", null, null, List.of(), List.of(), null, false)
        );

        assertThat(response.messageId()).isEqualTo(messageId);
        assertThat(query.getAnsweredAt()).isNotNull();
        assertThat(query.getResultMessageId()).isEqualTo(messageId);
    }

    private UserEntity user(UUID userId, String displayName) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDisplayName(displayName);
        user.setUsername(displayName.toLowerCase());
        user.setPhoneNumber("+1234567890");
        return user;
    }

    private UserEntity bot(UUID botUserId, String username) {
        UserEntity bot = user(botUserId, "Bot");
        bot.setBot(true);
        bot.setUsername(username);
        bot.setBotWebAppUrl("https://example.com/app");
        return bot;
    }

    private ChatEntity chat(UUID chatId, String chatType) {
        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType(chatType);
        return chat;
    }

    private ChatMessageResponse message(UUID chatId, UUID messageId, UUID senderId) {
        return new ChatMessageResponse(
                chatId,
                messageId,
                null,
                senderId,
                "Alice",
                null,
                null,
                false,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                0,
                "",
                List.of(),
                "SERVICE_MESSAGE",
                null,
                false,
                null,
                null,
                null,
                Instant.parse("2026-03-14T13:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                "SENT",
                null,
                null,
                null,
                null,
                null
        );
    }

    private Map<String, String> parseQuery(String launchUrl) {
        String query = launchUrl.substring(launchUrl.indexOf('?') + 1);
        Map<String, String> result = new LinkedHashMap<>();
        Arrays.stream(query.split("&"))
                .map(part -> part.split("=", 2))
                .forEach(parts -> result.put(
                        URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : ""
                ));
        return result;
    }
}
