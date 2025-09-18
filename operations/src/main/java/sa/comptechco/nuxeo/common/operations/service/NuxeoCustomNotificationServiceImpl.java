package sa.comptechco.nuxeo.common.operations.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.OperationNotFoundException;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.webengine.model.exceptions.WebResourceNotFoundException;
import org.nuxeo.runtime.api.Framework;
import sa.comptechco.nuxeo.common.operations.dto.NotifyDTO;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NuxeoCustomNotificationServiceImpl    implements  NuxeoCustomNotificationService{


    private static final String NUXEO_SEND_NOTIFICATIO_OPERATION = "AC_Notf_SendNotification";

    public NuxeoCustomNotificationServiceImpl() {


    }


    public Object callNotification(DocumentModel doc, String applicationKey, String notificationKey, String channel, String data, List<String> groups, List<String> users, String sourceTypee, String customMetaData,String companyName) throws  RuntimeException{

        AutomationService service = Framework.getService(AutomationService.class);

        try {

            if(StringUtils.isEmpty(companyName)) {

                if(doc.hasSchema("Company")) {
                    System.out.println("doc has company schema");
                    companyName = (String) doc.getPropertyValue("comp:companyCode");
                }
                else {
                    companyName ="default";
                }
                /*else {
                    MultiTenantService multiTenantService = Framework.getService(MultiTenantService.class);

                    CoreSession coreSession = CoreInstance.getCoreSession("default");

                if(multiTenantService.isTenantIsolationEnabled(coreSession)) {

                    DocumentModel domain = getParentDocument(service, coreSession, doc);
                    if (domain != null) {
                        String tenantId = (String) domain.getPropertyValue("tenantconfig:tenantId");
                        if (!tenantId.equals("default-domain")) {
                            companyName = tenantId;
                        }
                        else
                            companyName = "default";
                    }

                }
            }*/
            }
            Map<String, Object> params = new HashMap<>();
            params.put("applicationKey", applicationKey);
            params.put("notificationKey", notificationKey);
            params.put("companyName",companyName);
            params.put("data", data);
            params.put("groups", groups);
            params.put("users", users);
            params.put("sourceTypee", sourceTypee);
            params.put("customMetaData", customMetaData);
            CoreSession coresession = CoreInstance.getCoreSessionSystem("default");
            OperationContext operationContext = new OperationContext();
            operationContext.setInput(doc);
            operationContext.setCoreSession(coresession);


            service.run(operationContext, NUXEO_SEND_NOTIFICATIO_OPERATION, params);

        } catch (OperationNotFoundException cause) {
            return new WebResourceNotFoundException("Failed to invoke operation: " + NUXEO_SEND_NOTIFICATIO_OPERATION, cause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return doc;

    }


    public DocumentModel getParentDocument(AutomationService service,CoreSession session, DocumentModel doc) throws OperationException {
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(doc);
        Map<String, Object> params = new HashMap<>();
        DocumentModel parentDoc = (DocumentModel) service.run(ctx, "Document.GetParent", params);
        return parentDoc;
    }


private String writeJSON(NotifyDTO notDTO) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String jsonInString = mapper.writeValueAsString(notDTO);
        return jsonInString;
    }


}
