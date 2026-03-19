package com.alex.messenger.message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;

public interface MessageRepository extends CassandraRepository<MessageEntity, MessagePrimaryKey> {

    @Query("SELECT * FROM messages_by_chat_v2 WHERE chat_id = ?0 LIMIT ?1")
    List<MessageEntity> findRecentByChatId(UUID chatId, int limit);

    @Query("SELECT * FROM messages_by_chat_v2 WHERE chat_id = ?0 AND message_id < maxTimeuuid(?1) LIMIT ?2")
    List<MessageEntity> findRecentByChatIdBefore(UUID chatId, Instant before, int limit);

    @Query("SELECT * FROM messages_by_chat_v2 WHERE chat_id = ?0")
    List<MessageEntity> findAllByChatId(UUID chatId);

    @Query("SELECT * FROM messages_by_chat_v2 WHERE chat_id = ?0 AND message_id <= ?1")
    List<MessageEntity> findAllByChatIdUpToMessageId(UUID chatId, UUID messageId);

    @Query("SELECT * FROM messages_by_chat_v2 WHERE chat_id = ?0 AND message_id > ?1")
    List<MessageEntity> findAllByChatIdAfterMessageId(UUID chatId, UUID messageId);

    @Query("""
            SELECT * FROM messages_by_chat_v2
            WHERE chat_id = ?0
              AND message_id >= minTimeuuid(?1)
              AND message_id < maxTimeuuid(?2)
            """)
    List<MessageEntity> findAllByChatIdWithinRange(UUID chatId, Instant fromInclusive, Instant toExclusive);
}
