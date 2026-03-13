package com.alex.messenger.poll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.message.dto.CreatePollMessageRequest;
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
class PollServiceTest {

    @Mock
    private PollRepository pollRepository;

    @Mock
    private PollOptionRepository pollOptionRepository;

    @Mock
    private PollVoteRepository pollVoteRepository;

    private PollService pollService;

    @BeforeEach
    void setUp() {
        pollService = new PollService(pollRepository, pollOptionRepository, pollVoteRepository);
    }

    @Test
    void createQuizPollStoresCorrectOptionId() {
        UUID pollId = UUID.randomUUID();
        ChatEntity chat = new ChatEntity();
        chat.setId(UUID.randomUUID());

        when(pollRepository.save(any(PollEntity.class))).thenAnswer(invocation -> {
            PollEntity poll = invocation.getArgument(0);
            if (poll.getId() == null) {
                poll.setId(pollId);
            }
            return poll;
        });
        when(pollOptionRepository.save(any(PollOptionEntity.class))).thenAnswer(invocation -> {
            PollOptionEntity option = invocation.getArgument(0);
            option.setId(UUID.randomUUID());
            return option;
        });

        PollEntity result = pollService.createPoll(
                chat,
                UUID.randomUUID(),
                new CreatePollMessageRequest(
                        chat.getId(),
                        null,
                        null,
                        null,
                        "Quiz",
                        List.of("A", "B", "C"),
                        false,
                        true,
                        1,
                        "Because",
                        false,
                        Instant.now().plusSeconds(3600),
                        null
                )
        );

        assertThat(result.isQuiz()).isTrue();
        assertThat(result.isMultipleChoice()).isFalse();
        assertThat(result.getCorrectOptionId()).isNotNull();
        assertThat(result.isAnonymousVotes()).isFalse();
    }

    @Test
    void voteRejectsPollClosedByCloseAt() {
        UUID pollId = UUID.randomUUID();
        PollEntity poll = new PollEntity();
        poll.setId(pollId);
        poll.setCloseAt(Instant.now().minusSeconds(60));
        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        when(pollRepository.save(any(PollEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseStatusException exception = catchThrowableOfType(
                () -> pollService.vote(UUID.randomUUID(), pollId, List.of(UUID.randomUUID())),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
