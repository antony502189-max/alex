package com.alex.messenger.shared;

import java.net.URI;
import java.util.Locale;

public final class HttpUrlValidationSupport {

    private HttpUrlValidationSupport() {
    }

    public static boolean isValidRequiredHttpUrl(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        return !normalized.isBlank() && isValidHttpUrl(normalized);
    }

    public static boolean isValidOptionalHttpUrl(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.trim();
        return normalized.isBlank() || isValidHttpUrl(normalized);
    }

    private static boolean isValidHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
            return uri.isAbsolute() && scheme != null && ("http".equals(scheme) || "https".equals(scheme));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
