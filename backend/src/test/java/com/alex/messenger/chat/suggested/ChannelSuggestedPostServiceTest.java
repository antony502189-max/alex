package com.alex.messenger.chat.suggested;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.attachment.AttachmentService;
import com.alex.messenger.chat.ChatAdminLogService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberEntity;
import com.alex.messenger.chat.ChatMemberId;
import com.alex.messenger.chat.ChatMemberRepository;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.suggested.dto.CreateSuggestedPostRequest;
import com.alex.messenger.chat.suggested.dto.DeclineSuggestedPostRequest;
import com.alex.messenger.chat.suggested.dto.SuggestedPostResponse;
import com.alex.messenger.crypto.ChatEncryptionService;
import com.alex.messenger.crypto.EncryptedPayload;
import com.alex.messenger.message.MessageContentCodec;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.MessageTextContent;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.payments.PaymentInvoiceEntity;
import com.alex.messenger.payments.PaymentInvoiceRepository;
import com.alex.messenger.payments.PaymentService;
import com.alex.messenger.payments.dto.PaymentInvoiceResponse;
import com.alex.messenger.sticker.StickerService;
import java.time.Instant;
import java.util.List;
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
class ChannelSuggestedPostServiceTest {

    @Mock
    private SuggestedPostRepository suggestedPostRepository;

    @Mock
    private SuggestedPostPaymentRepository suggestedPostPaymentRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private ChatMemberRepository chatMemberRepository;

    @Mock
    private ChatAdminLogService chatAdminLogService;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private StickerService stickerService;

    @Mock
    private MessageService messageService;

    @Mock
    private MessageContentCodec messageContentCodec;

    @Mock
    private ChatEncryptionService chatEncryptionService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentInvoiceRepository paymentInvoiceRepository;

    private ChannelSuggestedPostService channelSuggestedPostService;

    @BeforeEach
    void setUp() {
        channelSuggestedPostService = new ChannelSuggestedPostService(
                suggestedPostRepository,
                suggestedPostPaymentRepository,
                chatService,
                chatMemberRepository,
                chatAdminLogService,
                attachmentService,
                stickerService,
                messageService,
                messageContentCodec,
                chatEncryptionService,
                paymentService,
                paymentInvoiceRepository
        );
    }

    @Test
    void createSuggestedPostForPublicChannelClonesAttachmentsAndCreatesInvoice() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID originalAttachmentId = UUID.randomUUID();
        UUID clonedAttachmentId = UUID.randomUUID();
        UUID suggestedPostId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        ChatEntity chat = channel(chatId, "public_channel");
        ChatMemberEntity ownerMembership = member(chatId, ownerUserId, "OWNER", true);
        MessageTextContent content = new MessageTextContent("Promoted post", List.of());

