package com.alex.messenger.story;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StoryLiveDonationHookDispatcher {

    private final StoryLiveDonationHookService storyLiveDonationHookService;

    @Value("${alex.stories.live-donations.batch-size:20}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${alex.stories.live-donations.dispatch-interval-ms:5000}")
    @Transactional
    void dispatchDonationHooks() {
        List<StoryLiveCommentEntity> comments = storyLiveDonationHookService.lockPendingDeliveryBatch(batchSize);
        for (StoryLiveCommentEntity comment : comments) {
            storyLiveDonationHookService.deliverDonationHook(comment);
        }
    }
}
