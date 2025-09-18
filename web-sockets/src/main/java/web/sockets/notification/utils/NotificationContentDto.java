package web.sockets.notification.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.pubsub.SerializableMessage;

import java.io.IOException;
import java.io.OutputStream;

public class NotificationContentDto implements SerializableMessage {
    private static final String NOTIFICATION_CONTENT_SCHEMA_PREFIX = "notif_cnt:";
    private String sourceDocumentId;
    private String sourceDocumentType;
    private String arabicMessage;
    private String englishMessage;
    private String data;
    private String notificationKey;
    private boolean isClickable;
    private String status;
    private String recipientUser;
    private String applicationKey;
    private String company;
    private String event;
    public NotificationContentDto(DocumentModel notificationContent) {
        this.sourceDocumentId = (String) notificationContent.getPropertyValue(NOTIFICATION_CONTENT_SCHEMA_PREFIX+"sourceDocument");
        this.sourceDocumentType = (String) notificationContent.getPropertyValue("notf_nt:sourceTypee");
        this.arabicMessage = (String) notificationContent.getPropertyValue(NOTIFICATION_CONTENT_SCHEMA_PREFIX+"arMessage");
        this.englishMessage = (String) notificationContent.getPropertyValue(NOTIFICATION_CONTENT_SCHEMA_PREFIX+"enMessage");
        this.data = (String) notificationContent.getPropertyValue(NOTIFICATION_CONTENT_SCHEMA_PREFIX+"data");
        this.notificationKey = (String) notificationContent.getPropertyValue("notf_nt:key");
        this.isClickable = (boolean) notificationContent.getPropertyValue("notf_nt:isClickable");
        this.status = (String) notificationContent.getPropertyValue(NOTIFICATION_CONTENT_SCHEMA_PREFIX+"status");
        this.recipientUser = (String) notificationContent.getPropertyValue(NOTIFICATION_CONTENT_SCHEMA_PREFIX+"user");
        this.applicationKey = (String) notificationContent.getPropertyValue(NOTIFICATION_CONTENT_SCHEMA_PREFIX+"applicationKey");
        this.company = (String) notificationContent.getPropertyValue(NOTIFICATION_CONTENT_SCHEMA_PREFIX+"company");
    }
    public NotificationContentDto(){

    }

    public String getSourceDocumentId() {
        return sourceDocumentId;
    }

    public void setSourceDocumentId(String sourceDocumentId) {
        this.sourceDocumentId = sourceDocumentId;
    }

    public String getSourceDocumentType() {
        return sourceDocumentType;
    }

    public void setSourceDocumentType(String sourceDocumentType) {
        this.sourceDocumentType = sourceDocumentType;
    }

    public String getArabicMessage() {
        return arabicMessage;
    }

    public void setArabicMessage(String arabicMessage) {
        this.arabicMessage = arabicMessage;
    }

    public String getEnglishMessage() {
        return englishMessage;
    }

    public void setEnglishMessage(String englishMessage) {
        this.englishMessage = englishMessage;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getNotificationKey() {
        return notificationKey;
    }

    public void setNotificationKey(String notificationKey) {
        this.notificationKey = notificationKey;
    }

    public boolean isClickable() {
        return isClickable;
    }

    public void setClickable(boolean clickable) {
        isClickable = clickable;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRecipientUser() {
        return recipientUser;
    }

    public void setRecipientUser(String recipientUser) {
        this.recipientUser = recipientUser;
    }

    public String getApplicationKey() {
        return applicationKey;
    }

    public void setApplicationKey(String applicationKey) {
        this.applicationKey = applicationKey;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    @Override
    public void serialize(OutputStream outputStream) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(outputStream, this);
    }
}

