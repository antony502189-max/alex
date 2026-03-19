package com.alex.messenger.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.ChatService;
import com.alex.messenger.crypto.ChatEncryptionService;
import com.alex.messenger.media.ProfilePhotoService;
import com.alex.messenger.message.ChatMessagePublisher;
import com.alex.messenger.message.MessageContentCodec;
import com.alex.messenger.message.MessageStorageService;
import com.alex.messenger.message.expiration.MessageExpirationRepository;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
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
class BotServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BotAccountRepository botAccountRepository;

    @Mock
    private BotCommandService botCommandService;

    @Mock
    private BotInlineResultCacheService botInlineResultCacheService;

    @Mock
    private ChatService chatService;

    @Mock
    private ProfilePhotoService profilePhotoService;

    @Mock
    private MessageStorageService messageStorageService;

    @Mock
    private MessageExpirationRepository messageExpirationRepository;

    @Mock
    private ChatEncryptionService chatEncryptionService;

    @Mock
    private MessageContentCodec messageContentCodec;

    @Mock
    private ChatMessagePublisher chatMessagePublisher;

    private BotService botService;

    @BeforeEach
    void setUp() {
        botService = new BotService(
                userRepository,
                botAccountRepository,
                botCommandService,
                botInlineResultCacheService,
                chatService,
                profilePhotoService,
                messageStorageService,
                messageExpirationRepository,
                chatEncryptionService,
                messageContentCodec,
                chatMessagePublisher
        );
    }

    @Test
    void getInlineResultsRejectsTooLongQuery() {
        UserEntity bot = new UserEntity();
        bot.setId(UUID.randomUUID());
        bot.setBot(true);
        bot.setUsername("alex_echo_bot");

        when(userRepository.findByUsernameIgnoreCase("alex_echo_bot")).thenReturn(Optional.of(bot));

        ResponseStatusException exception = catchThrowableOfType(
                () -> botService.getInlineResults("alex_echo_bot", "a".repeat(256)),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository).findByUsernameIgnoreCase("alex_echo_bot");
        verifyNoInteractions(
                botAccountRepository,
                botCommandService,
                botInlineResultCacheService,
                chatService,
                profilePhotoService,
                messageStorageService,
                messageExpirationRepository,
                chatEncryptionService,
                messageContentCodec,
                chatMessagePublisher
        );
    }
}
