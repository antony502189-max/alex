package com.alex.messenger.feature.dto;

public record FeatureProfileResponse(
        String productProfile,
        boolean stories,
        boolean bots,
        boolean calls,
        boolean directCalls,
        boolean groupCalls,
        boolean callJoinLinks,
        boolean callComments,
        boolean callReactions,
        boolean callModeration,
        boolean callScreenSharing,
        boolean callHandRaise,
        boolean callRecording,
        boolean secretChats,
        boolean adminCompliance,
        boolean lawfulDirectExport,
        boolean botApiFull,
        boolean business,
        boolean payments,
        boolean premium,
        boolean monetization,
        boolean translations
) {
}
