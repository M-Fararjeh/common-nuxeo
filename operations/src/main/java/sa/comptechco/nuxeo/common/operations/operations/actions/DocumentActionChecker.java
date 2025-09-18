package sa.comptechco.nuxeo.common.operations.operations.actions;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.api.Framework;
import sa.comptechco.nuxeo.common.services.api.CustomRestrictionsService;
import sa.comptechco.nuxeo.common.services.model.ActionEnum;

import java.util.List;

/**
 * Helper class to check document action permissions.
 * 
 * This class encapsulates the logic for determining whether specific actions
 * are allowed on a document based on user permissions and system restrictions.
 */
public class DocumentActionChecker {
    
    private final CoreSession session;
    private final DocumentModel document;
    private final CustomRestrictionsService restrictionsService;
    private final List<String> userGroups;
    private final String documentPath;
    private final String documentType;

    /**
     * Creates a new DocumentActionChecker for the given session and document.
     * 
     * @param session The current core session
     * @param document The document to check permissions for
     */
    public DocumentActionChecker(CoreSession session, DocumentModel document) {
        this.session = session;
        this.document = document;
        // Try to get the restrictions service, but handle gracefully if not available
        CustomRestrictionsService tempService = null;
        try {
            tempService = Framework.getService(CustomRestrictionsService.class);
        } catch (Exception e) {
            // Service not available, will default to allowing all actions
        }
        this.restrictionsService = tempService;
        this.userGroups = session.getPrincipal().getAllGroups();
        this.documentPath = getDocumentPath();
        this.documentType = document.getType();
    }

    /**
     * Checks if a specific action is allowed for the current document and user.
     * 
     * @param action The action to check
     * @return true if the action is allowed, false otherwise
     */
    public boolean isActionAllowed(ActionEnum action) {
        if (restrictionsService == null) {
            // If no restrictions service is available, allow all actions
            return true;
        }

        return restrictionsService.checkPathOrTypeAllowed(
            documentPath, 
            documentType, 
            userGroups, 
            action
        );
    }

    /**
     * Gets the document path, handling potential null values safely.
     * 
     * @return The document path as string, or null if not available
     */
    private String getDocumentPath() {
        if (document.getPath() != null) {
            return document.getPath().toString();
        }
        return null;
    }

    /**
     * Gets the core session associated with this checker.
     * 
     * @return The core session
     */
    public CoreSession getSession() {
        return session;
    }

    /**
     * Gets the document being checked.
     * 
     * @return The document model
     */
    public DocumentModel getDocument() {
        return document;
    }

    /**
     * Gets the user groups for the current principal.
     * 
     * @return List of user group names
     */
    public List<String> getUserGroups() {
        return userGroups;
    }

    /**
     * Gets the document type.
     * 
     * @return The document type name
     */
    public String getDocumentType() {
        return documentType;
    }
}