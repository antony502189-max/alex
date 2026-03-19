package com.alex.messenger.chat.suggested;

import com.alex.messenger.attachment.AttachmentService;
import com.alex.messenger.chat.ChatAdminLogService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberEntity;
import com.alex.messenger.chat.ChatMemberRepository;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.suggested.dto.CreateSuggestedPostRequest;
import com.alex.messenger.chat.suggested.dto.DeclineSuggestedPostRequest;
import com.alex.messenger.chat.suggested.dto.SuggestedPostPaymentResponse;
import com.alex.messenger.chat.suggested.dto.SuggestedPostResponse;
import com.alex.messenger.crypto.ChatEncryptionService;
import com.alex.messenger.crypto.EncryptedPayload;
import com.alex.messenger.message.MessageContentCodec;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.MessageTextContent;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.MessageTextEntityPayload;
import com.alex.messenger.message.dto.SendMessageRequest;
import com.alex.messenger.payments.PaymentInvoiceEntity;
import com.alex.messenger.payments.PaymentInvoiceRepository;
import com.alex.messenger.payments.PaymentService;
import com.alex.messenger.payments.dto.CreateInvoiceRequest;
import com.alex.messenger.payments.dto.PaymentInvoiceResponse;
import com.alex.messenger.sticker.StickerService;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ChannelSuggestedPostService {

    private record ChannelAccess(
            ChatEntity chat,
            boolean member,
            boolean canModerate
    ) {
    }

    private static final List<String> VISIBLE_STATUSES = List.of(
            "SUBMITTED",
            "PAYMENT_PENDING",
            "APPROVED",
            "DECLINED"
    );

    private static final String DEFAULT_PAYMENT_CURRENCY = "XTR";

    private final SuggestedPostRepository suggestedPostRepository;
    private final SuggestedPostPaymentRepository suggestedPostPaymentRepository;
    private final ChatService chatService;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatAdminLogService chatAdminLogService;
    private final AttachmentService attachmentService;
    private final StickerService stickerService;
    private final MessageService messageService;
    private final MessageContentCodec messageContentCodec;
    private final ChatEncryptionService chatEncryptionService;
    private final PaymentService paymentService;
    private final PaymentInvoiceRepository paymentInvoiceRepository;

    @Transactional
    public List<SuggestedPostResponse> listSuggestedPosts(
            UUID requesterId,
            UUID chatId,
            String status,
            int limit
    ) {
        ChannelAccess access = resolveAccess(chatId, requesterId);
        String normalizedStatus = normalizeStatus(status);
        int normalizedLimit = Math.min(Math.max(limit, 1), 100);
        UUID submittedByUserId = access.canModerate() ? null : requesterId;

        List<SuggestedPostEntity> posts = suggestedPostRepository.findVisible(
                access.chat().getId(),
                submittedByUserId,
                normalizedStatus,
                PageRequest.of(0, normalizedLimit)
        );
        Map<UUID, SuggestedPostPaymentEntity> paymentsBySuggestedPostId = loadPaymentsBySuggestedPostId(posts);
        return posts.stream()
                .map(post -> toResponse(access.chat(), post, paymentsBySuggestedPostId.get(post.getId())))
                .toList();
    }

    @Transactional
    public SuggestedPostResponse createSuggestedPost(
            UUID requesterId,
            UUID chatId,
            CreateSuggestedPostRequest request
    ) {
        ChannelAccess access = resolveAccess(chatId, requesterId);
        MessageTextContent content = messageContentCodec.normalize(
                request != null ? request.text() : null,
                request != null && request.entities() != null ? request.entities() : List.<MessageTextEntityPayload>of(),
                request != null ? request.messageType() : null,
                request != null ? request.caption() : null,
                request != null ? request.location() : null,
                request != null ? request.contactCard() : null,
                null,
                request != null ? request.silent() : null
        );
        List<UUID> requestedAttachmentIds = normalizeAttachmentIds(request != null ? request.attachmentIds() : null);
        UUID stickerId = request != null ? request.stickerId() : null;
        stickerService.assertStickerExists(stickerId);
        ensureMessageIsPresent(content, requestedAttachmentIds, stickerId);

        List<UUID> clonedAttachmentIds = requestedAttachmentIds.isEmpty()
                ? List.of()
                : attachmentService.cloneAttachmentsToChatForSystem(requesterId, access.chat().getId(), requestedAttachmentIds);
        EncryptedPayload payload = chatEncryptionService.encrypt(
                access.chat().getId(),
                messageContentCodec.encode(content)
        );

        Long requestedAmountUnits = normalizeRequestedAmount(request != null ? request.requestedAmountUnits() : null);
        SuggestedPostEntity suggestedPost = new SuggestedPostEntity();
        suggestedPost.setChatId(access.chat().getId());
        suggestedPost.setSubmittedByUserId(requesterId);
        suggestedPost.setCiphertext(payload.ciphertext());
        suggestedPost.setNonce(payload.nonce());
        suggestedPost.setKeyVersion(payload.keyVersion());
        suggestedPost.setStickerId(stickerId);
        suggestedPost.setAttachmentIds(serializeAttachmentIds(clonedAttachmentIds));
        suggestedPost.setPaymentAmountUnits(requestedAmountUnits);
        suggestedPost.setPaymentCurrencyCode(requestedAmountUnits != null ? DEFAULT_PAYMENT_CURRENCY : null);
        suggestedPost.setStatus(requestedAmountUnits != null ? "PAYMENT_PENDING" : "SUBMITTED");
        SuggestedPostEntity savedSuggestedPost = suggestedPostRepository.save(suggestedPost);

        SuggestedPostPaymentEntity payment = null;
        if (requestedAmountUnits != null) {
            payment = createSuggestedPostPayment(access.chat(), savedSuggestedPost, requesterId, content, requestedAmountUnits);
        }

        return toResponse(access.chat(), savedSuggestedPost, payment);
    }

    @Transactional
    public SuggestedPostResponse approveSuggestedPost(UUID requesterId, UUID chatId, UUID postId) {
        ChannelAccess access = resolveAccess(chatId, requesterId);
        ensureCanModerate(access);
        SuggestedPostEntity suggestedPost = getSuggestedPost(chatId, postId);
        ensureActionable(suggestedPost);

        SuggestedPostPaymentEntity payment = syncPaymentSnapshot(
                suggestedPostPaymentRepository.findBySuggestedPostId(suggestedPost.getId()).orElse(null)
        );
        if (payment != null && !"PAID".equals(payment.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Suggested post payment must be completed before approval"
            );
        }

        MessageTextContent content = decodeContent(access.chat(), suggestedPost);
        ChatMessageResponse published = messageService.sendMessage(
                requesterId,
                new SendMessageRequest(
                        access.chat().getId(),
                        null,
                        null,
                        null,
                        content.text(),
                        content.caption(),
                        content.messageType(),
                        content.entities(),
                        content.location(),
                        content.contactCard(),
                        deserializeAttachmentIds(suggestedPost.getAttachmentIds()),
                        suggestedPost.getStickerId(),
                        content.silent(),
                        null
                )
        );

        suggestedPost.setStatus("APPROVED");
        suggestedPost.setReviewedByUserId(requesterId);
        suggestedPost.setPublishedMessageId(published.messageId());
        suggestedPost.setApprovedAt(Instant.now());
        suggestedPost.setDeclinedAt(null);
        suggestedPost.setDeclineReason(null);
        SuggestedPostEntity savedSuggestedPost = suggestedPostRepository.save(suggestedPost);
        chatAdminLogService.log(
                access.chat().getId(),
                requesterId,
                suggestedPost.getSubmittedByUserId(),
                "SUGGESTED_POST_APPROVED",
                "Approved a suggested post",
                published.messageId(),
                null
        );
        return toResponse(access.chat(), savedSuggestedPost, payment);
    }

    @Transactional
    public SuggestedPostResponse declineSuggestedPost(
            UUID requesterId,
            UUID chatId,
            UUID postId,
            DeclineSuggestedPostRequest request
    ) {
        ChannelAccess access = resolveAccess(chatId, requesterId);
        ensureCanModerate(access);
        SuggestedPostEntity suggestedPost = getSuggestedPost(chatId, postId);
        ensureActionable(suggestedPost);

        SuggestedPostPaymentEntity payment = syncPaymentSnapshot(
                suggestedPostPaymentRepository.findBySuggestedPostId(suggestedPost.getId()).orElse(null)
        );
        suggestedPost.setStatus("DECLINED");
        suggestedPost.setReviewedByUserId(requesterId);
        suggestedPost.setDeclinedAt(Instant.now());
        suggestedPost.setApprovedAt(null);
        suggestedPost.setPublishedMessageId(null);
        suggestedPost.setDeclineReason(normalizeDeclineReason(request != null ? request.reason() : null));
        SuggestedPostEntity savedSuggestedPost = suggestedPostRepository.save(suggestedPost);
        chatAdminLogService.log(
                access.chat().getId(),
                requesterId,
                suggestedPost.getSubmittedByUserId(),
                "SUGGESTED_POST_DECLINED",
                "Declined a suggested post",
                null,
                null
        );
        return toResponse(access.chat(), savedSuggestedPost, payment);
    }

    private ChannelAccess resolveAccess(UUID chatId, UUID requesterId) {
        ChatEntity chat = chatService.getChat(chatId);
        if (!"CHANNEL".equals(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suggested posts are available only for channels");
        }

        boolean member = chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId);
        if (!member) {
            if (chat.getPublicUsername() == null || chat.getPublicUsername().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Suggested posts are available only for public channels or channel members"
                );
            }
            return new ChannelAccess(chat, false, false);
        }

        ChatMemberEntity membership = chatService.getMembership(chatId, requesterId);
        return new ChannelAccess(chat, true, canModerate(membership));
    }

    private void ensureCanModerate(ChannelAccess access) {
        if (!access.member() || !access.canModerate()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only channel owners or admins with message moderation rights can review suggested posts"
            );
        }
    }

    private SuggestedPostEntity getSuggestedPost(UUID chatId, UUID postId) {
        return suggestedPostRepository.findByIdAndChatId(postId, chatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Suggested post not found"));
    }

    private SuggestedPostPaymentEntity createSuggestedPostPayment(
            ChatEntity chat,
            SuggestedPostEntity suggestedPost,
            UUID requesterId,
            MessageTextContent content,
            long amountUnits
    ) {
        UUID ownerUserId = resolveChannelOwnerUserId(chat.getId());
        if (requesterId.equals(ownerUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Channel owner cannot require payment for their own suggested post"
            );
        }

        PaymentInvoiceResponse invoice = paymentService.createInvoice(
                requesterId,
                new CreateInvoiceRequest(
                        ownerUserId,
                        buildInvoiceTitle(chat),
                        buildInvoiceDescription(chat, content),
                        amountUnits,
                        null,
                        buildInvoiceMetadata(suggestedPost)
                )
        );

        SuggestedPostPaymentEntity payment = new SuggestedPostPaymentEntity();
        payment.setSuggestedPostId(suggestedPost.getId());
        payment.setInvoiceId(invoice.invoiceId());
        payment.setPaymentIntentId(null);
        payment.setPayerUserId(requesterId);
        payment.setRecipientUserId(ownerUserId);
        payment.setAmountUnits(invoice.amountUnits());
        payment.setCurrencyCode(invoice.currencyCode());
        payment.setStatus(invoice.status());
        return suggestedPostPaymentRepository.save(payment);
    }

    private UUID resolveChannelOwnerUserId(UUID chatId) {
        return chatMemberRepository.findByIdChatIdAndRole(chatId, "OWNER")
                .map(member -> member.getId().getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Channel owner not found"));
    }

    private String buildInvoiceTitle(ChatEntity chat) {
        String channelLabel = chat.getTitle() != null && !chat.getTitle().isBlank()
                ? chat.getTitle().trim()
                : "channel";
        return truncate(("Suggested post for " + channelLabel).trim(), 120);
    }

    private String buildInvoiceDescription(ChatEntity chat, MessageTextContent content) {
        String preview = messageContentCodec.buildSearchText(content).trim();
        StringBuilder builder = new StringBuilder();
        if (chat.getPublicUsername() != null && !chat.getPublicUsername().isBlank()) {
            builder.append("Suggested post submission to @").append(chat.getPublicUsername().trim());
        } else if (chat.getTitle() != null && !chat.getTitle().isBlank()) {
            builder.append("Suggested post submission to ").append(chat.getTitle().trim());
        } else {
            builder.append("Suggested post submission");
        }
        if (!preview.isBlank()) {
            builder.append(": ").append(preview);
        }
        return truncate(builder.toString(), 500);
    }

    private Map<String, String> buildInvoiceMetadata(SuggestedPostEntity suggestedPost) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("type", "SUGGESTED_POST");
        metadata.put("suggestedPostId", suggestedPost.getId().toString());
        metadata.put("chatId", suggestedPost.getChatId().toString());
        return metadata;
    }

    private void ensureActionable(SuggestedPostEntity suggestedPost) {
        if ("APPROVED".equals(suggestedPost.getStatus()) || "DECLINED".equals(suggestedPost.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Suggested post has already been reviewed"
            );
        }
    }

    private SuggestedPostPaymentEntity syncPaymentSnapshot(SuggestedPostPaymentEntity payment) {
        if (payment == null) {
            return null;
        }
        PaymentInvoiceEntity invoice = paymentInvoiceRepository.findById(payment.getInvoiceId()).orElse(null);
        if (invoice == null) {
            return payment;
        }

        boolean changed = false;
        if (!Objects.equals(payment.getStatus(), invoice.getStatus())) {
            payment.setStatus(invoice.getStatus());
            changed = true;
        }
        if (!Objects.equals(payment.getCurrencyCode(), invoice.getCurrencyCode())) {
            payment.setCurrencyCode(invoice.getCurrencyCode());
            changed = true;
        }
        if (!Objects.equals(payment.getAmountUnits(), invoice.getAmountUnits())) {
            payment.setAmountUnits(invoice.getAmountUnits());
            changed = true;
        }
        if (changed) {
            return suggestedPostPaymentRepository.save(payment);
        }
        return payment;
    }

    private Map<UUID, SuggestedPostPaymentEntity> loadPaymentsBySuggestedPostId(Collection<SuggestedPostEntity> posts) {
        if (posts.isEmpty()) {
            return Map.of();
        }
        Map<UUID, SuggestedPostPaymentEntity> paymentsBySuggestedPostId = suggestedPostPaymentRepository.findAllBySuggestedPostIdIn(
                posts.stream().map(SuggestedPostEntity::getId).toList()
        ).stream().collect(Collectors.toMap(SuggestedPostPaymentEntity::getSuggestedPostId, payment -> payment));

        if (paymentsBySuggestedPostId.isEmpty()) {
            return Map.of();
        }

        Map<UUID, PaymentInvoiceEntity> invoicesById = paymentInvoiceRepository.findAllById(
                paymentsBySuggestedPostId.values().stream().map(SuggestedPostPaymentEntity::getInvoiceId).toList()
        ).stream().collect(Collectors.toMap(PaymentInvoiceEntity::getId, invoice -> invoice));

        List<SuggestedPostPaymentEntity> dirtyPayments = paymentsBySuggestedPostId.values().stream()
                .map(payment -> mergeInvoiceSnapshot(payment, invoicesById.get(payment.getInvoiceId())))
                .filter(Objects::nonNull)
                .toList();
        if (!dirtyPayments.isEmpty()) {
            suggestedPostPaymentRepository.saveAll(dirtyPayments);
            dirtyPayments.forEach(payment -> paymentsBySuggestedPostId.put(payment.getSuggestedPostId(), payment));
        }
        return paymentsBySuggestedPostId;
    }

    private SuggestedPostPaymentEntity mergeInvoiceSnapshot(
            SuggestedPostPaymentEntity payment,
            PaymentInvoiceEntity invoice
    ) {
        if (invoice == null) {
            return null;
        }
        boolean changed = false;
        if (!Objects.equals(payment.getStatus(), invoice.getStatus())) {
            payment.setStatus(invoice.getStatus());
            changed = true;
        }
        if (!Objects.equals(payment.getCurrencyCode(), invoice.getCurrencyCode())) {
            payment.setCurrencyCode(invoice.getCurrencyCode());
            changed = true;
        }
        if (!Objects.equals(payment.getAmountUnits(), invoice.getAmountUnits())) {
            payment.setAmountUnits(invoice.getAmountUnits());
            changed = true;
        }
        return changed ? payment : null;
    }

    private SuggestedPostResponse toResponse(
            ChatEntity chat,
            SuggestedPostEntity suggestedPost,
            SuggestedPostPaymentEntity payment
    ) {
        MessageTextContent content = decodeContent(chat, suggestedPost);
        return new SuggestedPostResponse(
                suggestedPost.getId(),
                suggestedPost.getChatId(),
                suggestedPost.getSubmittedByUserId(),
                suggestedPost.getStatus(),
                suggestedPost.getReviewedByUserId(),
                suggestedPost.getDeclineReason(),
                suggestedPost.getPublishedMessageId(),
                content.text(),
                content.entities(),
                content.messageType(),
                content.caption(),
                content.silent(),
                content.location(),
                content.contactCard(),
                suggestedPost.getStickerId(),
                deserializeAttachmentIds(suggestedPost.getAttachmentIds()),
                payment != null ? toPaymentResponse(payment) : null,
                suggestedPost.getCreatedAt(),
                suggestedPost.getUpdatedAt(),
                suggestedPost.getApprovedAt(),
                suggestedPost.getDeclinedAt()
        );
    }

    private SuggestedPostPaymentResponse toPaymentResponse(SuggestedPostPaymentEntity payment) {
        return new SuggestedPostPaymentResponse(
                payment.getId(),
                payment.getInvoiceId(),
                payment.getPaymentIntentId(),
                payment.getPayerUserId(),
                payment.getRecipientUserId(),
                payment.getAmountUnits() != null ? payment.getAmountUnits() : 0L,
                payment.getCurrencyCode(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }

    private MessageTextContent decodeContent(ChatEntity chat, SuggestedPostEntity suggestedPost) {
        String plaintext = chatEncryptionService.decrypt(
                chat.getId(),
                suggestedPost.getCiphertext(),
                suggestedPost.getNonce(),
                suggestedPost.getKeyVersion()
        );
        return messageContentCodec.decode(plaintext);
    }

    private boolean canModerate(ChatMemberEntity membership) {
        return membership != null
                && ("OWNER".equals(membership.getRole())
                || ("ADMIN".equals(membership.getRole()) && Boolean.TRUE.equals(membership.getCanManageMessages())));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        if (!VISIBLE_STATUSES.contains(normalizedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported suggested post status");
        }
        return normalizedStatus;
    }

    private List<UUID> normalizeAttachmentIds(List<UUID> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(attachmentIds));
    }

    private void ensureMessageIsPresent(MessageTextContent content, List<UUID> attachmentIds, UUID stickerId) {
        boolean hasText = content != null && (!content.text().isBlank() || content.caption() != null);
        boolean hasStructuredPayload = content != null && (content.location() != null || content.contactCard() != null);
        if (!hasText && !hasStructuredPayload && attachmentIds.isEmpty() && stickerId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Suggested post must contain text, attachments or a sticker"
            );
        }
    }

    private String normalizeDeclineReason(String reason) {
        if (reason == null) {
            return null;
        }
        String normalized = reason.trim();
        if (normalized.isBlank()) {
            return null;
        }
        return truncate(normalized, 500);
    }

    private Long normalizeRequestedAmount(Long requestedAmountUnits) {
        if (requestedAmountUnits == null) {
            return null;
        }
        if (requestedAmountUnits <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suggested post payment amount must be positive");
        }
        return requestedAmountUnits;
    }

    private String serializeAttachmentIds(List<UUID> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return "";
        }
        return attachmentIds.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));
    }

    private List<UUID> deserializeAttachmentIds(String serializedAttachmentIds) {
        if (serializedAttachmentIds == null || serializedAttachmentIds.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(serializedAttachmentIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(UUID::fromString)
                .toList();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
