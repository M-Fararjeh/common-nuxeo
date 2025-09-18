package sa.comptechco.nuxeo.common.operations.utils.kafka;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationNotFoundException;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.webengine.model.exceptions.WebResourceNotFoundException;
import org.nuxeo.lib.stream.computation.AbstractBatchComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.api.login.NuxeoLoginContext;
import org.nuxeo.runtime.stream.StreamProcessorTopology;
import org.nuxeo.runtime.transaction.TransactionHelper;
import sa.comptechco.nuxeo.common.operations.dto.NotificationServiceDTO;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class StreamProcessNotifications implements StreamProcessorTopology {

    private static final String NUXEO_SEND_NOTIFICATION_OPERATION_NAME = "AC_Notf_SendNotification";
    private static final AutomationService AUTOMATION_SERVICE = Framework.getService(AutomationService.class);

    private static final Log log = LogFactory.getLog(StreamProcessNotifications.class);

    public static final String COMPUTATION_NAME = "computation/notifications";

    @Override
    public Topology getTopology(Map<String, String> options) {
        return Topology.builder()
                .addComputation(
                        () -> new StreamProcessNotifications.ProcessNotificationsComputation(COMPUTATION_NAME),
                        Collections.singletonList("i1:" + "process/notifications"))
                .build();
    }

    public static class ProcessNotificationsComputation extends AbstractBatchComputation {

        public ProcessNotificationsComputation(String name) {
            super(name, 1, 0);
        }

        @Override
        public void batchProcess(ComputationContext context, String inputStreamName, List<Record> records) {
            //log.error("batch process");
        }

        @Override
        public void batchFailure(ComputationContext context, String inputStreamName, List<Record> records) {
            // error log already done by abstract
        }

        @Override
        public void processRecord(ComputationContext context, String inputStreamName, Record record) {

            //log.error("Hello From processRecord");
            byte[] messageData = record.getData();

            ObjectMapper objectMapper = new ObjectMapper();
            try {
                TransactionHelper.startTransaction();
                NotificationServiceDTO notificationServiceDTO = objectMapper.readValue(messageData, NotificationServiceDTO.class);
                //log.error(" notification object is " + notificationServiceDTO);
                //log.error(" before notificationService.callNotification" );
                //notificationService.callNotification(notificationServiceDTO);

                sendNotification(notificationServiceDTO);

                //log.error(" after notificationService.callNotification" );

                context.askForCheckpoint();
                TransactionHelper.commitOrRollbackTransaction();
            } catch (IOException e) {
                log.error("Exception while processRecord" + e);
                //TransactionHelper.setTransactionRollbackOnly();
            }
            finally {
                TransactionHelper.commitOrRollbackTransaction();
            }
        }
        private static void sendNotification(NotificationServiceDTO notificationServiceDTO) {
            String documentId = notificationServiceDTO.getDocumentId();

            try {
                Map<String,Object> chainParameters = notificationServiceDTO.getChainParameters();
                CoreSession coreSession = CoreInstance.getCoreSessionSystem("default");
                DocumentRef docRef = new IdRef(documentId);
                DocumentModel doc = coreSession.getDocument(docRef);
                OperationContext operationContext = new OperationContext();
                operationContext.setInput(doc);
                operationContext.setCoreSession(coreSession);

                AUTOMATION_SERVICE.run(operationContext, NUXEO_SEND_NOTIFICATION_OPERATION_NAME, chainParameters);

            } catch (OperationNotFoundException cause) {
                throw new WebResourceNotFoundException("Failed to invoke operation: " + NUXEO_SEND_NOTIFICATION_OPERATION_NAME, cause);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
