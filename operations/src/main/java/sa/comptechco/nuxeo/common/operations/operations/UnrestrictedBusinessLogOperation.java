package sa.comptechco.nuxeo.common.operations.operations;

import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.platform.comment.api.CommentManager;
import org.nuxeo.runtime.api.Framework;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 *
 */
@Operation(id= UnrestrictedBusinessLogOperation.ID, category=Constants.CAT_DOCUMENT, label="Create Comment", description="Create comments on docs.")
public class UnrestrictedBusinessLogOperation {

    public static final String ID = "Document.CreateBusinessLog";

    @Param(name = "eventCategory")
    protected  String  eventCategory;

    @Param(name = "eventName")
    protected  String  eventName;

    @Param(name = "eventDate")
    protected  Date  eventDate;

    @Param(name = "eventTypes")
    protected  String  eventTypes;

    @Param(name = "eventComment")
    protected  String  eventComment;

    @Param(name = "documentTypes")
    protected  String  documentTypes;

    @Param(name = "extendedInfo")
    protected  String  extendedInfo;

    @Param(name = "currentLifeCycle")
    protected String currentLifeCycle;

    @Param(name = "person")
    protected String person;


    @Context
    protected CoreSession session;


    @OperationMethod
    public DocumentModel run(DocumentModel input) {
        if (session.getPrincipal().isAdministrator()) {
            DocumentModel parent = session.getParentDocument(input.getRef());
            DocumentModel businessLogFolder = session.getChild(parent.getRef(), "Business Logs");
            //DocumentModel businessLog = session.createDocumentModel(businessLogFolder.getPathAsString(), eventName + " log", "BusinessLog");
            //session.createDocumentModel("BusinessLog",p)
            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("bl:documentId", input.getId());
            properties.put("bl:documentLifecycle", currentLifeCycle);
            properties.put("bl:documentName", input.getName());
            properties.put("bl:eventDate", eventDate);
            properties.put("bl:documentPath", input.getPathAsString());
            properties.put("bl:documentTypes", input.getType());
            properties.put("bl:eventCategory", eventCategory);
            properties.put("bl:eventComment", eventComment);
            properties.put("bl:eventName", eventName);
            properties.put("bl:eventTypes", eventTypes);
            properties.put("bl:extendedInfo", extendedInfo);
            properties.put("bl:person", person);
            //businessLog.setProperties("businesslog", properties);
            Map<String, Object> options = new HashMap<>();
            options.put(CoreEventConstants.PARENT_PATH, businessLogFolder.getPathAsString());
            options.put(CoreEventConstants.DESTINATION_NAME,eventName + " log" );

            DocumentModel businessLog = session.createDocumentModel("BusinessLog", options);
            businessLog.setProperties("businesslog",properties);
            businessLog.addFacet("Company");
            businessLog.setProperties("Company", input.getProperties("Company"));
            businessLog=session.createDocument(businessLog);
            session.saveDocument(businessLog);
            //session.save();
            return businessLog;
        } else {
            throw new RuntimeException("not allowed to add comment");
        }
    }
}
