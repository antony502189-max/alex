package com.alex.messenger.sticker;

import com.alex.messenger.sticker.dto.StickerPackResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
