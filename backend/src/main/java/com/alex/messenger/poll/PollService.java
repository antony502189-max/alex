package com.alex.messenger.poll;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.message.dto.CreatePollMessageRequest;
import com.alex.messenger.poll.dto.PollOptionResponse;
import com.alex.messenger.poll.dto.PollResponse;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PollService {

    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;

    @Transactional
    public PollEntity createPoll(ChatEntity chat, UUID creatorId, CreatePollMessageRequest request) {
        String question = request.question().trim();
        if (question.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Poll question is blank");
        }

        List<String> normalizedOptions = request.options().stream()
                .map(option -> option != null ? option.trim() : "")
                .filter(option -> !option.isBlank())
                .distinct()
                .toList();

        if (normalizedOptions.size() < 2 || normalizedOptions.size() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Poll must contain 2 to 10 distinct options");
        }

        boolean quiz = Boolean.TRUE.equals(request.quiz());
        boolean multipleChoice = request.multipleChoice() && !quiz;
        validateQuizOptions(quiz, request.correctOptionIndex(), normalizedOptions.size(), multipleChoice);
        validateCloseAt(request.closeAt());

        PollEntity poll = new PollEntity();
        poll.setChatId(chat.getId());
        poll.setCreatedByUserId(creatorId);
        poll.setQuestion(question);
        poll.setMultipleChoice(multipleChoice);
        poll.setQuiz(quiz);
        poll.setExplanation(normalizeExplanation(request.explanation()));
        poll.setAnonymousVotes(request.anonymousVotes() == null || request.anonymousVotes());
        poll.setCloseAt(request.closeAt());
        PollEntity savedPoll = pollRepository.save(poll);

        UUID correctOptionId = null;
        for (int index = 0; index < normalizedOptions.size(); index++) {
            PollOptionEntity option = new PollOptionEntity();
            option.setPollId(savedPoll.getId());
            option.setOptionText(normalizedOptions.get(index));
            option.setPosition(index);
            PollOptionEntity savedOption = pollOptionRepository.save(option);
            if (quiz && request.correctOptionIndex() != null && request.correctOptionIndex() == index) {
                correctOptionId = savedOption.getId();
            }
        }

        if (correctOptionId != null) {
            savedPoll.setCorrectOptionId(correctOptionId);
            savedPoll = pollRepository.save(savedPoll);
        }

        return savedPoll;
    }

    @Transactional
    public PollResponse vote(UUID requesterId, UUID pollId, List<UUID> optionIds) {
        PollEntity poll = getPoll(pollId);
        if (isClosed(poll, Instant.now())) {
            if (!poll.isClosed()) {
                poll.setClosed(true);
                pollRepository.save(poll);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Poll is closed");
        }

        Set<UUID> normalizedOptionIds = optionIds == null
                ? Set.of()
                : new LinkedHashSet<>(optionIds);

        List<PollOptionEntity> options = pollOptionRepository.findAllByPollIdOrderByPositionAsc(pollId);
        Map<UUID, PollOptionEntity> optionsById = options.stream()
                .collect(Collectors.toMap(PollOptionEntity::getId, Function.identity()));

        for (UUID optionId : normalizedOptionIds) {
            if (!optionsById.containsKey(optionId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Poll option does not belong to this poll");
            }
        }

        if (!poll.isMultipleChoice() && normalizedOptionIds.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This poll accepts only one option");
        }

        pollVoteRepository.deleteAllByIdPollIdAndIdUserId(pollId, requesterId);
        for (UUID optionId : normalizedOptionIds) {
            pollVoteRepository.save(newVote(pollId, requesterId, optionId));
        }

        return getPollResponse(pollId, requesterId);
    }

    @Transactional(readOnly = true)
    public PollResponse getPollResponse(UUID pollId, UUID requesterId) {
        if (pollId == null) {
            return null;
        }

        PollEntity poll = getPoll(pollId);
        List<PollOptionEntity> options = pollOptionRepository.findAllByPollIdOrderByPositionAsc(pollId);
        List<PollVoteEntity> votes = pollVoteRepository.findAllByIdPollId(pollId);
        Map<UUID, Long> voteCounts = votes.stream()
                .collect(Collectors.groupingBy(vote -> vote.getId().getOptionId(), Collectors.counting()));
        Set<UUID> selectedByMe = votes.stream()
                .filter(vote -> vote.getId().getUserId().equals(requesterId))
                .map(vote -> vote.getId().getOptionId())
                .collect(Collectors.toSet());
        int totalVoters = Math.toIntExact(
                votes.stream()
                        .map(vote -> vote.getId().getUserId())
                        .distinct()
                        .count()
        );
        boolean resolvedClosed = isClosed(poll, Instant.now());

        return new PollResponse(
                poll.getId(),
                poll.getQuestion(),
                poll.isMultipleChoice(),
                poll.isQuiz(),
                poll.isQuiz() && resolvedClosed ? poll.getCorrectOptionId() : null,
                poll.getExplanation(),
                poll.isAnonymousVotes(),
                poll.getCloseAt(),
                resolvedClosed,
                totalVoters,
                options.stream()
                        .map(option -> new PollOptionResponse(
                                option.getId(),
                                option.getOptionText(),
                                voteCounts.getOrDefault(option.getId(), 0L).intValue(),
                                selectedByMe.contains(option.getId())
                        ))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public PollEntity getPollEntity(UUID pollId) {
        return getPoll(pollId);
    }

    @Transactional
    public PollResponse closePoll(UUID pollId, UUID requesterId) {
        PollEntity poll = getPoll(pollId);
        if (!poll.isClosed()) {
            poll.setClosed(true);
            pollRepository.save(poll);
        }
        return getPollResponse(pollId, requesterId);
    }

    private PollEntity getPoll(UUID pollId) {
        return pollRepository.findById(pollId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Poll not found"));
    }

    private boolean isClosed(PollEntity poll, Instant now) {
        return poll.isClosed() || (poll.getCloseAt() != null && !poll.getCloseAt().isAfter(now));
    }

    private String normalizeExplanation(String explanation) {
        if (explanation == null) {
            return null;
        }
        String normalized = explanation.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private void validateQuizOptions(boolean quiz, Integer correctOptionIndex, int optionCount, boolean multipleChoice) {
        if (!quiz) {
            return;
        }
        if (multipleChoice) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quiz polls cannot be multiple choice");
        }
        if (correctOptionIndex == null || correctOptionIndex < 0 || correctOptionIndex >= optionCount) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quiz polls require a valid correctOptionIndex");
        }
    }

    private void validateCloseAt(Instant closeAt) {
        if (closeAt != null && !closeAt.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Poll closeAt must be in the future");
        }
    }

    private PollVoteEntity newVote(UUID pollId, UUID userId, UUID optionId) {
        PollVoteEntity vote = new PollVoteEntity();
        vote.setId(new PollVoteId(pollId, userId, optionId));
        return vote;
    }
}
