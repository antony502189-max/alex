package com.alex.messenger.bot;

import com.alex.messenger.bot.dto.BotCommandResponse;
import com.alex.messenger.bot.dto.BotInlineResultResponse;
import com.alex.messenger.bot.dto.BotSummaryResponse;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.crypto.ChatEncryptionService;
import com.alex.messenger.crypto.EncryptedPayload;
import com.alex.messenger.media.PhotoAccess;
import com.alex.messenger.media.ProfilePhotoService;
import com.alex.messenger.message.ChatMessagePublisher;
import com.alex.messenger.message.MessageContentCodec;
import com.alex.messenger.message.MessageEvent;
import com.alex.messenger.message.MessageLookupEntity;
import com.alex.messenger.message.MessageStorageService;
import com.alex.messenger.message.MessageTextContent;
import com.alex.messenger.message.expiration.MessageExpirationEntity;
import com.alex.messenger.message.expiration.MessageExpirationRepository;
import com.alex.messenger.shared.SearchQueryValidationSupport;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BotService {

    private static final String HELPER_BOT_USERNAME = "alex_helper_bot";
    private static final String ECHO_BOT_USERNAME = "alex_echo_bot";
    private static final Set<String> BUILT_IN_BOT_USERNAMES = Set.of(HELPER_BOT_USERNAME, ECHO_BOT_USERNAME);

    private final UserRepository userRepository;
    private final BotAccountRepository botAccountRepository;
    private final BotCommandService botCommandService;
    private final BotInlineResultCacheService botInlineResultCacheService;
    private final ChatService chatService;
    private final ProfilePhotoService profilePhotoService;
    private final MessageStorageService messageStorageService;
    private final MessageExpirationRepository messageExpirationRepository;
    private final ChatEncryptionService chatEncryptionService;
    private final MessageContentCodec messageContentCodec;
    private final ChatMessagePublisher chatMessagePublisher;

    @Transactional(readOnly = true)
    public List<BotSummaryResponse> listBots() {
        return userRepository.findAllByBotTrueOrderByDisplayNameAsc().stream()
                .map(bot -> {
                    PhotoAccess photoAccess = profilePhotoService.buildPhotoAccess(
                            bot.getPhotoStorageProvider(),
                            bot.getPhotoBucketName(),
                            bot.getPhotoObjectKey()
                    );
                    return new BotSummaryResponse(
                            bot.getId(),
                            bot.getDisplayName(),
                            bot.getUsername(),
                            bot.getBotDescription(),
                            bot.isBotSupportsInline(),
                            bot.getBotWebAppUrl(),
                            photoAccess.photoUrl(),
                            photoAccess.photoAccessExpiresAt()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BotCommandResponse> getCommands(UUID botUserId) {
        UserEntity bot = getBot(botUserId);
        return commandDefinitions(bot);
    }

    @Transactional(readOnly = true)
    public List<BotInlineResultResponse> getInlineResults(String username, String query) {
        UserEntity bot = getBotByUsername(username);
        return inlineResultDefinitions(bot, query);
    }

    @Transactional(readOnly = true)
    public InlineBotSelection resolveInlineResult(String username, String resultId, String query) {
        UserEntity bot = getBotByUsername(username);
        return inlineResultDefinitions(bot, query).stream()
                .filter(result -> result.resultId().equals(resultId))
                .findFirst()
                .map(result -> new InlineBotSelection(result.botUserId(), result.botUsername(), result.text()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inline result not found"));
    }

    @Transactional
    public void maybeReplyToDirectMessage(ChatEntity chat, UUID senderId, MessageLookupEntity incomingMessage) {
        if (!"DIRECT".equals(chat.getChatType())) {
            return;
        }

        UserEntity sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sender not found"));
        if (sender.isBot()) {
            return;
        }

        UUID peerUserId = chatService.getPeerUserId(chat, senderId);
        UserEntity bot = userRepository.findByIdAndBotTrue(peerUserId).orElse(null);
        if (bot == null || !isBuiltInBot(bot)) {
            return;
        }

        BotReply reply = buildReply(bot, chat, senderId, incomingMessage);
        if (reply == null || reply.text() == null || reply.text().isBlank()) {
            return;
        }

        MessageLookupEntity replyLookup = buildReplyMessage(chat, bot.getId(), incomingMessage, reply.text());
        List<UUID> recipientIds = chatService.getRecipientIds(chat, bot.getId());
        messageStorageService.save(replyLookup);
        syncExpiration(replyLookup);
        chatService.updateLastMessageAt(chat, replyLookup.getCreatedAt());
        chatService.incrementUnreadCounts(
                chat.getId(),
                bot.getId(),
                messageContentCodec.plain(reply.text()),
                incomingMessage.getSenderId()
        );
        chatMessagePublisher.publish(new MessageEvent(
                chat.getId(),
                replyLookup.getMessageId(),
                null,
                bot.getId(),
                recipientIds,
                null,
                null,
                null,
                null,
                null,
                replyLookup.getCreatedAt(),
                replyLookup.getCiphertext(),
                replyLookup.getNonce(),
                replyLookup.getKeyVersion(),
                replyLookup.getReplyToMessageId(),
                null,
                null,
                null,
                null,
                List.of(),
                replyLookup.getDeliveryStatus(),
                null,
                null,
                replyLookup.getExpiresAt(),
                null,
                null
        ));
    }

    private UserEntity getBot(UUID botUserId) {
        return userRepository.findByIdAndBotTrue(botUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found"));
    }

    private UserEntity getBotByUsername(String username) {
        String normalizedUsername = username == null ? "" : username.trim().replace("@", "");
        return userRepository.findByUsernameIgnoreCase(normalizedUsername)
                .filter(UserEntity::isBot)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found"));
    }

    private List<BotCommandResponse> commandDefinitions(UserEntity bot) {
        if (ECHO_BOT_USERNAME.equals(bot.getUsername())) {
            return List.of(
                    new BotCommandResponse("/start", "Open the echo bot menu"),
                    new BotCommandResponse("/help", "Show available echo bot commands"),
                    new BotCommandResponse("/echo", "Echo back the provided text"),
                    new BotCommandResponse("/id", "Show your user id and current chat id")
            );
        }

        if (!isBuiltInBot(bot)) {
            BotAccountEntity account = botAccountRepository.findById(bot.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found"));
            return botCommandService.fallbackCommandsForBot(account, bot.getBotWebAppUrl());
        }

        return List.of(
                new BotCommandResponse("/start", "Open the helper bot menu"),
                new BotCommandResponse("/help", "Show available helper commands"),
                new BotCommandResponse("/about", "Show what this built-in bot can do"),
                new BotCommandResponse("/id", "Show your user id and current chat id"),
                new BotCommandResponse("/time", "Show current server time")
        );
    }

    private List<BotInlineResultResponse> inlineResultDefinitions(UserEntity bot, String query) {
        String normalizedQuery = SearchQueryValidationSupport.normalizeOptional(query);
        if (!isBuiltInBot(bot)) {
            return botInlineResultCacheService.getCachedResults(bot.getId(), normalizedQuery).stream()
                    .map(result -> new BotInlineResultResponse(
                            result.resultId(),
                            result.botUserId(),
                            bot.getUsername(),
                            result.title(),
                            result.description(),
                            result.text()
                    ))
                    .toList();
        }
        if (ECHO_BOT_USERNAME.equals(bot.getUsername())) {
            String echoText = normalizedQuery.isBlank() ? "Echo Bot inline result" : normalizedQuery;
            return List.of(
                    new BotInlineResultResponse(
                            "echo-text",
                            bot.getId(),
                            bot.getUsername(),
                            "Send echo result",
                            normalizedQuery.isBlank() ? "Insert a default echo message" : "Insert the echoed text into chat",
                            echoText
                    )
            );
        }

        return List.of(
                new BotInlineResultResponse(
                        "helper-about",
                        bot.getId(),
                        bot.getUsername(),
                        "About Alex",
                        "Insert a short helper message about the messenger",
                        "Alex is a Telegram-style messenger with offline-first sync, media, channels, calls and secret chats."
                ),
                new BotInlineResultResponse(
                        "helper-time",
                        bot.getId(),
                        bot.getUsername(),
                        "Current server time",
                        "Insert a helper time message",
                        "Server time: %s".formatted(Instant.now())
                ),
                new BotInlineResultResponse(
                        "helper-query",
                        bot.getId(),
                        bot.getUsername(),
                        "Use query as helper note",
                        normalizedQuery.isBlank() ? "Insert a generic helper note" : "Insert the current inline query into chat",
                        normalizedQuery.isBlank()
                                ? "Inline helper note"
                                : "Helper note: %s".formatted(normalizedQuery)
                )
        );
    }

    private BotReply buildReply(
            UserEntity bot,
            ChatEntity chat,
            UUID senderId,
            MessageLookupEntity incomingMessage
    ) {
        if (!isBuiltInBot(bot)) {
            return null;
        }
        String incomingText = "";
        if (incomingMessage.getDeletedAt() == null) {
            MessageTextContent content = messageContentCodec.decode(chatEncryptionService.decrypt(
                    chat.getId(),
                    incomingMessage.getCiphertext(),
                    incomingMessage.getNonce(),
                    incomingMessage.getKeyVersion()
            ));
            incomingText = content.text() != null ? content.text().trim() : "";
        }

        boolean hasAttachments = incomingMessage.getAttachmentIds() != null && !incomingMessage.getAttachmentIds().isEmpty();
        if (ECHO_BOT_USERNAME.equals(bot.getUsername())) {
            return buildEchoReply(chat, senderId, incomingText, hasAttachments);
        }
        return buildHelperReply(chat, senderId, incomingText, hasAttachments);
    }

    private BotReply buildEchoReply(ChatEntity chat, UUID senderId, String incomingText, boolean hasAttachments) {
        String normalized = incomingText.trim();
        if (normalized.equals("/start") || normalized.equals("/help") || normalized.isBlank()) {
            return new BotReply("""
                    Echo Bot is active.

                    Commands:
                    /start - open the command menu
                    /help - show help
                    /echo <text> - mirror your text
                    /id - show current identifiers

                    Send any plain text message and I will echo it back.
                    """.strip());
        }
        if (normalized.startsWith("/echo")) {
            String echoed = normalized.substring("/echo".length()).trim();
            return new BotReply(echoed.isBlank() ? "Usage: /echo <text>" : echoed);
        }
        if (normalized.equals("/id")) {
            return new BotReply("Your user id: %s\nChat id: %s".formatted(senderId, chat.getId()));
        }
        if (normalized.startsWith("/")) {
            return new BotReply("Unknown command. Send /help to see what Echo Bot supports.");
        }
        if (hasAttachments && normalized.isBlank()) {
            return new BotReply("Echo Bot currently mirrors text only. Send text or use /echo <text>.");
        }
        return new BotReply(normalized);
    }

    private BotReply buildHelperReply(ChatEntity chat, UUID senderId, String incomingText, boolean hasAttachments) {
        String normalized = incomingText.trim();
        if (normalized.equals("/start") || normalized.equals("/help") || normalized.isBlank()) {
            return new BotReply("""
                    Alex Helper Bot is active.

                    Commands:
                    /start - open the command menu
                    /help - show help
                    /about - what this bot can do
                    /id - show your user id and chat id
                    /time - show current server time
                    """.strip());
        }
        if (normalized.equals("/about")) {
            return new BotReply("""
                    Alex Helper Bot is a built-in service bot.

                    It demonstrates Telegram-style bot chats, command menus and auto replies inside direct dialogs.
                    """.strip());
        }
        if (normalized.equals("/id")) {
            return new BotReply("Your user id: %s\nChat id: %s".formatted(senderId, chat.getId()));
        }
        if (normalized.equals("/time")) {
            return new BotReply("Server time: %s".formatted(Instant.now()));
        }
        if (normalized.startsWith("/")) {
            return new BotReply("Unknown command. Send /help to see available helper commands.");
        }
        if (hasAttachments && normalized.isBlank()) {
            return new BotReply("Helper Bot currently supports text commands only. Send /help to begin.");
        }
        return new BotReply("Send /help to see available commands.");
    }

    private MessageLookupEntity buildReplyMessage(
            ChatEntity chat,
            UUID botUserId,
            MessageLookupEntity incomingMessage,
            String text
    ) {
        UUID messageId = Uuids.timeBased();
        Instant createdAt = Instant.ofEpochMilli(Uuids.unixTimestamp(messageId));
        EncryptedPayload encryptedPayload = chatEncryptionService.encrypt(
                chat.getId(),
                messageContentCodec.encode(messageContentCodec.plain(text))
        );

        MessageLookupEntity lookup = new MessageLookupEntity();
        lookup.setMessageId(messageId);
        lookup.setChatId(chat.getId());
        lookup.setCreatedAt(createdAt);
        lookup.setSenderId(botUserId);
        lookup.setRecipientId(incomingMessage.getSenderId());
        lookup.setTopicId(null);
        lookup.setThreadRootMessageId(null);
        lookup.setDiscussionChatId(null);
        lookup.setDiscussionRootMessageId(null);
        lookup.setCiphertext(encryptedPayload.ciphertext());
        lookup.setNonce(encryptedPayload.nonce());
        lookup.setKeyVersion(encryptedPayload.keyVersion());
        lookup.setReplyToMessageId(incomingMessage.getMessageId());
        lookup.setForwardedFromChatId(null);
        lookup.setForwardedFromMessageId(null);
        lookup.setPollId(null);
        lookup.setStickerId(null);
        lookup.setAttachmentIds(List.of());
        lookup.setDeliveryStatus("SENT");
        lookup.setDeliveredAt(null);
        lookup.setReadAt(null);
        lookup.setExpiresAt(chat.getAutoDeleteSeconds() != null ? createdAt.plusSeconds(chat.getAutoDeleteSeconds()) : null);
        lookup.setEditedAt(null);
        lookup.setDeletedAt(null);
        return lookup;
    }

    private void syncExpiration(MessageLookupEntity lookup) {
        if (lookup.getExpiresAt() == null) {
            messageExpirationRepository.findById(lookup.getMessageId()).ifPresent(messageExpirationRepository::delete);
            return;
        }
        MessageExpirationEntity expiration = new MessageExpirationEntity();
        expiration.setMessageId(lookup.getMessageId());
        expiration.setChatId(lookup.getChatId());
        expiration.setExpiresAt(lookup.getExpiresAt());
        messageExpirationRepository.save(expiration);
    }

    private boolean isBuiltInBot(UserEntity bot) {
        return bot != null && BUILT_IN_BOT_USERNAMES.contains(bot.getUsername());
    }

    private record BotReply(String text) {
    }

    public record InlineBotSelection(
            UUID botUserId,
            String botUsername,
            String text
    ) {
    }
}
