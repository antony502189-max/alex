package com.alex.messenger.sticker;

import com.alex.messenger.attachment.AttachmentEntity;
import com.alex.messenger.attachment.AttachmentRepository;
import com.alex.messenger.sticker.dto.StickerPackResponse;
import com.alex.messenger.sticker.dto.StickerResponse;
import java.time.Instant;
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
    private final AttachmentRepository attachmentRepository;
    private final UserRecentStickerRepository userRecentStickerRepository;
    private final UserFavoriteStickerRepository userFavoriteStickerRepository;
    private final UserRecentGifRepository userRecentGifRepository;

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

    @Transactional(readOnly = true)
    public List<StickerResponse> listRecent(UUID userId) {
        return mapStickerState(
                userRecentStickerRepository.findAllByIdUserIdOrderByUsedAtDesc(userId).stream()
                        .map(entity -> entity.getId().getStickerId())
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public List<StickerResponse> listFavorites(UUID userId) {
        return mapStickerState(
                userFavoriteStickerRepository.findAllByIdUserIdOrderByCreatedAtDesc(userId).stream()
                        .map(entity -> entity.getId().getStickerId())
                        .toList()
        );
    }

    @Transactional
    public List<StickerResponse> setFavorite(UUID userId, UUID stickerId, boolean favorite) {
        assertStickerExists(stickerId);
        UserStickerId id = new UserStickerId(userId, stickerId);
        if (favorite) {
            if (!userFavoriteStickerRepository.existsById(id)) {
                UserFavoriteStickerEntity entity = new UserFavoriteStickerEntity();
                entity.setId(id);
                userFavoriteStickerRepository.save(entity);
            }
        } else {
            userFavoriteStickerRepository.deleteById(id);
        }
        return listFavorites(userId);
    }

    @Transactional
    public void recordUsage(UUID userId, UUID stickerId) {
        if (userId == null || stickerId == null) {
            return;
        }
        UserStickerId id = new UserStickerId(userId, stickerId);
        UserRecentStickerEntity entity = userRecentStickerRepository.findById(id).orElseGet(() -> {
            UserRecentStickerEntity created = new UserRecentStickerEntity();
            created.setId(id);
            created.setUsageCount(0);
            return created;
        });
        entity.setUsedAt(Instant.now());
        entity.setUsageCount((entity.getUsageCount() != null ? entity.getUsageCount() : 0) + 1);
        userRecentStickerRepository.save(entity);
    }

    @Transactional
    public void recordGifUsage(UUID userId, List<UUID> attachmentIds) {
        if (userId == null || attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        List<AttachmentEntity> gifAttachments = attachmentRepository.findAllByIdIn(attachmentIds).stream()
                .filter(attachment -> attachment.getKind() != null)
                .filter(attachment -> "GIF".equalsIgnoreCase(attachment.getKind()))
                .toList();
        Instant now = Instant.now();
        for (AttachmentEntity attachment : gifAttachments) {
            UserAttachmentUsageId id = new UserAttachmentUsageId(userId, attachment.getId());
            UserRecentGifEntity entity = userRecentGifRepository.findById(id).orElseGet(() -> {
                UserRecentGifEntity created = new UserRecentGifEntity();
                created.setId(id);
                created.setUsageCount(0);
                return created;
            });
            entity.setUsedAt(now);
            entity.setUsageCount((entity.getUsageCount() != null ? entity.getUsageCount() : 0) + 1);
            userRecentGifRepository.save(entity);
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> listRecentGifIds(UUID userId) {
        return userRecentGifRepository.findAllByIdUserIdOrderByUsedAtDesc(userId).stream()
                .map(entity -> entity.getId().getAttachmentId())
                .toList();
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

    private List<StickerResponse> mapStickerState(List<UUID> stickerIds) {
        if (stickerIds == null || stickerIds.isEmpty()) {
            return List.of();
        }
        List<StickerEntity> stickers = stickerRepository.findAllById(stickerIds);
        if (stickers.isEmpty()) {
            return List.of();
        }
        Map<UUID, StickerEntity> stickersById = stickers.stream()
                .collect(Collectors.toMap(StickerEntity::getId, Function.identity()));
        Map<UUID, StickerPackEntity> packsById = stickerPackRepository.findAllById(
                stickers.stream().map(StickerEntity::getPackId).distinct().toList()
        ).stream().collect(Collectors.toMap(StickerPackEntity::getId, Function.identity()));
        return stickerIds.stream()
                .map(stickersById::get)
                .filter(java.util.Objects::nonNull)
                .map(sticker -> toResponse(sticker, packsById.get(sticker.getPackId())))
                .toList();
    }
}
