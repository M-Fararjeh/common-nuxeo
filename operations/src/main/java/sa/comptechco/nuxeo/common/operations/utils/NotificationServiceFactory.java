package sa.comptechco.nuxeo.common.operations.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoGroup;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;
import sa.comptechco.nuxeo.common.operations.dto.NotificationServiceDTO;
import sa.comptechco.nuxeo.common.operations.dto.NotifyDTO;
import sa.comptechco.nuxeo.common.operations.dto.RecipientsDTO;
import sa.comptechco.nuxeo.common.operations.dto.SourceDTO;
import sa.comptechco.nuxeo.common.operations.service.NotificationService;
import sa.comptechco.nuxeo.common.operations.service.NotificationServiceImpl;
import sa.comptechco.nuxeo.common.operations.service.NuxeoNotificationServiceImpl;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NotificationServiceFactory {

    private static final String NOTIFICATION_SERVICE_CONFIG = "org.notification.service";
    private static final String DEFAULT_NOTIFICATION_SERVICE_NAME = "NotificationService";
    private static final String NOTIFICATION_SERVICE_NAME = Framework.getProperty(NOTIFICATION_SERVICE_CONFIG,
            DEFAULT_NOTIFICATION_SERVICE_NAME);
    private static final String NUXEO_NOTIFICATION_SERVICE_NAME = "nuxeo";

    public static NotificationService getNotificationServiceImpl() {
        if (NOTIFICATION_SERVICE_NAME.equalsIgnoreCase(NUXEO_NOTIFICATION_SERVICE_NAME)) {
            return new NuxeoNotificationServiceImpl();
        } else {
            return new NotificationServiceImpl();
        }
    }

    public static NotificationServiceDTO getNotificationServiceDTO(Map<String, Object> notificationServiceParams) {

        if (NOTIFICATION_SERVICE_NAME.equalsIgnoreCase(NUXEO_NOTIFICATION_SERVICE_NAME)) {
            return createNuxeoNotificationServiceDTO(notificationServiceParams);
        } else {
            return createDefaultNotificationServiceDTO(notificationServiceParams);
        }

    }

    private static NotificationServiceDTO createNuxeoNotificationServiceDTO(Map<String, Object> notificationServiceParams) {
        NotificationServiceDTO notificationServiceDTO = new NotificationServiceDTO();
        Map<String, Object> operationParams = new HashMap<>();

        for (Map.Entry<String, Object> entry : notificationServiceParams.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value != null) {
                if (value instanceof String) {
                    operationParams.put(key, value);
                } else if (value instanceof StringList) {
                    if ("groupsId".equals(key)) {
                        operationParams.put("groups", value);
                    } else if ("usersId".equals(key)) {
                        operationParams.put("users", value);
                    }
                }
            }
        }
        String documentUid = ((DocumentModel) notificationServiceParams.get("input")).getId();

        notificationServiceDTO.setChainParameters(operationParams);
        notificationServiceDTO.setChannelName("System");
        notificationServiceDTO.setDocumentId(documentUid);

        return notificationServiceDTO;
    }

    private static NotificationServiceDTO createDefaultNotificationServiceDTO(Map<String, Object> notificationServiceParams) {
        NotificationServiceDTO notificationServiceDTO = new NotificationServiceDTO();
        String applicationKey = notificationServiceParams.get("applicationKey").toString();
        String notificationKey = notificationServiceParams.get("notificationKey").toString();
        String data = notificationServiceParams.get("data").toString();
        String apiData = notificationServiceParams.get("apiData").toString();
        StringList groupsId = (StringList) notificationServiceParams.get("groupsId");
        StringList usersId = (StringList) notificationServiceParams.get("usersId");
        String sourceType = notificationServiceParams.get("sourceType").toString();
        DocumentModel input = (DocumentModel) notificationServiceParams.get("input");
        CoreSession session = (CoreSession) notificationServiceParams.get("session");

        NotifyDTO notifyDTO = prepareNotifyDTO(input, data, groupsId, usersId, session, applicationKey, sourceType, apiData);

        notificationServiceDTO.setNotificationKey(notificationKey);
        notificationServiceDTO.setChannelName("SYSTEM");
        notificationServiceDTO.setNotifyDTO(notifyDTO);

        return notificationServiceDTO;
    }

    private static NotifyDTO prepareNotifyDTO(DocumentModel input, String data, StringList groupsId,
                                              StringList usersId, CoreSession session, String applicationKey,
                                              String sourceType, String apiData) {
        List<String> userIds = retrieveUsersList(usersId, groupsId);


        RecipientsDTO recipientsDTO = new RecipientsDTO();
        recipientsDTO.setUserIds(userIds);


        // add commonData
        Gson gson = new Gson();
        JsonObject jo = gson.fromJson(data, JsonObject.class);
        HashMap<String, Object> dataMap = new Gson().fromJson(jo.toString(), HashMap.class);
        if (input.hasSchema("Correspondence")) {


            dataMap.put("correspondenceId", input.getId());
            dataMap.put("correspondenceReferenceNumber", input.getPropertyValue(CorrespondenceDataProperties.correspondenceReferenceNumber));
        }
        if (input.hasSchema("assignment")) {

            dataMap.put("assignmentId", input.getId());
            String correspondenceId = (String) input.getProperty(CorrespondenceDataProperties.correspondence).getValue();
            DocumentModel correspondence = session.getDocument(new IdRef(correspondenceId));
            dataMap.put("correspondenceId", correspondence.getId());
            dataMap.put("correspondenceReferenceNumber", correspondence.getPropertyValue(CorrespondenceDataProperties.correspondenceReferenceNumber));
        } else if (input.hasSchema("CommonData")) {
            dataMap.put("committeeId", input.getPropertyValue(CommonDataProperties.committeeId));
            dataMap.put("committeeName", input.getPropertyValue(CommonDataProperties.committeeName));
            dataMap.put("committeeCode", input.getPropertyValue(CommonDataProperties.committeeCode));
            dataMap.put("meetingId", input.getPropertyValue(CommonDataProperties.meetingId));
            dataMap.put("meetingCode", input.getPropertyValue(CommonDataProperties.meetingCode));
        }
        SourceDTO sourceDTO = new SourceDTO();
        sourceDTO.setSourceId(input.getId());
        sourceDTO.setSourceType(sourceType);
        sourceDTO.setSourceName(input.getTitle());
        sourceDTO.setData(dataMap);
        sourceDTO.setApplication(applicationKey);


        // NotifyDTO
        gson = new Gson();
        jo = gson.fromJson(apiData, JsonObject.class);
        HashMap<String, Object> apiDataMap = new Gson().fromJson(jo.toString(), HashMap.class);
        //apiData.put("id", input.getId());
        //apiData.put("id", "1");

        NotifyDTO notifyDTO = new NotifyDTO();
        notifyDTO.setRecipients(recipientsDTO);
        notifyDTO.setSource(sourceDTO);
        notifyDTO.setSendDate(new Date().toString());
        notifyDTO.setApplicationKey(applicationKey);
        notifyDTO.setApiData(apiDataMap);


        return notifyDTO;
    }

    private static List<String> retrieveUsersList(StringList users, StringList groups) {
        if ((groups != null) && (groups.size() > 0)) {
            UserManager userManager = Framework.getService(UserManager.class);

            for (String grp : groups) {
                NuxeoGroup group = userManager.getGroup(grp);
                String label = group.getLabel();
                System.out.println("Group: " + label);
                List<String> grpUsers = group.getMemberUsers();
                for (String u : grpUsers) {
                    System.out.println("User: " + u + "\n");
                    users.add(u);
                }
            }


        }
        List<String> listWithoutDuplicates = users.stream().distinct().collect(Collectors.toList());
        return listWithoutDuplicates;
    }

}
