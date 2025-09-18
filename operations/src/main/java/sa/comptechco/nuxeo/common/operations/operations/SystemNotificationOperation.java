package sa.comptechco.nuxeo.common.operations.operations;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.api.Framework;
import sa.comptechco.nuxeo.common.operations.dto.*;
import sa.comptechco.nuxeo.common.operations.service.NotificationService;
import sa.comptechco.nuxeo.common.operations.utils.*;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.DocumentModel;

import java.io.IOException;
import java.util.*;

import static sa.comptechco.nuxeo.common.operations.utils.NotificationServiceFactory.getNotificationServiceDTO;
import static sa.comptechco.nuxeo.common.operations.utils.NotificationServiceFactory.getNotificationServiceImpl;

/**
 * Operation for sending system notifications to users and groups.
 * 
 * This operation creates and sends notifications through the configured notification service,
 * supporting both individual users and group-based notifications with custom metadata.
 */
@Operation(id = SystemNotificationOperation.ID, category = Constants.CAT_NOTIFICATION, label = "System Notification", description = "Send system notification")
public class SystemNotificationOperation {

    private static final Log logger = LogFactory.getLog(SystemNotificationOperation.class);
    private static final String NOTIFICATION_ENABLED_PROPERTY = "sa.comptechco.nuxeo.notification.enabled";
    private static final String DEFAULT_SOURCE_TYPE = "nuxeo";
    private static final String DEFAULT_COMPANY_NAME = "default";

    public static final String ID = "System.Notification";
    
    private static final NotificationService notificationService = getNotificationServiceImpl();

    @Param(name = "applicationKey", required = true)
    protected String applicationKey;

    @Param(name = "notificationKey", required = true)
    protected String notificationKey;

    @Param(name = "usersId", required = false)
    protected StringList usersId;

    @Param(name = "groupsId", required = false)
    protected StringList groupsId;

    @Param(name = "data", required = false)
    protected String data;

    @Param(name = "apiData", required = false)
    protected String apiData;

    @Param(name = "sourceType", required = false)
    protected String sourceType = DEFAULT_SOURCE_TYPE;

    @Param(name = "companyName", required = false)
    protected String companyName = DEFAULT_COMPANY_NAME;

    @Param(name = "customMetaData", required = false)
    protected String customMetaData;

    @Context
    protected CoreSession session;

    @OperationMethod
    public DocumentModel run(DocumentModel input) throws IOException {
        if (!isNotificationEnabled()) {
            LogUtil.log(logger, "Notification service is disabled", LogEnum.INFO);
            return input;
        }

        LogUtil.log(logger, "Starting notification action", LogEnum.INFO);
        
        try {
            sendNotification(input);
            LogUtil.log(logger, "Notification sent successfully", LogEnum.INFO);
        } catch (Exception e) {
            LogUtil.log(logger, "Failed to send notification: " + e.getMessage(), LogEnum.ERROR);
            throw new IOException("Failed to send notification", e);
        }
        
        return input;
    }

    /**
     * Checks if notification service is enabled via configuration.
     */
    private boolean isNotificationEnabled() {
        return Boolean.parseBoolean(Framework.getProperty(NOTIFICATION_ENABLED_PROPERTY, "true"));
    }

    /**
     * Sends the notification using the configured service.
     */
    private void sendNotification(DocumentModel input) throws IOException {
        NotificationServiceParams params = createNotificationServiceParams(input);
        NotificationServiceDTO notificationDTO = getNotificationServiceDTO(params.toMap());
        
        LogUtil.log(logger, "NotificationServiceDTO - " + notificationDTO, LogEnum.INFO);
        notificationService.callNotification(notificationDTO);
    }

    /**
     * Creates notification service parameters from the operation inputs.
     */
    private NotificationServiceParams createNotificationServiceParams(DocumentModel input) {
        String resolvedCompanyName = resolveCompanyName(input);
        
        return new NotificationServiceParams.Builder()
            .applicationKey(applicationKey)
            .notificationKey(notificationKey)
            .data(data)
            .apiData(apiData)
            .groupsId(groupsId)
            .usersId(usersId)
            .sourceType(sourceType)
            .customMetaData(customMetaData)
            .companyName(resolvedCompanyName)
            .input(input)
            .session(session)
            .build();
    }

    /**
     * Resolves company name from document or uses default.
     */
    private String resolveCompanyName(DocumentModel input) {
        if (input.hasSchema("Company")) {
            String companyCode = (String) input.getPropertyValue("comp:companyCode");
            return companyCode != null ? companyCode : DEFAULT_COMPANY_NAME;
        }
        return companyName != null ? companyName : DEFAULT_COMPANY_NAME;
    }

    /**
     * Helper class to organize notification service parameters.
     */
    private static class NotificationServiceParams {
        private final String applicationKey;
        private final String notificationKey;
        private final String data;
        private final String apiData;
        private final StringList groupsId;
        private final StringList usersId;
        private final String sourceType;
        private final String customMetaData;
        private final String companyName;
        private final DocumentModel input;
        private final CoreSession session;

        private NotificationServiceParams(Builder builder) {
            this.applicationKey = builder.applicationKey;
            this.notificationKey = builder.notificationKey;
            this.data = builder.data;
            this.apiData = builder.apiData;
            this.groupsId = builder.groupsId;
            this.usersId = builder.usersId;
            this.sourceType = builder.sourceType;
            this.customMetaData = builder.customMetaData;
            this.companyName = builder.companyName;
            this.input = builder.input;
            this.session = builder.session;
        }

        @NotNull
        Map<String, Object> toMap() {
            Map<String, Object> params = new HashMap<>();
            params.put("applicationKey", applicationKey);
            params.put("notificationKey", notificationKey);
            params.put("data", data);
            params.put("apiData", apiData);
            params.put("groupsId", groupsId);
            params.put("usersId", usersId);
            params.put("sourceType", sourceType);
            params.put("customMetaData", customMetaData);
            params.put("companyName", companyName);
            params.put("input", input);
            params.put("session", session);
            return params;
        }

        static class Builder {
            private String applicationKey;
            private String notificationKey;
            private String data;
            private String apiData;
            private StringList groupsId;
            private StringList usersId;
            private String sourceType;
            private String customMetaData;
            private String companyName;
            private DocumentModel input;
            private CoreSession session;

            Builder applicationKey(String applicationKey) {
                this.applicationKey = applicationKey;
                return this;
            }

            Builder notificationKey(String notificationKey) {
                this.notificationKey = notificationKey;
                return this;
            }

            Builder data(String data) {
                this.data = data;
                return this;
            }

            Builder apiData(String apiData) {
                this.apiData = apiData;
                return this;
            }

            Builder groupsId(StringList groupsId) {
                this.groupsId = groupsId;
                return this;
            }

            Builder usersId(StringList usersId) {
                this.usersId = usersId;
                return this;
            }

            Builder sourceType(String sourceType) {
                this.sourceType = sourceType;
                return this;
            }

            Builder customMetaData(String customMetaData) {
                this.customMetaData = customMetaData;
                return this;
            }

            Builder companyName(String companyName) {
                this.companyName = companyName;
                return this;
            }

            Builder input(DocumentModel input) {
                this.input = input;
                return this;
            }

            Builder session(CoreSession session) {
                this.session = session;
                return this;
            }

            NotificationServiceParams build() {
                return new NotificationServiceParams(this);
            }
        }
    }
}