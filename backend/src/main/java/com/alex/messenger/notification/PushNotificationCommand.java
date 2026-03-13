package com.alex.messenger.notification;

import java.util.Map;

public record PushNotificationCommand(
        String provider,
        String pushToken,
        String title,
        String body,
        Map<String, String> data
) {
}
