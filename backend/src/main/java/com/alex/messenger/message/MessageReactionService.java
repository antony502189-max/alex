package com.alex.messenger.message;

import com.alex.messenger.message.dto.MessageReactionSummary;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageReactionService {

    private final MessageReactionRepository messageReactionRepository;

    public List<MessageReactionSummary> getSummaries(UUID messageId) {
        return summarize(messageReactionRepository.findAllByIdMessageId(messageId));
    }

    public Map<UUID, List<MessageReactionSummary>> getSummaries(Collection<UUID> messageIds) {
        return messageReactionRepository.findAllByIdMessageIdIn(messageIds).stream()
                .collect(Collectors.groupingBy(
                        entity -> entity.getId().getMessageId(),
                        Collectors.collectingAndThen(Collectors.toList(), this::summarize)
                ));
    }

    public void toggle(UUID messageId, UUID userId, String emoji) {
        MessageReactionId id = new MessageReactionId(messageId, userId, emoji);
        if (messageReactionRepository.existsById(id)) {
            messageReactionRepository.deleteById(id);
            return;
        }

        MessageReactionEntity entity = new MessageReactionEntity();
        entity.setId(id);
        messageReactionRepository.save(entity);
    }

    private List<MessageReactionSummary> summarize(List<MessageReactionEntity> entities) {
        return entities.stream()
                .collect(Collectors.groupingBy(
                        entity -> entity.getId().getEmoji(),
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new MessageReactionSummary(entry.getKey(), entry.getValue()))
                .toList();
    }
}
