package com.alex.messenger.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.alex.messenger.message.dto.MessageLiveLocationPayload;
import com.alex.messenger.message.dto.SendMessageRequest;
import com.alex.messenger.message.dto.UpdateLiveLocationRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MessageLiveLocationValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @Test
    void liveLocationPayloadRejectsOutOfRangeCoordinatesAndDuration() {
        MessageLiveLocationPayload payload = new MessageLiveLocationPayload(
                120.0,
                200.0,
                "Title",
                "Address",
                30,
                Instant.parse("2999-01-01T00:00:00Z"),
                Instant.parse("2026-03-19T18:00:00Z"),
                null,
                true
        );

        Set<String> invalidProperties = validator.validate(payload).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(invalidProperties).contains("latitude", "longitude", "livePeriodSeconds");
    }

    @Test
    void updateLiveLocationRequestRequiresCoordinates() {
        UpdateLiveLocationRequest request = new UpdateLiveLocationRequest(null, null, "Title", "Address");

        Set<String> invalidProperties = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(invalidProperties).contains("latitude", "longitude");
    }

    @Test
    void sendMessageRequestCascadesLiveLocationValidation() {
        SendMessageRequest request = new SendMessageRequest(
                UUID.randomUUID(),
                null,
                null,
                null,
                "",
                null,
                "LIVE_LOCATION",
                List.of(),
                null,
                new MessageLiveLocationPayload(
                        95.0,
                        27.56,
                        "Title",
                        "Address",
                        30,
                        null,
                        null,
                        null,
                        null
                ),
                null,
                List.of(),
                null,
                false,
                null,
                null
        );

        Set<String> invalidProperties = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(invalidProperties).contains(
                "liveLocation.latitude",
                "liveLocation.livePeriodSeconds"
        );
    }
}
