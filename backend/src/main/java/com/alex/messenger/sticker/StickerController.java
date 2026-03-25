package com.alex.messenger.sticker;

import com.alex.messenger.sticker.dto.StickerPackResponse;
import com.alex.messenger.sticker.dto.StickerResponse;
import com.alex.messenger.shared.CurrentUser;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stickers")
@RequiredArgsConstructor
public class StickerController {

    private final StickerService stickerService;

    @GetMapping("/packs")
    public ResponseEntity<List<StickerPackResponse>> listPacks() {
        return ResponseEntity.ok(stickerService.listPacks());
    }

    @GetMapping("/recent")
    public ResponseEntity<List<StickerResponse>> listRecent() {
        return ResponseEntity.ok(stickerService.listRecent(CurrentUser.id()));
    }

    @GetMapping("/favorites")
    public ResponseEntity<List<StickerResponse>> listFavorites() {
        return ResponseEntity.ok(stickerService.listFavorites(CurrentUser.id()));
    }

    @PutMapping("/favorites/{stickerId}")
    public ResponseEntity<List<StickerResponse>> favorite(@PathVariable UUID stickerId) {
        return ResponseEntity.ok(stickerService.setFavorite(CurrentUser.id(), stickerId, true));
    }

    @DeleteMapping("/favorites/{stickerId}")
    public ResponseEntity<List<StickerResponse>> unfavorite(@PathVariable UUID stickerId) {
        return ResponseEntity.ok(stickerService.setFavorite(CurrentUser.id(), stickerId, false));
    }
}
