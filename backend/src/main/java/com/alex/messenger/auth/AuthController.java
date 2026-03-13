package com.alex.messenger.auth;

import com.alex.messenger.auth.dto.AuthRequest;
import com.alex.messenger.auth.dto.AuthResponse;
import com.alex.messenger.auth.dto.DisableTwoFactorRequest;
import com.alex.messenger.auth.dto.EnableTwoFactorRequest;
import com.alex.messenger.auth.dto.GenerateQrLoginResponse;
import com.alex.messenger.auth.dto.QrLoginBindRequest;
import com.alex.messenger.auth.dto.QrLoginChallengeResponse;
import com.alex.messenger.auth.dto.QrLoginPollRequest;
import com.alex.messenger.auth.dto.QrLoginStatusResponse;
import com.alex.messenger.auth.dto.RefreshTokenRequest;
import com.alex.messenger.auth.dto.RequestLoginCodeRequest;
import com.alex.messenger.auth.dto.RequestLoginCodeResponse;
import com.alex.messenger.auth.dto.TwoFactorStatusResponse;
import com.alex.messenger.auth.dto.UpdatePushTokenRequest;
import com.alex.messenger.auth.dto.UserSessionResponse;
import com.alex.messenger.auth.dto.VerifyLoginCodeRequest;
import com.alex.messenger.auth.dto.VerifyTwoFactorRequest;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.shared.CurrentSession;
import com.alex.messenger.shared.CurrentUser;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserSessionService userSessionService;

    @PostMapping("/request-code")
    public ResponseEntity<RequestLoginCodeResponse> requestCode(
            @Valid @RequestBody RequestLoginCodeRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(authService.requestCode(
                request,
                extractClientIp(httpServletRequest),
                httpServletRequest.getHeader("User-Agent")
        ));
    }

    @PostMapping("/verify-code")
    public ResponseEntity<AuthResponse> verifyCode(
            @Valid @RequestBody VerifyLoginCodeRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(authService.verifyCode(
                request,
                extractClientIp(httpServletRequest),
                httpServletRequest.getHeader("User-Agent")
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(authService.refresh(
                request,
                extractClientIp(httpServletRequest),
                httpServletRequest.getHeader("User-Agent")
        ));
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<AuthResponse> verifyTwoFactor(
            @Valid @RequestBody VerifyTwoFactorRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(authService.verifyTwoFactor(
                request,
                extractClientIp(httpServletRequest),
                httpServletRequest.getHeader("User-Agent")
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(authService.login(
                request,
                extractClientIp(httpServletRequest),
                httpServletRequest.getHeader("User-Agent")
        ));
    }

    @GetMapping("/2fa/status")
    public ResponseEntity<TwoFactorStatusResponse> twoFactorStatus() {
        return ResponseEntity.ok(authService.getTwoFactorStatus(CurrentUser.id()));
    }

    @PostMapping("/2fa/enable")
    public ResponseEntity<TwoFactorStatusResponse> enableTwoFactor(
            @Valid @RequestBody EnableTwoFactorRequest request
    ) {
        return ResponseEntity.ok(authService.enableTwoFactor(CurrentUser.id(), CurrentSession.id(), request));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<TwoFactorStatusResponse> disableTwoFactor(
            @Valid @RequestBody DisableTwoFactorRequest request
    ) {
        return ResponseEntity.ok(authService.disableTwoFactor(CurrentUser.id(), request));
    }

    @PostMapping("/qr/generate")
    public ResponseEntity<GenerateQrLoginResponse> generateQrLogin() {
        return ResponseEntity.ok(authService.generateQrLogin(CurrentUser.id(), CurrentSession.id()));
    }

    @GetMapping("/qr/pending")
    public ResponseEntity<List<QrLoginChallengeResponse>> qrChallenges() {
        return ResponseEntity.ok(authService.listQrLoginChallenges(CurrentUser.id(), CurrentSession.id()));
    }

    @PostMapping("/qr/{challengeId}/approve")
    public ResponseEntity<QrLoginChallengeResponse> approveQrLogin(@PathVariable UUID challengeId) {
        return ResponseEntity.ok(authService.approveQrLogin(CurrentUser.id(), CurrentSession.id(), challengeId));
    }

    @PostMapping("/qr/{challengeId}/decline")
    public ResponseEntity<QrLoginChallengeResponse> declineQrLogin(@PathVariable UUID challengeId) {
        return ResponseEntity.ok(authService.declineQrLogin(CurrentUser.id(), CurrentSession.id(), challengeId));
    }

    @PostMapping("/qr/bind")
    public ResponseEntity<QrLoginStatusResponse> bindQrLogin(
            @Valid @RequestBody QrLoginBindRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(authService.bindQrLogin(
                request,
                extractClientIp(httpServletRequest),
                httpServletRequest.getHeader("User-Agent")
        ));
    }

    @PostMapping("/qr/poll")
    public ResponseEntity<QrLoginStatusResponse> pollQrLogin(
            @Valid @RequestBody QrLoginPollRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(authService.pollQrLogin(
                request,
                extractClientIp(httpServletRequest),
                httpServletRequest.getHeader("User-Agent")
        ));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<UserSessionResponse>> sessions() {
        return ResponseEntity.ok(userSessionService.list(CurrentUser.id(), CurrentSession.id()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID sessionId) {
        userSessionService.revoke(CurrentUser.id(), sessionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions/others")
    public ResponseEntity<Void> revokeOtherSessions() {
        userSessionService.revokeOthers(CurrentUser.id(), CurrentSession.id());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/sessions/current/push-token")
    public ResponseEntity<UserSessionResponse> updatePushToken(@Valid @RequestBody UpdatePushTokenRequest request) {
        return ResponseEntity.ok(userSessionService.updatePushToken(CurrentUser.id(), CurrentSession.id(), request));
    }

    @DeleteMapping("/sessions/current/push-token")
    public ResponseEntity<UserSessionResponse> clearPushToken() {
        return ResponseEntity.ok(userSessionService.clearPushToken(CurrentUser.id(), CurrentSession.id()));
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