        when(chatService.getChat(chatId)).thenReturn(chat);
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(false);
        when(messageContentCodec.normalize(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(content);
        when(messageContentCodec.encode(content)).thenReturn("encoded");
        when(chatEncryptionService.encrypt(chatId, "encoded"))
                .thenReturn(new EncryptedPayload("cipher", "nonce", 1));
        when(attachmentService.cloneAttachmentsToChatForSystem(requesterId, chatId, List.of(originalAttachmentId)))
                .thenReturn(List.of(clonedAttachmentId));
        when(suggestedPostRepository.save(any(SuggestedPostEntity.class))).thenAnswer(invocation -> {
            SuggestedPostEntity suggestedPost = invocation.getArgument(0);
            if (suggestedPost.getId() == null) {
                suggestedPost.setId(suggestedPostId);
            }
            if (suggestedPost.getCreatedAt() == null) {
                suggestedPost.setCreatedAt(Instant.parse("2026-03-19T12:00:00Z"));
            }
            if (suggestedPost.getUpdatedAt() == null) {
                suggestedPost.setUpdatedAt(suggestedPost.getCreatedAt());
            }
            return suggestedPost;
        });
        when(chatMemberRepository.findByIdChatIdAndRole(chatId, "OWNER")).thenReturn(Optional.of(ownerMembership));
        when(paymentService.createInvoice(eq(requesterId), any())).thenReturn(new PaymentInvoiceResponse(
                invoiceId,
                requesterId,
                ownerUserId,
                "Suggested post for public_channel",
                "Suggested post submission to @public_channel: Promoted post",
                250L,
                "XTR",
                "OPEN",
                java.util.Map.of("type", "SUGGESTED_POST"),
                Instant.parse("2026-03-19T12:00:30Z"),
                Instant.parse("2026-03-19T12:00:30Z"),
                null
        ));
        when(suggestedPostPaymentRepository.save(any(SuggestedPostPaymentEntity.class))).thenAnswer(invocation -> {
            SuggestedPostPaymentEntity payment = invocation.getArgument(0);
            payment.setId(paymentId);
            payment.setCreatedAt(Instant.parse("2026-03-19T12:00:31Z"));
            payment.setUpdatedAt(Instant.parse("2026-03-19T12:00:31Z"));
            return payment;
        });
        when(chatEncryptionService.decrypt(chatId, "cipher", "nonce", 1)).thenReturn("decoded");
        when(messageContentCodec.decode("decoded")).thenReturn(content);

        SuggestedPostResponse response = channelSuggestedPostService.createSuggestedPost(
                requesterId,
                chatId,
                new CreateSuggestedPostRequest(
                        "Promoted post",
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(originalAttachmentId),
                        null,
                        false,
                        250L
                )
        );

        assertThat(response.suggestedPostId()).isEqualTo(suggestedPostId);
        assertThat(response.status()).isEqualTo("PAYMENT_PENDING");
        assertThat(response.attachmentIds()).containsExactly(clonedAttachmentId);
        assertThat(response.payment()).isNotNull();
        assertThat(response.payment().invoiceId()).isEqualTo(invoiceId);
        assertThat(response.payment().recipientUserId()).isEqualTo(ownerUserId);
        verify(attachmentService).cloneAttachmentsToChatForSystem(requesterId, chatId, List.of(originalAttachmentId));
    }

    @Test
    void createSuggestedPostRejectsMissingRequest() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ResponseStatusException exception = catchThrowableOfType(
                () -> channelSuggestedPostService.createSuggestedPost(requesterId, chatId, null),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("Suggested post payload is required");
    }

    @Test
    void createSuggestedPostRejectsNullAttachmentId() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = channel(chatId, "public_channel");
        MessageTextContent content = new MessageTextContent("Promoted post", List.of());

        when(chatService.getChat(chatId)).thenReturn(chat);
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(false);
        when(messageContentCodec.normalize(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(content);

        ResponseStatusException exception = catchThrowableOfType(
                () -> channelSuggestedPostService.createSuggestedPost(
                        requesterId,
                        chatId,
                        new CreateSuggestedPostRequest(
                                "Promoted post",
                                null,
                                null,
                                List.of(),
                                null,
                                null,
                                java.util.Arrays.asList(UUID.randomUUID(), null),
                                null,
                                false,
                                null
                        )
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("Suggested post attachment ids must not contain null");
    }

    @Test
    void listSuggestedPostsRejectsInvalidLimit() {
        ResponseStatusException exception = catchThrowableOfType(
                () -> channelSuggestedPostService.listSuggestedPosts(UUID.randomUUID(), UUID.randomUUID(), null, 101),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("limit must be between 1 and 100");
    }

    @Test
    void approveSuggestedPostPublishesMessageWhenInvoiceIsPaid() {
        UUID reviewerId = UUID.randomUUID();
        UUID submitterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID publishedMessageId = UUID.randomUUID();

        ChatEntity chat = channel(chatId, "reviewed_channel");
        ChatMemberEntity reviewerMembership = member(chatId, reviewerId, "ADMIN", true);
        SuggestedPostEntity suggestedPost = suggestedPost(postId, chatId, submitterId, "PAYMENT_PENDING", attachmentId);
        SuggestedPostPaymentEntity payment = payment(postId, paymentId, invoiceId, submitterId, reviewerId, "OPEN");
        PaymentInvoiceEntity invoice = invoice(invoiceId, submitterId, reviewerId, 300L, "PAID");
        MessageTextContent content = new MessageTextContent("Ready for publish", List.of());

        when(chatService.getChat(chatId)).thenReturn(chat);
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, reviewerId)).thenReturn(true);
        when(chatService.getMembership(chatId, reviewerId)).thenReturn(reviewerMembership);
        when(suggestedPostRepository.findByIdAndChatId(postId, chatId)).thenReturn(Optional.of(suggestedPost));
        when(suggestedPostPaymentRepository.findBySuggestedPostId(postId)).thenReturn(Optional.of(payment));
        when(paymentInvoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(suggestedPostPaymentRepository.save(any(SuggestedPostPaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatEncryptionService.decrypt(chatId, "cipher", "nonce", 1)).thenReturn("decoded");
        when(messageContentCodec.decode("decoded")).thenReturn(content);
        when(messageService.sendMessage(eq(reviewerId), any())).thenReturn(message(chatId, publishedMessageId, reviewerId));
        when(suggestedPostRepository.save(any(SuggestedPostEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SuggestedPostResponse response = channelSuggestedPostService.approveSuggestedPost(reviewerId, chatId, postId);

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.publishedMessageId()).isEqualTo(publishedMessageId);
        assertThat(response.payment()).isNotNull();
        assertThat(response.payment().status()).isEqualTo("PAID");
        verify(messageService).sendMessage(eq(reviewerId), any());
    }

    @Test
    void approveSuggestedPostRejectsUnpaidInvoice() {
        UUID reviewerId = UUID.randomUUID();
        UUID submitterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        ChatEntity chat = channel(chatId, "reviewed_channel");
        ChatMemberEntity reviewerMembership = member(chatId, reviewerId, "OWNER", true);
        SuggestedPostEntity suggestedPost = suggestedPost(postId, chatId, submitterId, "PAYMENT_PENDING", null);
        SuggestedPostPaymentEntity payment = payment(postId, paymentId, invoiceId, submitterId, reviewerId, "OPEN");
        PaymentInvoiceEntity invoice = invoice(invoiceId, submitterId, reviewerId, 300L, "OPEN");

        when(chatService.getChat(chatId)).thenReturn(chat);
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, reviewerId)).thenReturn(true);
        when(chatService.getMembership(chatId, reviewerId)).thenReturn(reviewerMembership);
        when(suggestedPostRepository.findByIdAndChatId(postId, chatId)).thenReturn(Optional.of(suggestedPost));
        when(suggestedPostPaymentRepository.findBySuggestedPostId(postId)).thenReturn(Optional.of(payment));
        when(paymentInvoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        ResponseStatusException exception = catchThrowableOfType(
                () -> channelSuggestedPostService.approveSuggestedPost(reviewerId, chatId, postId),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(messageService, never()).sendMessage(eq(reviewerId), any());
    }

    @Test
    void declineSuggestedPostPersistsReason() {
        UUID reviewerId = UUID.randomUUID();
        UUID submitterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        ChatEntity chat = channel(chatId, "reviewed_channel");
        ChatMemberEntity reviewerMembership = member(chatId, reviewerId, "ADMIN", true);
        SuggestedPostEntity suggestedPost = suggestedPost(postId, chatId, submitterId, "SUBMITTED", null);
        MessageTextContent content = new MessageTextContent("Needs review", List.of());

        when(chatService.getChat(chatId)).thenReturn(chat);
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, reviewerId)).thenReturn(true);
        when(chatService.getMembership(chatId, reviewerId)).thenReturn(reviewerMembership);
        when(suggestedPostRepository.findByIdAndChatId(postId, chatId)).thenReturn(Optional.of(suggestedPost));
        when(suggestedPostPaymentRepository.findBySuggestedPostId(postId)).thenReturn(Optional.empty());
        when(suggestedPostRepository.save(any(SuggestedPostEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatEncryptionService.decrypt(chatId, "cipher", "nonce", 1)).thenReturn("decoded");
        when(messageContentCodec.decode("decoded")).thenReturn(content);

        SuggestedPostResponse response = channelSuggestedPostService.declineSuggestedPost(
                reviewerId,
                chatId,
                postId,
                new DeclineSuggestedPostRequest(" Off topic ")
        );

        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo("Off topic");
        assertThat(response.declinedAt()).isNotNull();
    }

    private ChatEntity channel(UUID chatId, String publicUsername) {
        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("CHANNEL");
        chat.setPublicUsername(publicUsername);
        chat.setTitle("Channel " + publicUsername);
        chat.setCreatedAt(Instant.parse("2026-03-19T10:00:00Z"));
        return chat;
    }

    private ChatMemberEntity member(UUID chatId, UUID userId, String role, boolean canManageMessages) {
        ChatMemberEntity member = new ChatMemberEntity();
        member.setId(new ChatMemberId(chatId, userId));
        member.setRole(role);
        member.setCanManageMessages(canManageMessages);
        member.setCanPostMessages(true);
        member.setCanSendMessages(true);
        return member;
    }

    private SuggestedPostEntity suggestedPost(
            UUID postId,
            UUID chatId,
            UUID submitterId,
            String status,
            UUID attachmentId
    ) {
        SuggestedPostEntity suggestedPost = new SuggestedPostEntity();
        suggestedPost.setId(postId);
        suggestedPost.setChatId(chatId);
        suggestedPost.setSubmittedByUserId(submitterId);
        suggestedPost.setCiphertext("cipher");
        suggestedPost.setNonce("nonce");
        suggestedPost.setKeyVersion(1);
        suggestedPost.setAttachmentIds(attachmentId != null ? attachmentId.toString() : "");
        suggestedPost.setStatus(status);
        suggestedPost.setCreatedAt(Instant.parse("2026-03-19T11:00:00Z"));
        suggestedPost.setUpdatedAt(Instant.parse("2026-03-19T11:00:00Z"));
        return suggestedPost;
    }

    private SuggestedPostPaymentEntity payment(
            UUID suggestedPostId,
            UUID paymentId,
            UUID invoiceId,
            UUID payerUserId,
            UUID recipientUserId,
            String status
    ) {
        SuggestedPostPaymentEntity payment = new SuggestedPostPaymentEntity();
        payment.setId(paymentId);
        payment.setSuggestedPostId(suggestedPostId);
        payment.setInvoiceId(invoiceId);
        payment.setPayerUserId(payerUserId);
        payment.setRecipientUserId(recipientUserId);
        payment.setAmountUnits(300L);
        payment.setCurrencyCode("XTR");
        payment.setStatus(status);
        payment.setCreatedAt(Instant.parse("2026-03-19T11:00:30Z"));
        payment.setUpdatedAt(Instant.parse("2026-03-19T11:00:30Z"));
        return payment;
    }

    private PaymentInvoiceEntity invoice(
            UUID invoiceId,
            UUID createdByUserId,
            UUID recipientUserId,
            long amountUnits,
            String status
    ) {
        PaymentInvoiceEntity invoice = new PaymentInvoiceEntity();
        invoice.setId(invoiceId);
        invoice.setCreatedByUserId(createdByUserId);
        invoice.setRecipientUserId(recipientUserId);
        invoice.setAmountUnits(amountUnits);
        invoice.setCurrencyCode("XTR");
        invoice.setStatus(status);
        invoice.setCreatedAt(Instant.parse("2026-03-19T11:00:31Z"));
        invoice.setUpdatedAt(Instant.parse("2026-03-19T11:00:31Z"));
        return invoice;
    }

    private ChatMessageResponse message(UUID chatId, UUID messageId, UUID senderId) {
        return new ChatMessageResponse(
                chatId,
                messageId,
                null,
                senderId,
                "Moderator",
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                "Ready for publish",
                List.of(),
                null,
                null,
                false,
                null,
                null,
                null,
                Instant.parse("2026-03-19T12:30:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                "SENT",
                Instant.parse("2026-03-19T12:30:00Z"),
                null,
                null,
                null,
                null
        );
    }
}
