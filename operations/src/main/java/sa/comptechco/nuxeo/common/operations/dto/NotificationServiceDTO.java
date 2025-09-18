package sa.comptechco.nuxeo.common.operations.dto;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.util.Map;

public class NotificationServiceDTO implements Serializable {
    private static final Logger log = LogManager.getLogger(NotificationServiceDTO.class);
    String notificationKey;
    String channelName;
    NotifyDTO notifyDTO;
    String documentId;
    Map<String, Object> chainParameters;


    public String getNotificationKey() {
        return notificationKey;
    }

    public void setNotificationKey(String notificationKey) {
        this.notificationKey = notificationKey;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public NotifyDTO getNotifyDTO() {
        return notifyDTO;
    }

    public void setNotifyDTO(NotifyDTO notifyDTO) {
        this.notifyDTO = notifyDTO;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public Map<String, Object> getChainParameters() {
        return chainParameters;
    }

    public void setChainParameters(Map<String, Object> chainParameters) {
        this.chainParameters = chainParameters;
    }

    @Override
    public String toString() {
        return "NotificationServiceDTO{" +
                "notificationKey='" + notificationKey + '\'' +
                ", channelName='" + channelName + '\'' +
                ", notifyDTO=" + notifyDTO +
                ", documentId='" + documentId + '\'' +
                ", chainParameters=" + chainParameters +
                '}';
    }
}
