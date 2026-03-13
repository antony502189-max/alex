package com.alex.messenger.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MessageTranslationProviderClient {

    private final MessageTranslationProperties properties;
    private final ObjectMapper objectMapper;

    public String translate(String providerUrl, String sourceLanguage, String targetLanguage, String value, String apiKey) {
        if (value == null) {
            return null;
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(properties.getTimeout())
                    .build();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(providerUrl.endsWith("/translate") ? providerUrl : providerUrl + "/translate"))
                    .timeout(properties.getTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(new TranslationPayload(
                                    value,
                                    sourceLanguage != null ? sourceLanguage : "auto",
                                    targetLanguage,
                                    "text",
                                    apiKey
                            )),
                            StandardCharsets.UTF_8
                    ));
            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Translation provider returned an error");
            }
            JsonNode root = objectMapper.readTree(response.body());
            String translated = root.path("translatedText").isTextual() ? root.path("translatedText").asText() : null;
            if (translated == null || translated.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Translation provider returned an empty result");
            }
            return translated;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to translate message", exception);
        }
    }

    private record TranslationPayload(
            String q,
            String source,
            String target,
            String format,
            String api_key
    ) {
    }
}
