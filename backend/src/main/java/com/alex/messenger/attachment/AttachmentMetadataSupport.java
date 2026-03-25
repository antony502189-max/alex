package com.alex.messenger.attachment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class AttachmentMetadataSupport {

    record UploadMetadata(
            String originalFileName,
            String contentType,
            String kind,
            Long durationMs,
            Integer width,
            Integer height,
            boolean hdPhoto,
            List<Integer> waveformSamples,
            UUID albumId,
            Integer albumItemIndex
    ) {
    }

    private AttachmentMetadataSupport() {
    }

    static UploadMetadata prepareMultipartUpload(
            String originalFileName,
            String contentType,
            String kind,
            Long durationMs,
            Integer width,
            Integer height,
            Boolean hdPhoto,
            String waveform,
            UUID albumId,
            Integer albumItemIndex
    ) {
        return prepare(
                originalFileName,
                contentType,
                kind,
                durationMs,
                width,
                height,
                hdPhoto,
                parseWaveformString(waveform),
                albumId,
                albumItemIndex
        );
    }

    static UploadMetadata prepareResumableUpload(
            String originalFileName,
            String contentType,
            String kind,
            Long durationMs,
            Integer width,
            Integer height,
            Boolean hdPhoto,
            List<Integer> waveform,
            UUID albumId,
            Integer albumItemIndex
    ) {
        return prepare(
                originalFileName,
                contentType,
                kind,
                durationMs,
                width,
                height,
                hdPhoto,
                normalizeWaveformSamples(waveform),
                albumId,
                albumItemIndex
        );
    }

    static List<Integer> parseWaveformString(String waveform) {
        if (waveform == null || waveform.isBlank()) {
            return List.of();
        }
        String[] parts = waveform.split(",");
        if (parts.length > 96) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Waveform is too large");
        }
        List<Integer> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            String normalized = part.trim();
            if (normalized.isBlank()) {
                continue;
            }
            int value;
            try {
                value = Integer.parseInt(normalized);
            } catch (NumberFormatException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Waveform contains invalid sample", exception);
            }
            if (value < 0 || value > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Waveform sample is out of range");
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    static String serializeWaveform(List<Integer> waveformSamples) {
        if (waveformSamples == null || waveformSamples.isEmpty()) {
            return null;
        }
        return waveformSamples.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private static UploadMetadata prepare(
            String originalFileName,
            String contentType,
            String kind,
            Long durationMs,
            Integer width,
            Integer height,
            Boolean hdPhoto,
            List<Integer> waveformSamples,
            UUID albumId,
            Integer albumItemIndex
    ) {
        String normalizedContentType = normalizeContentType(contentType);
        String normalizedKind = normalizeKind(kind, normalizedContentType);
        validateAttachmentMetadata(normalizedKind, durationMs, width, height, normalizedContentType);
        validateWaveform(normalizedKind, waveformSamples);
        validateAlbumMetadata(normalizedKind, albumId, albumItemIndex);
        return new UploadMetadata(
                safeFileName(originalFileName),
                normalizedContentType,
                normalizedKind,
                durationMs,
                width,
                height,
                normalizeHdPhoto(hdPhoto, normalizedKind),
                waveformSamples == null || waveformSamples.isEmpty() ? List.of() : List.copyOf(waveformSamples),
                albumId,
                albumItemIndex
        );
    }

    private static List<Integer> normalizeWaveformSamples(List<Integer> waveformSamples) {
        if (waveformSamples == null || waveformSamples.isEmpty()) {
            return List.of();
        }
        if (waveformSamples.size() > 96) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Waveform is too large");
        }
        for (Integer sample : waveformSamples) {
            if (sample == null || sample < 0 || sample > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Waveform sample is out of range");
            }
        }
        return List.copyOf(waveformSamples);
    }

    private static void validateWaveform(String kind, List<Integer> waveformSamples) {
        if (waveformSamples == null || waveformSamples.isEmpty()) {
            return;
        }
        if (!List.of("VOICE", "AUDIO", "VIDEO_NOTE").contains(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Waveform is supported only for audio-style attachments");
        }
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType.trim().toLowerCase();
    }

    private static String normalizeKind(String kind, String contentType) {
        String normalizedKind = kind != null ? kind.trim().toUpperCase() : "";
        if (normalizedKind.isBlank()) {
            if ("image/gif".equalsIgnoreCase(contentType)) {
                return "GIF";
            }
            if (contentType.toLowerCase().startsWith("image/")) {
                return "IMAGE";
            }
            if (contentType.toLowerCase().startsWith("video/")) {
                return "VIDEO";
            }
            if (contentType.toLowerCase().startsWith("audio/")) {
                return "AUDIO";
            }
            return "FILE";
        }
        if (!List.of("FILE", "VOICE", "IMAGE", "VIDEO", "AUDIO", "GIF", "VIDEO_NOTE").contains(normalizedKind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported attachment kind");
        }
        return normalizedKind;
    }

    private static void validateAttachmentMetadata(
            String kind,
            Long durationMs,
            Integer width,
            Integer height,
            String contentType
    ) {
        if ("VOICE".equals(kind)) {
            if (durationMs == null || durationMs <= 0 || durationMs > 60 * 60 * 1000L) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voice attachment duration is invalid");
            }
            if (!contentType.toLowerCase().startsWith("audio/")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voice attachment must be audio");
            }
        } else if ("AUDIO".equals(kind) && !contentType.toLowerCase().startsWith("audio/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio attachment must be audio");
        } else if ("IMAGE".equals(kind) && !contentType.toLowerCase().startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image attachment must be an image");
        } else if ("GIF".equals(kind) && !"image/gif".equalsIgnoreCase(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GIF attachment must be a GIF image");
        } else if ("VIDEO".equals(kind) && !contentType.toLowerCase().startsWith("video/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video attachment must be a video");
        } else if ("VIDEO_NOTE".equals(kind)) {
            if (durationMs == null || durationMs <= 0 || durationMs > 60 * 60 * 1000L) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video note duration is invalid");
            }
            if (!contentType.toLowerCase().startsWith("video/")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video note attachment must be a video");
            }
        }

        if (width != null && width <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment width is invalid");
        }
        if (height != null && height <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment height is invalid");
        }
        if ((width != null || height != null)
                && !List.of("IMAGE", "GIF", "VIDEO", "VIDEO_NOTE").contains(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dimensions are supported only for visual attachments");
        }
        if ("VIDEO_NOTE".equals(kind)
                && width != null
                && height != null
                && !width.equals(height)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video note must use square dimensions");
        }
    }

    private static void validateAlbumMetadata(String kind, UUID albumId, Integer albumItemIndex) {
        if (albumItemIndex != null && albumItemIndex < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album item index is invalid");
        }
        if (albumItemIndex != null && albumId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album item index requires album id");
        }
        if (albumId != null && !List.of("IMAGE", "VIDEO", "GIF", "VIDEO_NOTE").contains(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album attachments must be visual media");
        }
    }

    private static boolean normalizeHdPhoto(Boolean hdPhoto, String kind) {
        boolean enabled = Boolean.TRUE.equals(hdPhoto);
        if (enabled && !"IMAGE".equals(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HD photo is supported only for image attachments");
        }
        return enabled;
    }

    private static String safeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "file";
        }
        String normalized = originalFileName.replace("\\", "_").replace("/", "_").trim();
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }
}
