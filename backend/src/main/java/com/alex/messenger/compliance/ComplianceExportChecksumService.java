package com.alex.messenger.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ComplianceExportChecksumService {

    private final ObjectMapper objectMapper;

    public String computeArtifactChecksum(ComplianceCaseExportArtifactPayload payload) {
        try {
            byte[] serializedPayload = objectMapper.writeValueAsBytes(payload);
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(serializedPayload));
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to compute compliance export checksum",
                    exception
            );
        }
    }
}
