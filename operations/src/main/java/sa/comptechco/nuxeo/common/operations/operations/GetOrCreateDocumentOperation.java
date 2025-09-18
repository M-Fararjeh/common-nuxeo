package sa.comptechco.nuxeo.common.operations.operations;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;

/**
 *
 */
@Operation(id= GetOrCreateDocumentOperation.ID, category=Constants.CAT_DOCUMENT, label="Get or Create a Document", description="Get a document if exist and create it if not.")
public class GetOrCreateDocumentOperation {

    private static Log logger = LogFactory.getLog(GetOrCreateDocumentOperation.class);
    public static final String ID = "Document.GetOrCreateDocument";

    @Param(name = "type",required = true)
    protected  String type ;


    @Param(name = "name", required = true)
    protected  String name ;

    @Context
    protected CoreSession session;



    @OperationMethod
    public DocumentModel run(DocumentModel input) {
        DocumentModel dcModel = session.createDocumentModel(input.getPathAsString(), name, type);
        try {
            return session.getOrCreateDocument(dcModel);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Failed to create a document with " + name + " for first time, trying again");
            return session.getOrCreateDocument(dcModel);
        }
    }

}
