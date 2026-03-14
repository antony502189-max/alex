package com.alex.messenger.monetization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMonetizationWithdrawalCallbackRepository
        extends JpaRepository<ChannelMonetizationWithdrawalCallbackEntity, UUID> {

    List<ChannelMonetizationWithdrawalCallbackEntity> findAllByWithdrawalIdOrderByReceivedAtDesc(UUID withdrawalId);
}
