package com.alex.messenger.story;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.story.dto.CreateStoryAlbumRequest;
import com.alex.messenger.story.dto.CreateStoryHighlightRequest;
import com.alex.messenger.story.dto.CreateStoryLiveCommentRequest;
import com.alex.messenger.story.dto.CreateStoryRequest;
import com.alex.messenger.story.dto.GoLiveStoryRequest;
import com.alex.messenger.story.dto.StoryMentionRequest;
import com.alex.messenger.story.dto.StoryReactionRequest;
import com.alex.messenger.story.dto.StoryReplyRequest;
import com.alex.messenger.story.dto.UpdateStoryHighlightStoriesRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class StoryControllerMvcTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private StoryService storyService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new StoryController(featureFlagService, storyService, validator))
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void reactReturnsBadRequestForBlankReaction() throws Exception {
        mockMvc.perform(
                        post("/api/stories/{storyId}/reactions", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(new StoryReactionRequest(" ")))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void mentionReturnsBadRequestForMissingTargetUserId() throws Exception {
        mockMvc.perform(
                        post("/api/stories/{storyId}/mentions", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(new StoryMentionRequest(null, "hello")))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void replyReturnsBadRequestForBlankMessage() throws Exception {
        mockMvc.perform(
                        post("/api/stories/{storyId}/replies", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(new StoryReplyRequest(" ")))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void commentLiveReturnsBadRequestForNegativeDonationAmount() throws Exception {
        mockMvc.perform(
                        post("/api/stories/{storyId}/comments", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateStoryLiveCommentRequest("Thanks", -5L, "XTR")
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void commentLiveReturnsBadRequestWhenTextAndDonationMissing() throws Exception {
        mockMvc.perform(
                        post("/api/stories/{storyId}/comments", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateStoryLiveCommentRequest("   ", null, null)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void commentLiveReturnsBadRequestWhenCurrencyProvidedWithoutDonation() throws Exception {
        mockMvc.perform(
                        post("/api/stories/{storyId}/comments", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateStoryLiveCommentRequest("hello", null, "XTR")
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void goLiveReturnsBadRequestForInvalidDonationHookUrl() throws Exception {
        mockMvc.perform(
                        post("/api/stories/{storyId}/go-live", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new GoLiveStoryRequest(true, "stars", "xtr", "ftp://hook")
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void goLiveReturnsBadRequestWhenDonationsEnabledWithoutCurrency() throws Exception {
        mockMvc.perform(
                        post("/api/stories/{storyId}/go-live", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new GoLiveStoryRequest(true, "stars", null, "https://hooks.example/live")
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void createHighlightReturnsBadRequestForEmptyStoryIds() throws Exception {
        mockMvc.perform(
                        post("/api/stories/highlights")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateStoryHighlightRequest("Trips", null, null, List.of())
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void createHighlightReturnsBadRequestForNegativePosition() throws Exception {
        mockMvc.perform(
                        post("/api/stories/highlights")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateStoryHighlightRequest(
                                                "Trips",
                                                null,
                                                -1,
                                                List.of(UUID.randomUUID())
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void createHighlightReturnsBadRequestForCoverStoryOutsideSelection() throws Exception {
        mockMvc.perform(
                        post("/api/stories/highlights")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateStoryHighlightRequest(
                                                "Trips",
                                                UUID.randomUUID(),
                                                null,
                                                List.of(UUID.randomUUID())
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void createAlbumReturnsBadRequestForEmptyStoryIds() throws Exception {
        mockMvc.perform(
                        post("/api/stories/albums")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateStoryAlbumRequest("Spring", null, null, List.of(), null)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void createAlbumReturnsBadRequestForCoverStoryOutsideSelection() throws Exception {
        mockMvc.perform(
                        post("/api/stories/albums")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateStoryAlbumRequest(
                                                "Spring",
                                                UUID.randomUUID(),
                                                null,
                                                List.of(UUID.randomUUID()),
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void createAlbumReturnsBadRequestForBlankTitle() throws Exception {
        mockMvc.perform(
                        post("/api/stories/albums")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateStoryAlbumRequest("   ", null, null, List.of(UUID.randomUUID()), null)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void createAlbumReturnsBadRequestForNegativePosition() throws Exception {
        mockMvc.perform(
                        post("/api/stories/albums")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateStoryAlbumRequest("Spring", null, -1, List.of(UUID.randomUUID()), null)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void createReturnsBadRequestForCustomAudienceWithoutAllowedViewers() throws Exception {
        mockMvc.perform(
                        post("/api/stories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateStoryRequest(
                                                "Friends only",
                                                null,
                                                null,
                                                null,
                                                "CUSTOM",
                                                List.of(),
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void createReturnsBadRequestForSelectedUsersAudienceWithoutAllowedViewers() throws Exception {
        mockMvc.perform(
                        post("/api/stories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateStoryRequest(
                                                "Friends only",
                                                null,
                                                null,
                                                null,
                                                "SELECTED_USERS",
                                                List.of(),
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void addStoriesToHighlightReturnsBadRequestForNullStoryId() throws Exception {
        mockMvc.perform(
                        post("/api/stories/highlights/{highlightId}/stories", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpdateStoryHighlightStoriesRequest(Collections.singletonList(null), null)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void addStoriesToHighlightReturnsBadRequestWhenNoChangesProvided() throws Exception {
        mockMvc.perform(
                        post("/api/stories/highlights/{highlightId}/stories", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpdateStoryHighlightStoriesRequest(null, null)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void createReturnsBadRequestForNullAllowedViewerUserId() throws Exception {
        mockMvc.perform(
                        post("/api/stories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateStoryRequest(
                                                "Friends only",
                                                null,
                                                null,
                                                null,
                                                "SELECTED_USERS",
                                                Collections.singletonList(null),
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void createReturnsBadRequestForUnsupportedAudience() throws Exception {
        mockMvc.perform(
                        post("/api/stories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateStoryRequest(
                                                "Audience test",
                                                null,
                                                null,
                                                null,
                                                "TEAM_ONLY",
                                                List.of(),
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void createWithMediaReturnsBadRequestForCustomAudienceWithoutAllowedViewers() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "story.jpg", "image/jpeg", new byte[] {1, 2, 3});

        mockMvc.perform(
                        multipart("/api/stories")
                                .file(file)
                                .param("text", "Friends only")
                                .param("audience", "CUSTOM")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void createWithMediaReturnsBadRequestForUnsupportedAudience() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "story.jpg", "image/jpeg", new byte[] {1, 2, 3});

        mockMvc.perform(
                        multipart("/api/stories")
                                .file(file)
                                .param("text", "Audience test")
                                .param("audience", "TEAM_ONLY")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }

    @Test
    void createWithMediaReturnsBadRequestForInvalidVideoDuration() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "story.mp4", "video/mp4", new byte[] {1, 2, 3});

        mockMvc.perform(
                        multipart("/api/stories")
                                .file(file)
                                .param("text", "Video story")
                                .param("durationMs", "0")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, storyService);
    }
}
