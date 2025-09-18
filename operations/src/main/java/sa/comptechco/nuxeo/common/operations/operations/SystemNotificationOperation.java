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

@Operation(id = SystemNotificationOperation.ID, category = Constants.CAT_NOTIFICATION, label = "System Notification", description = "Send system notification")
public class SystemNotificationOperation {

    private static final Log logger = LogFactory.getLog(SystemNotificationOperation.class);

    public static final String ID = "System.Notification";
    protected static final NotificationService notificationService = getNotificationServiceImpl();

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
    protected String sourceType = "nuxeo";

    @Param(name = "companyName", required = false)
    protected String companyName = "default";

    @Param(name = "customMetaData", required = false)
    protected String customMetaData;

    @Context
    protected CoreSession session;

    @OperationMethod
    public DocumentModel run(DocumentModel input) throws IOException {

        Boolean isNotificationEnabled = Boolean.valueOf(Framework.getProperty("sa.comptechco.nuxeo.notification.enabled", "true"));

        LogUtil.log(logger, "Start Notification action with status: "+ isNotificationEnabled, LogEnum.INFO);
        if(isNotificationEnabled) {
            Map<String, Object> notificationServiceParams = createNotificationServiceParams(input);
            NotificationServiceDTO notificationServiceDTO = getNotificationServiceDTO(notificationServiceParams);

            LogUtil.log(logger, "NotificationServiceDTO - " + notificationServiceDTO, LogEnum.INFO);

            notificationService.callNotification(notificationServiceDTO);
        }
        return input;
    }


    @NotNull
    private Map<String, Object> createNotificationServiceParams(DocumentModel input) {
        Map<String, Object> notificationServiceData = new HashMap<>();
        notificationServiceData.put("applicationKey", applicationKey);
        notificationServiceData.put("notificationKey", notificationKey);
        notificationServiceData.put("data", data);
        notificationServiceData.put("apiData", apiData);
        notificationServiceData.put("groupsId", groupsId);
        notificationServiceData.put("usersId", usersId);
        notificationServiceData.put("sourceType", sourceType);
        notificationServiceData.put("customMetaData", customMetaData);
        notificationServiceData.put("input", input);
        notificationServiceData.put("session", session);

        if(input.hasSchema("Company")) {
            System.out.println("doc has company schema");
            companyName = (String) input.getPropertyValue("comp:companyCode");
        }
        else {
            companyName ="default";
        }
        notificationServiceData.put("companyName", companyName);

        return notificationServiceData;
    }

}
