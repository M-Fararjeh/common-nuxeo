package sa.comptechco.nuxeo.common.operations.dto;

import java.io.Serializable;
import java.util.Map;

/***
 *
 */
public class NotifyDTO implements Serializable {
    private String sendDate;

    private String applicationKey;

    private RecipientsDTO recipients;

    private Map<String, Object> apiData;

    private SourceDTO source;

    @Override
    public String toString() {
        return "NotifyDTO{" +
                "sendDate='" + sendDate + '\'' +
                ", applicationKey='" + applicationKey + '\'' +
                ", recipients=" + recipients +
                ", apiData=" + apiData +
                ", source=" + source +
                '}';
    }

    public String getSendDate() {
        return sendDate;
    }

    public void setSendDate(String sendDate) {
        this.sendDate = sendDate;
    }

    public String getApplicationKey() {
        return applicationKey;
    }

    public void setApplicationKey(String applicationKey) {
        this.applicationKey = applicationKey;
    }

    public RecipientsDTO getRecipients() {
        return recipients;
    }

    public void setRecipients(RecipientsDTO recipients) {
        this.recipients = recipients;
    }

    public Map<String, Object> getApiData() {
        return apiData;
    }

    public void setApiData(Map<String, Object> apiData) {
        this.apiData = apiData;
    }

    public SourceDTO getSource() {
        return source;
    }

    public void setSource(SourceDTO source) {
        this.source = source;
    }
}

