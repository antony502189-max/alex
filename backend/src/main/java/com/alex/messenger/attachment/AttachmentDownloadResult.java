package com.alex.messenger.attachment;

public record AttachmentDownloadResult(
        String redirectUrl,
        DownloadedAttachment downloadedAttachment
) {
    public boolean isRedirect() {
        return redirectUrl != null && !redirectUrl.isBlank();
    }
}
