package com.alex.messenger.sticker;

import com.alex.messenger.sticker.dto.StickerPackResponse;
import com.alex.messenger.sticker.dto.StickerResponse;
import java.util.List;
import java.util.Map;
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
public class StickerService {

    private final StickerPackRepository stickerPackRepository;
    private final StickerRepository stickerRepository;

    @Transactional(readOnly = true)
    public List<StickerPackResponse> listPacks() {
        List<StickerPackEntity> packs = stickerPackRepository.findAllByOrderByTitleAsc();
        if (packs.isEmpty()) {
            return List.of();
        }

        Map<UUID, StickerPackEntity> packsById = packs.stream()
                .collect(Collectors.toMap(StickerPackEntity::getId, Function.identity()));

        Map<UUID, List<StickerResponse>> stickersByPack = stickerRepository.findAllByPackIdInOrderByPackIdAscPositionAsc(
                        packs.stream().map(StickerPackEntity::getId).toList()
                ).stream()
                .map(sticker -> toResponse(sticker, packsById.get(sticker.getPackId())))
                .collect(Collectors.groupingBy(StickerResponse::packId));

        return packs.stream()
                .map(pack -> new StickerPackResponse(
                        pack.getId(),
                        pack.getTitle(),
                        pack.getSlug(),
                        stickersByPack.getOrDefault(pack.getId(), List.of())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public StickerResponse getStickerResponse(UUID stickerId) {
        if (stickerId == null) {
            return null;
        }
        StickerEntity sticker = stickerRepository.findById(stickerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sticker not found"));
        StickerPackEntity pack = stickerPackRepository.findById(sticker.getPackId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sticker pack not found"));
        return toResponse(sticker, pack);
    }

    @Transactional(readOnly = true)
    public void assertStickerExists(UUID stickerId) {
        if (stickerId == null) {
            return;
        }
        if (!stickerRepository.existsById(stickerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sticker not found");
        }
    }

    private StickerResponse toResponse(StickerEntity sticker, StickerPackEntity pack) {
        return new StickerResponse(
                sticker.getId(),
                sticker.getPackId(),
                pack != null ? pack.getTitle() : "Stickers",
                sticker.getEmoji(),
                sticker.getLabel(),
                sticker.getBackgroundFrom(),
                sticker.getBackgroundTo(),
                sticker.getTextColor()
        );
    }
}
