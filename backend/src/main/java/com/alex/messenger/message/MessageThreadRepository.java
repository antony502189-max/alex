package com.alex.messenger.message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;

public interface MessageThreadRepository extends CassandraRepository<MessageThreadEntity, MessageThreadPrimaryKey> {

    @Query("SELECT * FROM messages_by_thread_v1 WHERE thread_root_message_id = ?0 LIMIT ?1")
    List<MessageThreadEntity> findRecentByThreadRootMessageId(UUID threadRootMessageId, int limit);

    @Query("SELECT * FROM messages_by_thread_v1 WHERE thread_root_message_id = ?0 AND message_id < maxTimeuuid(?1) LIMIT ?2")
    List<MessageThreadEntity> findRecentByThreadRootMessageIdBefore(UUID threadRootMessageId, Instant before, int limit);

    @Query("SELECT * FROM messages_by_thread_v1 WHERE thread_root_message_id = ?0")
    List<MessageThreadEntity> findAllByThreadRootMessageId(UUID threadRootMessageId);
}
