package com.alex.messenger.media;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final ProfilePhotoService profilePhotoService;

    @GetMapping("/download")
    public ResponseEntity<Void> download(
            @RequestParam(name = PhotoAccessTokenService.QUERY_PARAMETER) String accessToken
    ) {
        PhotoDownloadResult result = profilePhotoService.downloadByAccessToken(accessToken);
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                .location(URI.create(result.redirectUrl()))
                .build();
    }
}
