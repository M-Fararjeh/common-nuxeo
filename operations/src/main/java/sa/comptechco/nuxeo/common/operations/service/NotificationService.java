package sa.comptechco.nuxeo.common.operations.service;

import sa.comptechco.nuxeo.common.operations.dto.NotificationServiceDTO;

import java.io.IOException;

public interface NotificationService {
    //public Object callNotification(String notificationKey, String channelName, NotifyDTO notifyDTO) throws IOException;
    void callNotification(NotificationServiceDTO notificationServiceDTO) throws IOException;

}
