package com.alex.messenger.media;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PhotoControllerMvcTest {

    @Mock
    private ProfilePhotoService profilePhotoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PhotoController(profilePhotoService)).build();
    }

    @Test
    void downloadReturnsBadRequestWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get("/api/photos/download"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(profilePhotoService);
    }

    @Test
    void downloadRedirectsToResolvedPhotoUrl() throws Exception {
        when(profilePhotoService.downloadByAccessToken("photo-token"))
                .thenReturn(new PhotoDownloadResult("https://cdn.example/users/u1/photo.jpg"));

        mockMvc.perform(
                        get("/api/photos/download")
                                .param(PhotoAccessTokenService.QUERY_PARAMETER, "photo-token")
                )
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("https://cdn.example/users/u1/photo.jpg"));
    }
}
