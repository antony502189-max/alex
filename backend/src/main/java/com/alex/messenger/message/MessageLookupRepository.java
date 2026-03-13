package com.alex.messenger.message;

import java.util.UUID;
import org.springframework.data.cassandra.repository.CassandraRepository;

public interface MessageLookupRepository extends CassandraRepository<MessageLookupEntity, UUID> {
}
