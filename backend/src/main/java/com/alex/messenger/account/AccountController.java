package com.alex.messenger.account;

import com.alex.messenger.account.dto.AccountDeletionResponse;
import com.alex.messenger.account.dto.AccountExportResponse;
import com.alex.messenger.account.dto.RequestAccountExport;
import com.alex.messenger.account.dto.ScheduleAccountDeletionRequest;
import com.alex.messenger.shared.CurrentSession;
import com.alex.messenger.shared.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/export")
    public ResponseEntity<AccountExportResponse> exportAccount(
            @RequestBody(required = false) RequestAccountExport request
    ) {
        return ResponseEntity.ok(accountService.export(CurrentUser.id(), CurrentSession.id(), request));
    }

    @PostMapping("/delete")
    public ResponseEntity<AccountDeletionResponse> scheduleDeletion(
            @Valid @RequestBody(required = false) ScheduleAccountDeletionRequest request
    ) {
        return ResponseEntity.ok(accountService.scheduleDeletion(CurrentUser.id(), CurrentSession.id(), request));
    }
}
