package com.alex.messenger.notification;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class ExpoPushNotificationService implements PushNotificationService {

    private final RestClient restClient;
    private final boolean enabled;
    private final String sound;

    public ExpoPushNotificationService(
            @Value("${alex.notifications.expo.base-url}") String baseUrl,
            @Value("${alex.notifications.expo.enabled}") boolean enabled,
            @Value("${alex.notifications.expo.sound}") String sound
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.enabled = enabled;
        this.sound = sound;
    }

    @Override
    public void send(List<PushNotificationCommand> commands) {
        if (!enabled || commands == null || commands.isEmpty()) {
            return;
        }

        List<Map<String, Object>> payload = commands.stream()
                .filter(command -> "EXPO".equals(command.provider()))
                .map(command -> Map.<String, Object>of(
                        "to", command.pushToken(),
                        "title", command.title(),
                        "body", command.body(),
                        "sound", sound,
                        "data", command.data()
                ))
                .toList();

        if (payload.isEmpty()) {
            return;
        }

        try {
            restClient.post()
                    .uri("/--/api/v2/push/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            log.warn("Unable to send Expo push notifications", exception);
        }
    }
}
