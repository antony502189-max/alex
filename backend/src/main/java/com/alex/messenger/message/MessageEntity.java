package com.alex.messenger.message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Getter
@Setter
@NoArgsConstructor
@Table("messages_by_chat_v2")
public class MessageEntity {

    @PrimaryKey
    private MessagePrimaryKey key;

    @Column("created_at")
    private Instant createdAt;

    @Column("sender_id")
    private UUID senderId;

    @Column("recipient_id")
    private UUID recipientId;

    @Column("via_bot_user_id")
    private UUID viaBotUserId;

    @Column("topic_id")
    private UUID topicId;

    @Column("thread_root_message_id")
    private UUID threadRootMessageId;

    @Column("discussion_chat_id")
    private UUID discussionChatId;

    @Column("discussion_root_message_id")
    private UUID discussionRootMessageId;

    @Column("ciphertext")
    private String ciphertext;

    @Column("nonce")
    private String nonce;

    @Column("key_version")
    private Integer keyVersion;

    @Column("reply_to_message_id")
    private UUID replyToMessageId;

    @Column("forwarded_from_chat_id")
    private UUID forwardedFromChatId;

    @Column("forwarded_from_message_id")
    private UUID forwardedFromMessageId;

    @Column("poll_id")
    private UUID pollId;

    @Column("sticker_id")
    private UUID stickerId;

    @Column("attachment_ids")
    private List<UUID> attachmentIds;

    @Column("delivery_status")
    private String deliveryStatus;

    @Column("delivered_at")
    private Instant deliveredAt;

    @Column("read_at")
    private Instant readAt;

    @Column("expires_at")
    private Instant expiresAt;

    @Column("edited_at")
    private Instant editedAt;

    @Column("deleted_at")
    private Instant deletedAt;
}
