package com.alex.messenger.payments.dto;

public record TopUpWalletRequest(
        Long amountUnits,
        String description
) {
}
