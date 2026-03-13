package com.alex.messenger.bot;

import com.alex.messenger.bot.dto.BotApiProfileResponse;
import com.alex.messenger.bot.dto.BotWebhookInfoResponse;
import com.alex.messenger.bot.dto.CreateDeveloperBotRequest;
import com.alex.messenger.bot.dto.DeveloperBotResponse;
import com.alex.messenger.bot.dto.IssuedBotTokenResponse;
import com.alex.messenger.bot.dto.UpdateBotWebhookRequest;
import com.alex.messenger.bot.dto.UpdateDeveloperBotRequest;
import com.alex.messenger.media.PhotoAccess;
import com.alex.messenger.media.ProfilePhotoService;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DeveloperBotService {

    private static final int BOT_PHONE_TOKEN_BYTES = 15;
    private static final int BOT_API_TOKEN_BYTES = 36;

    private final UserRepository userRepository;
    private final BotAccountRepository botAccountRepository;
    private final ProfilePhotoService profilePhotoService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public List<DeveloperBotResponse> listOwnedBots(UUID ownerUserId) {
        requireUser(ownerUserId);
        List<BotAccountEntity> accounts = botAccountRepository.findAllByOwnerUserIdOrderByCreatedAtDesc(ownerUserId);
        Map<UUID, UserEntity> botsById = userRepository.findAllById(
                        accounts.stream().map(BotAccountEntity::getBotUserId).toList()
                ).stream()
                .filter(UserEntity::isBot)
                .collect(Collectors.toMap(UserEntity::getId, user -> user));

        return accounts.stream()
                .map(account -> {
                    UserEntity bot = botsById.get(account.getBotUserId());
                    return bot != null ? toDeveloperBotResponse(bot, account) : null;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public IssuedBotTokenResponse createBot(UUID ownerUserId, CreateDeveloperBotRequest request) {
        UserEntity owner = requireUser(ownerUserId);
        String apiToken = generateBotApiToken();

        UserEntity bot = new UserEntity();
        bot.setPhoneNumber(generateBotPhoneNumber());
        bot.setDisplayName(normalizeRequired(request.displayName(), "Display name", 120));
        bot.setUsername(normalizeAndValidateBotUsername(null, request.username()));
        bot.setAbout(normalizeOptional(request.about(), 255));
        bot.setBot(true);
        bot.setBotDescription(normalizeOptional(request.description(), 255));
        bot.setBotSupportsInline(request.supportsInline());
        bot.setBotWebAppUrl(normalizeHttpUrl(request.webAppUrl(), "Mini app URL"));
        bot.setPhonePrivacy("NOBODY");
        bot.setLastSeenPrivacy("NOBODY");
        bot.setStoryPrivacy("NOBODY");
        bot.setLastSeenAt(Instant.now());

        UserEntity savedBot = userRepository.save(bot);

        BotAccountEntity account = new BotAccountEntity();
        account.setBotUserId(savedBot.getId());
        account.setOwnerUserId(owner.getId());
        account.setApiTokenHash(hash(apiToken));
        account.setApiTokenPrefix(tokenPrefix(apiToken));
        account.setTokenRotatedAt(Instant.now());
        BotAccountEntity savedAccount = botAccountRepository.save(account);

        return new IssuedBotTokenResponse(toDeveloperBotResponse(savedBot, savedAccount), apiToken);
    }

    @Transactional(readOnly = true)
    public DeveloperBotResponse getOwnedBot(UUID ownerUserId, UUID botUserId) {
        ManagedBot managedBot = requireOwnedBot(ownerUserId, botUserId);
        return toDeveloperBotResponse(managedBot.bot(), managedBot.account());
    }

    @Transactional
    public DeveloperBotResponse updateBot(UUID ownerUserId, UUID botUserId, UpdateDeveloperBotRequest request) {
        ManagedBot managedBot = requireOwnedBot(ownerUserId, botUserId);
        UserEntity bot = managedBot.bot();

        if (request.displayName() != null) {
            bot.setDisplayName(normalizeRequired(request.displayName(), "Display name", 120));
        }
        if (request.username() != null) {
            bot.setUsername(normalizeAndValidateBotUsername(bot.getId(), request.username()));
        }
        if (request.description() != null) {
            bot.setBotDescription(normalizeOptional(request.description(), 255));
        }
        if (request.about() != null) {
            bot.setAbout(normalizeOptional(request.about(), 255));
        }
        if (request.supportsInline() != null) {
            bot.setBotSupportsInline(request.supportsInline());
        }
        if (request.webAppUrl() != null) {
            bot.setBotWebAppUrl(normalizeHttpUrl(request.webAppUrl(), "Mini app URL"));
        }

        UserEntity savedBot = userRepository.save(bot);
        return toDeveloperBotResponse(savedBot, managedBot.account());
    }

    @Transactional
    public IssuedBotTokenResponse rotateToken(UUID ownerUserId, UUID botUserId) {
        ManagedBot managedBot = requireOwnedBot(ownerUserId, botUserId);
        String apiToken = generateBotApiToken();
        BotAccountEntity account = managedBot.account();
        account.setApiTokenHash(hash(apiToken));
        account.setApiTokenPrefix(tokenPrefix(apiToken));
        account.setTokenRotatedAt(Instant.now());
        account.setLastWebhookError(null);

        BotAccountEntity savedAccount = botAccountRepository.save(account);
        return new IssuedBotTokenResponse(toDeveloperBotResponse(managedBot.bot(), savedAccount), apiToken);
    }

    @Transactional
    public DeveloperBotResponse updateWebhook(UUID ownerUserId, UUID botUserId, UpdateBotWebhookRequest request) {
        ManagedBot managedBot = requireOwnedBot(ownerUserId, botUserId);
        BotAccountEntity savedAccount = updateWebhookInternal(managedBot.account(), request);
        return toDeveloperBotResponse(managedBot.bot(), savedAccount);
    }

    @Transactional
    public DeveloperBotResponse clearWebhook(UUID ownerUserId, UUID botUserId) {
        ManagedBot managedBot = requireOwnedBot(ownerUserId, botUserId);
        BotAccountEntity savedAccount = clearWebhookInternal(managedBot.account());
        return toDeveloperBotResponse(managedBot.bot(), savedAccount);
    }

    @Transactional(readOnly = true)
    public Optional<BotAccountEntity> authenticateApiToken(String presentedToken) {
        if (presentedToken == null) {
            return Optional.empty();
        }
        String normalized = presentedToken.trim();
        if (normalized.isBlank() || normalized.length() > 512) {
            return Optional.empty();
        }
        return botAccountRepository.findByApiTokenHash(hash(normalized));
    }

    @Transactional(readOnly = true)
    public BotApiProfileResponse getBotApiProfile(UUID botUserId) {
        ManagedBot managedBot = requireBot(botUserId);
        return new BotApiProfileResponse(
                managedBot.bot().getId(),
                managedBot.bot().getDisplayName(),
                managedBot.bot().getUsername(),
                managedBot.bot().getBotDescription(),
                managedBot.bot().getAbout(),
                managedBot.bot().isBotSupportsInline(),
                managedBot.bot().getBotWebAppUrl(),
                managedBot.account().getTokenRotatedAt()
        );
    }

    @Transactional(readOnly = true)
    public BotWebhookInfoResponse getBotWebhookInfo(UUID botUserId) {
        return toWebhookInfoResponse(requireBot(botUserId).account());
    }

    @Transactional
    public BotWebhookInfoResponse updateWebhookForBotApi(UUID botUserId, UpdateBotWebhookRequest request) {
        return toWebhookInfoResponse(updateWebhookInternal(requireBot(botUserId).account(), request));
    }

    @Transactional
    public BotWebhookInfoResponse clearWebhookForBotApi(UUID botUserId) {
        return toWebhookInfoResponse(clearWebhookInternal(requireBot(botUserId).account()));
    }

    private BotAccountEntity updateWebhookInternal(BotAccountEntity account, UpdateBotWebhookRequest request) {
        String webhookUrl = normalizeHttpUrl(request.webhookUrl(), "Webhook URL");
        if (webhookUrl == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook URL is required");
        }

        account.setWebhookUrl(webhookUrl);
        account.setWebhookEnabled(true);
        String normalizedSecret = normalizeOptional(request.secretToken(), 255);
        account.setWebhookSecretHash(normalizedSecret != null ? hash(normalizedSecret) : null);
        account.setWebhookSecretValue(normalizedSecret);
        account.setLastWebhookError(null);
        return botAccountRepository.save(account);
    }

    private BotAccountEntity clearWebhookInternal(BotAccountEntity account) {
        account.setWebhookUrl(null);
        account.setWebhookEnabled(false);
        account.setWebhookSecretHash(null);
        account.setWebhookSecretValue(null);
        account.setLastWebhookError(null);
        return botAccountRepository.save(account);
    }

    private ManagedBot requireOwnedBot(UUID ownerUserId, UUID botUserId) {
        BotAccountEntity account = botAccountRepository.findByBotUserIdAndOwnerUserId(botUserId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found"));
        UserEntity bot = userRepository.findByIdAndBotTrue(botUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found"));
        return new ManagedBot(bot, account);
    }

    private ManagedBot requireBot(UUID botUserId) {
        BotAccountEntity account = botAccountRepository.findById(botUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found"));
        UserEntity bot = userRepository.findByIdAndBotTrue(botUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found"));
        return new ManagedBot(bot, account);
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private DeveloperBotResponse toDeveloperBotResponse(UserEntity bot, BotAccountEntity account) {
        PhotoAccess photoAccess = profilePhotoService.buildPhotoAccess(
                bot.getPhotoStorageProvider(),
                bot.getPhotoBucketName(),
                bot.getPhotoObjectKey()
        );
        return new DeveloperBotResponse(
                bot.getId(),
                account.getOwnerUserId(),
                bot.getDisplayName(),
                bot.getUsername(),
                bot.getBotDescription(),
                bot.getAbout(),
                bot.isBotSupportsInline(),
                bot.getBotWebAppUrl(),
                photoAccess.photoUrl(),
                photoAccess.photoAccessExpiresAt(),
                account.getApiTokenPrefix(),
                account.getTokenRotatedAt(),
                account.getWebhookUrl(),
                account.isWebhookEnabled(),
                account.getWebhookSecretValue() != null || account.getWebhookSecretHash() != null,
                account.getLastWebhookDeliveryAt(),
                account.getLastWebhookError(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    private BotWebhookInfoResponse toWebhookInfoResponse(BotAccountEntity account) {
        return new BotWebhookInfoResponse(
                account.getBotUserId(),
                account.getWebhookUrl(),
                account.isWebhookEnabled(),
                account.getWebhookSecretValue() != null || account.getWebhookSecretHash() != null,
                account.getLastWebhookDeliveryAt(),
                account.getLastWebhookError(),
                account.getUpdatedAt()
        );
    }

    private String normalizeAndValidateBotUsername(UUID existingBotUserId, String username) {
        String normalized = normalizeRequired(username, "Bot username", 64).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]{4,64}") || !normalized.endsWith("bot")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bot username must end with 'bot' and match [a-z0-9_]{4,64}"
            );
        }
        userRepository.findByUsernameIgnoreCase(normalized)
                .filter(existing -> existingBotUserId == null || !existing.getId().equals(existingBotUserId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
                });
        return normalized;
    }

    private String normalizeRequired(String value, String fieldName, int maxLength) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is too long");
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

    private String normalizeHttpUrl(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > 512) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is too long");
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
            if (!uri.isAbsolute() || scheme == null || (!"http".equals(scheme) && !"https".equals(scheme))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be a valid http(s) URL");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be a valid http(s) URL");
        }
    }

    private String generateBotPhoneNumber() {
        for (int attempt = 0; attempt < 8; attempt++) {
            byte[] bytes = new byte[BOT_PHONE_TOKEN_BYTES];
            secureRandom.nextBytes(bytes);
            String candidate = "bot-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (candidate.length() <= 32 && userRepository.findByPhoneNumber(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to allocate bot identity");
    }

    private String generateBotApiToken() {
        byte[] bytes = new byte[BOT_API_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return "alexbot_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String tokenPrefix(String apiToken) {
        return apiToken.substring(0, Math.min(16, apiToken.length()));
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to hash bot secret", exception);
        }
    }

    private record ManagedBot(
            UserEntity bot,
            BotAccountEntity account
    ) {
    }
}
