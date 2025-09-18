package sa.comptechco.nuxeo.common.operations.operations;

import java.util.ArrayList;
import java.util.List;

import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

import sa.comptechco.nuxeo.common.operations.operations.actions.ActionPermissionEvaluator;
import sa.comptechco.nuxeo.common.operations.operations.actions.DocumentActionChecker;

/**
 * Operation to retrieve allowed document actions based on user permissions and restrictions.
 * 
 * This operation provides a comprehensive way to determine what actions a user can perform
 * on a specific document, taking into account:
 * - Document type restrictions
 * - Path-based restrictions  
 * - User group memberships
 * - System-wide permission policies
 * 
 * The operation returns a list of action names that can be used by client applications
 * to show/hide UI elements or enable/disable functionality based on user permissions.
 * 
 * @since 1.0
 */
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
    description = "Retrieves the list of actions that the current user is allowed to perform on the given document. " +
                  "Actions include create, edit, delete operations for both the document and its children."
)
public class DocumentActionsOperation {

    public static final String ID = "Document.DocumentActionsOperation";

    @Context
    protected CoreSession session;

    /**
     * Evaluates and returns all allowed actions for the input document.
     * 
     * This method creates the necessary helper objects to evaluate permissions
     * and returns a list of action names that the current user is allowed to perform.
     * 
     * @param document The document to check permissions for
     * @return List of allowed action names (e.g., "create", "edit", "delete")
     * @throws IllegalArgumentException if document is null
     */
    @OperationMethod
    public List<String> run(DocumentModel document) {
        // Validate input
        if (document == null) {
            // Return empty list for null documents rather than throwing exception
            // This allows the operation to be used safely in chains
            return new ArrayList<>();
        }

        // Create action checker and evaluator
        DocumentActionChecker actionChecker = new DocumentActionChecker(session, document);
        ActionPermissionEvaluator permissionEvaluator = new ActionPermissionEvaluator(actionChecker);
        
        // Evaluate and return allowed actions
        return permissionEvaluator.getAllowedActions();
    }
}