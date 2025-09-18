package sa.comptechco.nuxeo.common.operations.operations;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * Operation to get an existing document or create it if it doesn't exist.
 * 
 * This operation provides a safe way to ensure a document exists at a specific
 * location with a given type and name, creating it only if necessary.
 */
@Operation(id = GetOrCreateDocumentOperation.ID, category = Constants.CAT_DOCUMENT, label = "Get or Create Document", description = "Get a document if it exists, create it if it doesn't.")
public class GetOrCreateDocumentOperation {

    private static Log logger = LogFactory.getLog(GetOrCreateDocumentOperation.class);
    public static final String ID = "Document.GetOrCreateDocument";

    @Param(name = "type", required = true)
    protected String type;

    @Param(name = "name", required = true)
    protected String name;

    @Context
    protected CoreSession session;

    @OperationMethod
    public DocumentModel run(DocumentModel input) {
        validateInputParameters(input);
        
        DocumentModel documentModel = createDocumentModel(input);
        return getOrCreateDocument(documentModel);
    }

    /**
     * Validates input parameters and parent document.
     */
    private void validateInputParameters(DocumentModel parent) {
        if (parent == null) {
            throw new IllegalArgumentException("Parent document is required");
        }
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Document type is required");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Document name is required");
        }
    }

    /**
     * Creates a document model with the specified parameters.
     */
    private DocumentModel createDocumentModel(DocumentModel parent) {
        return session.createDocumentModel(parent.getPathAsString(), name, type);
    }

    /**
     * Gets existing document or creates it with retry logic.
     */
    private DocumentModel getOrCreateDocument(DocumentModel documentModel) {
        try {
            return session.getOrCreateDocument(documentModel);
        } catch (Exception e) {
            logger.warn("Failed to get/create document on first attempt, retrying: " + e.getMessage());
            return retryGetOrCreateDocument(documentModel);
        }
    }

    /**
     * Retries document creation if the first attempt fails.
     */
    private DocumentModel retryGetOrCreateDocument(DocumentModel documentModel) {
        try {
            return session.getOrCreateDocument(documentModel);
        } catch (Exception e) {
            String errorMessage = String.format("Failed to get or create document '%s' of type '%s'", name, type);
            logger.error(errorMessage, e);
            throw new NuxeoException(errorMessage, e);
        }
    }
}