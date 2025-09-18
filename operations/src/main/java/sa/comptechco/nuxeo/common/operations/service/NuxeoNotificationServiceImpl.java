package sa.comptechco.nuxeo.common.operations.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.StreamManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.StreamService;
import sa.comptechco.nuxeo.common.operations.dto.NotificationServiceDTO;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class NuxeoNotificationServiceImpl implements NotificationService{
    static final Logger log = LogManager.getLogger(NuxeoNotificationServiceImpl.class);
    private static final String NOTIFICATION_TOPIC = "process/notifications";

    private static final String NUXEO_VALIDATION_NOTIFICATION_OPERATION_NAME = "AC_VA_Notf_SendSystemNotification";
    private static final AutomationService AUTOMATION_SERVICE = Framework.getService(AutomationService.class);
    @Override
    public void callNotification(NotificationServiceDTO notificationServiceDTO) throws IOException {


        if(isNotificationContentValid(notificationServiceDTO)) {
            sendMessageToKafka(notificationServiceDTO);
        }
        else{
            log.error("the notification content is not valid");
        }

    }

    private static void sendMessageToKafka(NotificationServiceDTO notificationServiceDTO) {
        String messageKey = notificationServiceDTO.getDocumentId();
        //NuxeoOperationsService operationsService = Framework.getService(NuxeoOperationsService.class);
        //StreamManager streamManager = operationsService.getStreamManager();
        //String topicName = operationsService.getNotificationTopicString();
        StreamManager streamManager = Framework.getService(StreamService.class).getStreamManager();

        Record record = Record.of(messageKey, toByteArray(notificationServiceDTO));

        streamManager.append(NOTIFICATION_TOPIC, record);
    }

    private static boolean isNotificationContentValid(NotificationServiceDTO notificationServiceDTO) {
        //DocumentModel doc = getDocumentModel(notificationServiceDTO.getDocumentId());
        String documentId = notificationServiceDTO.getDocumentId();

        try {

            //Map<String,Object> chainParameters = notificationServiceDTO.getChainParameters();
            Map<String,Object> chainParameters = new HashMap<>();
            chainParameters.put("notificationKey",notificationServiceDTO.getChainParameters().get("notificationKey"));
                chainParameters.put("applicationKey",notificationServiceDTO.getChainParameters().get("applicationKey"));
            chainParameters.put("data",notificationServiceDTO.getChainParameters().get("data"));

            CoreSession coreSession = CoreInstance.getCoreSessionSystem("default");
            DocumentRef docRef = new IdRef(documentId);
            DocumentModel doc = coreSession.getDocument(docRef);
            OperationContext operationContext = new OperationContext();
            operationContext.setInput(doc);
            //operationContext.setCoreSession(coreSession);

            AUTOMATION_SERVICE.run(operationContext, NUXEO_VALIDATION_NOTIFICATION_OPERATION_NAME, chainParameters);

            return true;
        } catch (Exception e) {
            return false;
        }
    }
    private static byte[] toByteArray(NotificationServiceDTO notificationDTO) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsBytes(notificationDTO);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing notificationServiceDTO", e);
        }
    }
}
