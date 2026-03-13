package com.alex.messenger.business.dto;

public record BusinessHourSlotPayload(
        String dayOfWeek,
        String fromTime,
        String toTime
) {
}
