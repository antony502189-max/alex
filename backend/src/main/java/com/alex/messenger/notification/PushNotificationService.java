package com.alex.messenger.notification;

import java.util.List;

public interface PushNotificationService {

    void send(List<PushNotificationCommand> commands);
}
