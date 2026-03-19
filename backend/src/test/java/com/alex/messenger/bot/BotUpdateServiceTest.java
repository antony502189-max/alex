package com.alex.messenger.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.alex.messenger.chat.ChatService;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.payments.PaymentInvoiceRepository;
import com.alex.messenger.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BotUpdateServiceTest {

    @Mock
    private BotAccountRepository botAccountRepository;

    @Mock
    private BotCallbackQueryService botCallbackQueryService;

    @Mock
    private BotUpdateRepository botUpdateRepository;

    @Mock
    private BotWebAppEventRepository botWebAppEventRepository;

    @Mock
    private BotWebAppQueryRepository botWebAppQueryRepository;

    @Mock
    private BotPaymentInvoiceRepository botPaymentInvoiceRepository;

    @Mock
    private BotPreCheckoutQueryRepository botPreCheckoutQueryRepository;

    @Mock
    private BotPaymentReceiptRepository botPaymentReceiptRepository;

    @Mock
    private PaymentInvoiceRepository paymentInvoiceRepository;

    @Mock
    private MessageService messageService;

    @Mock
    private ChatService chatService;

    @Mock
    private UserRepository userRepository;

    private BotUpdateService botUpdateService;

    @BeforeEach
    void setUp() {
        botUpdateService = new BotUpdateService(
                botAccountRepository,
                botCallbackQueryService,
                botUpdateRepository,
                botWebAppEventRepository,
                botWebAppQueryRepository,
                botPaymentInvoiceRepository,
                botPreCheckoutQueryRepository,
                botPaymentReceiptRepository,
                paymentInvoiceRepository,
                objectProvider(messageService),
                chatService,
                userRepository,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(botUpdateService, "maxLongPollLimit", 100);
        ReflectionTestUtils.setField(botUpdateService, "maxLongPollTimeoutSeconds", 30);
    }

    @Test
    void getUpdatesRejectsTooLargeLimit() {
        UUID botUserId = UUID.randomUUID();
        whenBotExists(botUserId);

        ResponseStatusException exception = catchThrowableOfType(
                () -> botUpdateService.getUpdates(botUserId, 0L, 101, 0),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("limit must be between 1 and 100");
    }

    @Test
    void getUpdatesRejectsTooLargeTimeout() {
        UUID botUserId = UUID.randomUUID();
        whenBotExists(botUserId);

        ResponseStatusException exception = catchThrowableOfType(
                () -> botUpdateService.getUpdates(botUserId, 0L, 20, 31),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("timeoutSeconds must be between 0 and 30");
    }

    @Test
    void getUpdatesRejectsNegativeOffset() {
        UUID botUserId = UUID.randomUUID();
        whenBotExists(botUserId);

        ResponseStatusException exception = catchThrowableOfType(
                () -> botUpdateService.getUpdates(botUserId, -1L, 20, 0),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("offset must be non-negative");
    }

    @Test
    void deliverWebhookUpdateMarksErrorWhenBotAccountIsMissing() {
        UUID botUserId = UUID.randomUUID();
        BotUpdateEntity update = new BotUpdateEntity();
        update.setId(1L);
        update.setBotUserId(botUserId);

        org.mockito.Mockito.when(botAccountRepository.findById(botUserId)).thenReturn(Optional.empty());
        org.mockito.Mockito.when(botUpdateRepository.save(any(BotUpdateEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        botUpdateService.deliverWebhookUpdate(update);

        assertThat(update.getDeliveryAttempts()).isEqualTo(1);
        assertThat(update.getLastDeliveryAttemptAt()).isNotNull();
        assertThat(update.getLastError()).isEqualTo("Bot not found");
        verify(botUpdateRepository).save(update);
        verify(botAccountRepository, never()).save(any(BotAccountEntity.class));
    }

    private void whenBotExists(UUID botUserId) {
        BotAccountEntity account = new BotAccountEntity();
        account.setBotUserId(botUserId);
        account.setWebhookEnabled(false);
        account.setWebhookUrl(null);
        org.mockito.Mockito.when(botAccountRepository.findById(botUserId)).thenReturn(Optional.of(account));
    }

    private ObjectProvider<MessageService> objectProvider(MessageService service) {
        return new ObjectProvider<>() {
            @Override
            public MessageService getObject() {
                return service;
            }

            @Override
            public MessageService getObject(Object... args) {
                return service;
            }

            @Override
            public MessageService getIfAvailable() {
                return service;
            }

            @Override
            public MessageService getIfUnique() {
                return service;
            }

            @Override
            public Iterator<MessageService> iterator() {
                return Stream.of(service).iterator();
            }

            @Override
            public Stream<MessageService> stream() {
                return Stream.of(service);
            }

            @Override
            public Stream<MessageService> orderedStream() {
                return Stream.of(service);
            }
        };
    }
}
