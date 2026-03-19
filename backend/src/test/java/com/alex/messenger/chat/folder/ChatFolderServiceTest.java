package com.alex.messenger.chat.folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.dto.ChatFolderResponse;
import com.alex.messenger.chat.dto.ChatLastMessagePreviewResponse;
import com.alex.messenger.chat.dto.ChatSummaryResponse;
import com.alex.messenger.user.ContactEntity;
import com.alex.messenger.user.ContactId;
import com.alex.messenger.user.ContactRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatFolderServiceTest {

    @Mock
    private ChatFolderRepository chatFolderRepository;

    @Mock
    private ChatFolderItemRepository chatFolderItemRepository;

    @Mock
    private ChatFolderExcludedItemRepository chatFolderExcludedItemRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private ContactRepository contactRepository;

    private ChatFolderService chatFolderService;

    @BeforeEach
    void setUp() {
        chatFolderService = new ChatFolderService(
                chatFolderRepository,
                chatFolderItemRepository,
                chatFolderExcludedItemRepository,
                chatService,
                contactRepository
        );
    }

    @Test
    void listChatsAppliesRuleSignalsAndExplicitOverrides() {
        UUID requesterId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID excludedContactPeerId = UUID.randomUUID();
        UUID includedContactPeerId = UUID.randomUUID();
        UUID nonContactPeerId = UUID.randomUUID();
        UUID botPeerId = UUID.randomUUID();
        UUID excludedContactChatId = UUID.randomUUID();
        UUID includedContactChatId = UUID.randomUUID();
        UUID directNonContactChatId = UUID.randomUUID();
        UUID directBotChatId = UUID.randomUUID();
        UUID explicitGroupChatId = UUID.randomUUID();

        ChatFolderEntity folder = folder(folderId, requesterId);
        folder.setIncludeContacts(true);

        when(chatFolderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(chatFolderItemRepository.findAllByIdFolderId(folderId)).thenReturn(List.of(
                included(folderId, explicitGroupChatId)
        ));
        when(chatFolderExcludedItemRepository.findAllByIdFolderId(folderId)).thenReturn(List.of(
                excluded(folderId, excludedContactChatId)
        ));
        when(chatService.listAllChats(requesterId)).thenReturn(List.of(
                chat(excludedContactChatId, "DIRECT", excludedContactPeerId, false, 4, false, null),
                chat(includedContactChatId, "DIRECT", includedContactPeerId, false, 1, false, null),
                chat(directNonContactChatId, "DIRECT", nonContactPeerId, false, 2, false, null),
                chat(directBotChatId, "DIRECT", botPeerId, true, 3, false, null),
                chat(explicitGroupChatId, "GROUP", null, false, 0, false, null)
        ));
        when(contactRepository.findAllByIdOwnerUserIdOrderByContactNameAsc(requesterId)).thenReturn(List.of(
                contact(requesterId, excludedContactPeerId),
                contact(requesterId, includedContactPeerId)
        ));

        List<ChatSummaryResponse> response = chatFolderService.listChats(requesterId, folderId, null);

        assertThat(response).extracting(ChatSummaryResponse::chatId)
                .containsExactly(includedContactChatId, explicitGroupChatId);
    }

    @Test
    void listReturnsResolvedChatIdsAndRuleMetadata() {
        UUID requesterId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID peerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatFolderEntity folder = folder(folderId, requesterId);
        folder.setIncludedChatTypes("DIRECT");
        folder.setIncludeContacts(true);
        folder.setIncludeUnread(true);
        folder.setIncludeNonArchived(true);

        when(chatFolderRepository.findAllByOwnerUserIdOrderByPositionAscTitleAsc(requesterId)).thenReturn(List.of(folder));
        when(chatFolderItemRepository.findAllByIdFolderIdIn(List.of(folderId))).thenReturn(List.of(
                included(folderId, chatId)
        ));
        when(chatFolderExcludedItemRepository.findAllByIdFolderIdIn(List.of(folderId))).thenReturn(List.of());
        when(chatService.listAllChats(requesterId)).thenReturn(List.of(
                chat(chatId, "DIRECT", peerUserId, false, 1, false, null)
        ));
        when(contactRepository.findAllByIdOwnerUserIdOrderByContactNameAsc(requesterId)).thenReturn(List.of(
                contact(requesterId, peerUserId)
        ));

        List<ChatFolderResponse> response = chatFolderService.list(requesterId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).chatIds()).containsExactly(chatId);
        assertThat(response.get(0).includedChatIds()).containsExactly(chatId);
        assertThat(response.get(0).includedChatTypes()).containsExactly("DIRECT");
        assertThat(response.get(0).includeContacts()).isTrue();
        assertThat(response.get(0).includeUnread()).isTrue();
        assertThat(response.get(0).includeNonArchived()).isTrue();
    }

    @Test
    void listChatsRespectsArchivedFilterAlongsideMutedRules() {
        UUID requesterId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID mutedActiveChatId = UUID.randomUUID();
        UUID mutedArchivedChatId = UUID.randomUUID();
        Instant futureMute = Instant.now().plusSeconds(600);

        ChatFolderEntity folder = folder(folderId, requesterId);
        folder.setIncludeMuted(true);
        folder.setIncludeArchived(true);

        when(chatFolderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(chatFolderItemRepository.findAllByIdFolderId(folderId)).thenReturn(List.of());
        when(chatFolderExcludedItemRepository.findAllByIdFolderId(folderId)).thenReturn(List.of());
        when(chatService.listAllChats(requesterId)).thenReturn(List.of(
                chat(mutedActiveChatId, "GROUP", null, false, 0, false, futureMute),
                chat(mutedArchivedChatId, "GROUP", null, false, 0, true, futureMute)
        ));
        when(contactRepository.findAllByIdOwnerUserIdOrderByContactNameAsc(requesterId)).thenReturn(List.of());

        List<ChatSummaryResponse> activeChats = chatFolderService.listChats(requesterId, folderId, false);
        List<ChatSummaryResponse> archivedChats = chatFolderService.listChats(requesterId, folderId, true);

        assertThat(activeChats).extracting(ChatSummaryResponse::chatId).containsExactly(mutedActiveChatId);
        assertThat(archivedChats).extracting(ChatSummaryResponse::chatId).containsExactly(mutedArchivedChatId);
    }

    @Test
    void createRejectsNegativePosition() {
        assertThatThrownBy(() -> chatFolderService.create(
                UUID.randomUUID(),
                new com.alex.messenger.chat.dto.UpsertChatFolderRequest(
                        "Work",
                        -1,
                        null,
                        null,
                        null,
                        List.of("DIRECT"),
                        true,
                        false,
                        false,
                        false,
                        true,
                        false,
                        true,
                        false,
                        true
                )
        ))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .extracting(throwable -> ((org.springframework.web.server.ResponseStatusException) throwable).getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    private ChatFolderEntity folder(UUID folderId, UUID ownerUserId) {
        ChatFolderEntity folder = new ChatFolderEntity();
        folder.setId(folderId);
        folder.setOwnerUserId(ownerUserId);
        folder.setTitle("Work");
        folder.setPosition(0);
        folder.setIncludeContacts(false);
        folder.setIncludeNonContacts(false);
        folder.setIncludeBots(false);
        folder.setIncludeRead(false);
        folder.setIncludeUnread(false);
        folder.setIncludeMuted(false);
        folder.setIncludeUnmuted(false);
        folder.setIncludeArchived(false);
        folder.setIncludeNonArchived(false);
        return folder;
    }

    private ChatFolderItemEntity included(UUID folderId, UUID chatId) {
        ChatFolderItemEntity item = new ChatFolderItemEntity();
        item.setId(new ChatFolderItemId(folderId, chatId));
        return item;
    }

    private ChatFolderExcludedItemEntity excluded(UUID folderId, UUID chatId) {
        ChatFolderExcludedItemEntity item = new ChatFolderExcludedItemEntity();
        item.setId(new ChatFolderExcludedItemId(folderId, chatId));
        return item;
    }

    private ContactEntity contact(UUID ownerUserId, UUID contactUserId) {
        ContactEntity contact = new ContactEntity();
        contact.setId(new ContactId(ownerUserId, contactUserId));
        contact.setContactName("Contact");
        return contact;
    }

    private ChatSummaryResponse chat(
            UUID chatId,
            String chatType,
            UUID peerUserId,
            boolean peerIsBot,
            int unreadCount,
            boolean archived,
            Instant mutedUntil
    ) {
        return new ChatSummaryResponse(
                chatId,
                chatType,
                chatType + " chat",
                null,
                null,
                peerUserId,
                null,
                peerUserId != null ? "Peer" : null,
                false,
                null,
                peerIsBot,
                false,
                null,
                null,
                null,
                null,
                null,
                false,
                0,
                null,
                null,
                Instant.parse("2026-03-19T10:00:00Z"),
                2,
                null,
                unreadCount,
                0,
                0,
                archived,
                null,
                null,
                mutedUntil,
                null,
                false,
                true,
                true,
                true,
                new ChatLastMessagePreviewResponse(null, null, null, false, false, null, null, null, null, null)
        );
    }
}
