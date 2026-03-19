package com.alex.messenger.search;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicPostSearchIndexRepository extends JpaRepository<PublicPostSearchIndexEntity, UUID> {

    void deleteByChatId(UUID chatId);

    @Query("""
        select p
        from PublicPostSearchIndexEntity p
        where p.searchCorpus like concat('%', :query, '%')
        order by p.createdAt desc
        """)
    List<PublicPostSearchIndexEntity> search(@Param("query") String query, Pageable pageable);
}
