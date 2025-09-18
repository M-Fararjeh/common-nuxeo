package sa.comptechco.nuxeo.common.operations.operations;

import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.api.Framework;
import sa.comptechco.nuxeo.common.services.api.CustomRestrictionsService;
import sa.comptechco.nuxeo.common.services.model.ActionEnum;

import java.util.ArrayList;
import java.util.List;

/**
 * Operation to retrieve allowed document actions based on user permissions and restrictions.
 * 
 * This operation checks various action permissions for a document including:
 * - Create operations
 * - Edit operations  
 * - Delete operations
 * - Child document operations
 * 
 * The permissions are evaluated based on:
 * - Document path restrictions
 * - Document type restrictions
 * - User group memberships
 */
@Operation(
    id = DocumentActionsOperation.ID, 
    category = Constants.CAT_DOCUMENT, 
    label = "Get Document Allowed Actions", 
    description = "Retrieves the list of actions that the current user is allowed to perform on the given document."
)
public class DocumentActionsOperation {

    public static final String ID = "Document.DocumentActionsOperation";

    @Context
    protected CoreSession session;

    /**
     * Main operation method that returns allowed actions for the input document.
     * 
     * @param document The document to check permissions for
     * @return List of allowed action names
     */
    @OperationMethod
    public List<String> run(DocumentModel document) {
        if (document == null) {
            return new ArrayList<>();
        }

        DocumentActionChecker actionChecker = new DocumentActionChecker(session, document);
        return actionChecker.getAllowedActions();
    }

    /**
     * Helper class to encapsulate document action checking logic.
     * Separates concerns and makes the code more testable and maintainable.
     */
    private static class DocumentActionChecker {
        
        private final CoreSession session;
        private final DocumentModel document;
        private final CustomRestrictionsService restrictionsService;
        private final List<String> userGroups;
        private final String documentPath;
        private final String documentType;

        public DocumentActionChecker(CoreSession session, DocumentModel document) {
            this.session = session;
            this.document = document;
            this.restrictionsService = Framework.getService(CustomRestrictionsService.class);
            this.userGroups = session.getPrincipal().getAllGroups();
            this.documentPath = getDocumentPath();
            this.documentType = document.getType();
        }

        /**
         * Gets all allowed actions for the current document and user.
         * 
         * @return List of allowed action names
         */
        public List<String> getAllowedActions() {
            List<String> allowedActions = new ArrayList<>();

            // Check each action type
            if (isActionAllowed(ActionEnum.create)) {
                allowedActions.add(ActionEnum.create.getValue());
            }

            if (isActionAllowed(ActionEnum.create_child)) {
                allowedActions.add(ActionEnum.create_child.getValue());
            }

            if (isActionAllowed(ActionEnum.edit)) {
                allowedActions.add(ActionEnum.edit.getValue());
            }

            if (isActionAllowed(ActionEnum.edit_child)) {
                allowedActions.add(ActionEnum.edit_child.getValue());
            }

            if (isActionAllowed(ActionEnum.delete)) {
                allowedActions.add(ActionEnum.delete.getValue());
            }

            if (isActionAllowed(ActionEnum.delete_child)) {
                allowedActions.add(ActionEnum.delete_child.getValue());
            }

            return allowedActions;
        }

        /**
         * Checks if a specific action is allowed for the current document and user.
         * 
         * @param action The action to check
         * @return true if the action is allowed, false otherwise
         */
        private boolean isActionAllowed(ActionEnum action) {
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
    }
}