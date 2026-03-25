package com.alex.messenger.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.alex.messenger.call.dto.CallSignalRequest;
import com.alex.messenger.call.dto.CreateCallJoinLinkRequest;
import com.alex.messenger.call.dto.StartCallRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CallRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void startCallAllowsTrimmedLowercaseKindAndMode() {
        StartCallRequest request = new StartCallRequest(
                UUID.randomUUID(),
                " voice ",
                " group ",
                false
        );

        Set<ConstraintViolation<StartCallRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void createJoinLinkAllowsTrimmedLowercaseKindAndMode() {
        CreateCallJoinLinkRequest request = new CreateCallJoinLinkRequest(
                UUID.randomUUID(),
                " video ",
                " live_stream ",
                "Townhall",
                Instant.now().plusSeconds(60)
        );

        Set<ConstraintViolation<CreateCallJoinLinkRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void callSignalAllowsTrimmedLowercaseSupportedAlias() {
        CallSignalRequest request = new CallSignalRequest(
                UUID.randomUUID(),
                " ice-candidate ",
                "{\"candidate\":\"x\"}"
        );

        Set<ConstraintViolation<CallSignalRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void callSignalRejectsUnsupportedType() {
        CallSignalRequest request = new CallSignalRequest(
                UUID.randomUUID(),
                " teleport ",
                "{\"value\":1}"
        );

        Set<ConstraintViolation<CallSignalRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }
}
