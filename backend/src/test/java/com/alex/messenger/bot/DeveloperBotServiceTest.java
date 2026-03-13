package com.alex.messenger.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.alex.messenger.bot.dto.CreateDeveloperBotRequest;
import com.alex.messenger.bot.dto.IssuedBotTokenResponse;
import com.alex.messenger.bot.dto.UpdateBotWebhookRequest;
import com.alex.messenger.bot.dto.UpdateDeveloperBotRequest;
import com.alex.messenger.media.PhotoAccess;
import com.alex.messenger.media.ProfilePhotoService;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DeveloperBotServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BotAccountRepository botAccountRepository;

    @Mock
    private ProfilePhotoService profilePhotoService;

    private DeveloperBotService developerBotService;

    @BeforeEach
    void setUp() {
        developerBotService = new DeveloperBotService(userRepository, botAccountRepository, profilePhotoService);
    }

    @Test
    void createBotReturnsIssuedTokenAndPersistsBotAccount() {
        UUID ownerUserId = UUID.randomUUID();
        UUID botUserId = UUID.randomUUID();

        UserEntity owner = new UserEntity();
        owner.setId(ownerUserId);
        owner.setDisplayName("Owner");

        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(userRepository.findByPhoneNumber(any())).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("weatherbot")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(botUserId);
            }
            return user;
        });
        when(botAccountRepository.save(any(BotAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profilePhotoService.buildPhotoAccess(any(), any(), any())).thenReturn(new PhotoAccess(null, null));

        IssuedBotTokenResponse response = developerBotService.createBot(
                ownerUserId,
                new CreateDeveloperBotRequest(
                        "Weather Bot",
                        "weatherbot",
                        "Forecasts and conditions",
                        "Provides simple forecast responses",
                        true,
                        "https://example.com/weather-mini-app"
                )
        );

        assertThat(response.apiToken()).startsWith("alexbot_");
        assertThat(response.bot().botUserId()).isEqualTo(botUserId);
        assertThat(response.bot().ownerUserId()).isEqualTo(ownerUserId);
        assertThat(response.bot().username()).isEqualTo("weatherbot");
        assertThat(response.bot().supportsInline()).isTrue();
        assertThat(response.bot().webAppUrl()).isEqualTo("https://example.com/weather-mini-app");
        assertThat(response.bot().apiTokenPrefix()).isNotBlank();
    }

    @Test
    void updateBotRejectsUsernameWithoutBotSuffix() {
        UUID ownerUserId = UUID.randomUUID();
        UUID botUserId = UUID.randomUUID();

        UserEntity bot = new UserEntity();
        bot.setId(botUserId);
        bot.setBot(true);
        bot.setDisplayName("Bot");
        bot.setUsername("samplebot");

        BotAccountEntity account = new BotAccountEntity();
        account.setBotUserId(botUserId);
        account.setOwnerUserId(ownerUserId);
        account.setApiTokenHash("hash");
        account.setApiTokenPrefix("alexbot_abcd");
        account.setTokenRotatedAt(Instant.now());

        when(botAccountRepository.findByBotUserIdAndOwnerUserId(botUserId, ownerUserId)).thenReturn(Optional.of(account));
        when(userRepository.findByIdAndBotTrue(botUserId)).thenReturn(Optional.of(bot));

        ResponseStatusException exception = catchThrowableOfType(
                () -> developerBotService.updateBot(
                        ownerUserId,
                        botUserId,
                        new UpdateDeveloperBotRequest(null, "weather", null, null, null, null)
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateWebhookStoresWebhookMetadata() {
        UUID ownerUserId = UUID.randomUUID();
        UUID botUserId = UUID.randomUUID();

        UserEntity bot = new UserEntity();
        bot.setId(botUserId);
        bot.setBot(true);
        bot.setDisplayName("Bot");
        bot.setUsername("samplebot");

        BotAccountEntity account = new BotAccountEntity();
        account.setBotUserId(botUserId);
        account.setOwnerUserId(ownerUserId);
        account.setApiTokenHash("hash");
        account.setApiTokenPrefix("alexbot_abcd");
        account.setTokenRotatedAt(Instant.now());

        when(botAccountRepository.findByBotUserIdAndOwnerUserId(botUserId, ownerUserId)).thenReturn(Optional.of(account));
        when(userRepository.findByIdAndBotTrue(botUserId)).thenReturn(Optional.of(bot));
        when(botAccountRepository.save(any(BotAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profilePhotoService.buildPhotoAccess(any(), any(), any())).thenReturn(new PhotoAccess(null, null));

        var response = developerBotService.updateWebhook(
                ownerUserId,
                botUserId,
                new UpdateBotWebhookRequest("https://example.com/webhook", "secret-token")
        );

        assertThat(response.webhookEnabled()).isTrue();
        assertThat(response.webhookUrl()).isEqualTo("https://example.com/webhook");
        assertThat(response.hasWebhookSecret()).isTrue();
    }
}
