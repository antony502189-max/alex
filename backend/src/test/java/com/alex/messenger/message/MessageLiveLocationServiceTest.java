package com.alex.messenger.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.alex.messenger.message.dto.MessageLiveLocationPayload;
import com.alex.messenger.message.dto.UpdateLiveLocationRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MessageLiveLocationServiceTest {

    @Mock
    private MessageLiveLocationRepository messageLiveLocationRepository;

    private MessageLiveLocationService messageLiveLocationService;

    @BeforeEach
    void setUp() {
        messageLiveLocationService = new MessageLiveLocationService(messageLiveLocationRepository);
    }

    @Test
    void activateCreatesActiveLiveLocationPayload() {
        UUID messageId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        when(messageLiveLocationRepository.findByMessageId(messageId)).thenReturn(Optional.empty());
        when(messageLiveLocationRepository.save(any(MessageLiveLocationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MessageLiveLocationPayload payload = messageLiveLocationService.activate(
                messageId,
                chatId,
                senderId,
                new MessageLiveLocationPayload(53.9, 27.56, " Minsk ", " Center ", 3600, null, null, null, null)
        );

        assertThat(payload.latitude()).isEqualTo(53.9);
        assertThat(payload.longitude()).isEqualTo(27.56);
        assertThat(payload.title()).isEqualTo("Minsk");
        assertThat(payload.address()).isEqualTo("Center");
        assertThat(payload.expiresAt()).isNotNull();
        assertThat(payload.lastUpdatedAt()).isNotNull();
        assertThat(payload.active()).isTrue();
    }

    @Test
    void updateRefreshesCoordinatesForActiveSenderOwnedLocation() {
        UUID messageId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        MessageLiveLocationEntity entity = new MessageLiveLocationEntity();
        entity.setMessageId(messageId);
        entity.setChatId(chatId);
        entity.setSenderUserId(senderId);
        entity.setLatitude(53.9);
        entity.setLongitude(27.56);
        entity.setExpiresAt(Instant.now().plusSeconds(600));

        when(messageLiveLocationRepository.findByMessageId(messageId)).thenReturn(Optional.of(entity));
        when(messageLiveLocationRepository.save(any(MessageLiveLocationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MessageLiveLocationPayload payload = messageLiveLocationService.update(
                message(messageId, chatId, senderId),
                new UpdateLiveLocationRequest(54.1, 27.7, "Updated", "New address")
        );

        assertThat(payload.latitude()).isEqualTo(54.1);
        assertThat(payload.longitude()).isEqualTo(27.7);
        assertThat(payload.title()).isEqualTo("Updated");
        assertThat(payload.address()).isEqualTo("New address");
        assertThat(payload.lastUpdatedAt()).isNotNull();
        assertThat(payload.active()).isTrue();
    }

    @Test
    void stopMarksLiveLocationInactive() {
        UUID messageId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        MessageLiveLocationEntity entity = new MessageLiveLocationEntity();
        entity.setMessageId(messageId);
        entity.setChatId(chatId);
        entity.setSenderUserId(senderId);
        entity.setLatitude(53.9);
        entity.setLongitude(27.56);
        entity.setExpiresAt(Instant.now().plusSeconds(600));

        when(messageLiveLocationRepository.findByMessageId(messageId)).thenReturn(Optional.of(entity));
        when(messageLiveLocationRepository.save(any(MessageLiveLocationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MessageLiveLocationPayload payload = messageLiveLocationService.stop(message(messageId, chatId, senderId));

        assertThat(payload.stoppedAt()).isNotNull();
        assertThat(payload.lastUpdatedAt()).isEqualTo(payload.stoppedAt());
        assertThat(payload.active()).isFalse();
    }

    @Test
    void activateRejectsInvalidLiveLocationDuration() {
        assertThatThrownBy(() -> messageLiveLocationService.activate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new MessageLiveLocationPayload(53.9, 27.56, "Point", "Address", 30, null, null, null, null)
        ))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .extracting(throwable -> ((org.springframework.web.server.ResponseStatusException) throwable).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateRejectsExpiredLiveLocation() {
        UUID messageId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        MessageLiveLocationEntity entity = new MessageLiveLocationEntity();
        entity.setMessageId(messageId);
        entity.setChatId(chatId);
        entity.setSenderUserId(senderId);
        entity.setLatitude(53.9);
        entity.setLongitude(27.56);
        entity.setExpiresAt(Instant.now().minusSeconds(60));

        when(messageLiveLocationRepository.findByMessageId(messageId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> messageLiveLocationService.update(
                message(messageId, chatId, senderId),
                new UpdateLiveLocationRequest(54.1, 27.7, "Updated", "New address")
        ))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .extracting(throwable -> ((org.springframework.web.server.ResponseStatusException) throwable).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void stopRejectsForeignSender() {
        UUID messageId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID anotherSenderId = UUID.randomUUID();

        MessageLiveLocationEntity entity = new MessageLiveLocationEntity();
        entity.setMessageId(messageId);
        entity.setChatId(chatId);
        entity.setSenderUserId(senderId);
        entity.setLatitude(53.9);
        entity.setLongitude(27.56);
        entity.setExpiresAt(Instant.now().plusSeconds(600));

        when(messageLiveLocationRepository.findByMessageId(messageId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> messageLiveLocationService.stop(message(messageId, chatId, anotherSenderId)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .extracting(throwable -> ((org.springframework.web.server.ResponseStatusException) throwable).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private MessageLookupEntity message(UUID messageId, UUID chatId, UUID senderId) {
        MessageLookupEntity message = new MessageLookupEntity();
        message.setMessageId(messageId);
        message.setChatId(chatId);
        message.setSenderId(senderId);
        return message;
    }
}
