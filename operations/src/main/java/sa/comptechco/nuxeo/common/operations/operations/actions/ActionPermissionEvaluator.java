package sa.comptechco.nuxeo.common.operations.operations.actions;

import sa.comptechco.nuxeo.common.services.model.ActionEnum;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates and collects allowed actions for a document.
 * 
 * This class provides a centralized way to evaluate all possible actions
 * and return only those that are permitted for the current context.
 */
public class ActionPermissionEvaluator {
    
    private final DocumentActionChecker actionChecker;

    /**
     * Creates a new ActionPermissionEvaluator with the given action checker.
     * 
     * @param actionChecker The action checker to use for permission evaluation
     */
    public ActionPermissionEvaluator(DocumentActionChecker actionChecker) {
        this.actionChecker = actionChecker;
    }

    /**
     * Gets all allowed actions for the document and user context.
     * 
     * This method systematically checks each action type and includes
     * only those that are permitted in the returned list.
     * 
     * @return List of allowed action names
     */
    public List<String> getAllowedActions() {
        List<String> allowedActions = new ArrayList<>();

        // Check basic document actions
        addActionIfAllowed(allowedActions, ActionEnum.create);
        addActionIfAllowed(allowedActions, ActionEnum.edit);
        addActionIfAllowed(allowedActions, ActionEnum.delete);

        // Check child document actions
        addActionIfAllowed(allowedActions, ActionEnum.create_child);
        addActionIfAllowed(allowedActions, ActionEnum.edit_child);
        addActionIfAllowed(allowedActions, ActionEnum.delete_child);

        return allowedActions;
    }

    /**
     * Checks if an action is allowed and adds it to the list if permitted.
     * 
     * @param allowedActions The list to add the action to
     * @param action The action to check
     */
    private void addActionIfAllowed(List<String> allowedActions, ActionEnum action) {
        if (actionChecker.isActionAllowed(action)) {
            allowedActions.add(action.getValue());
        }
    }

    /**
     * Checks if a specific action is allowed.
     * 
     * @param action The action to check
     * @return true if the action is allowed, false otherwise
     */
    public boolean isActionAllowed(ActionEnum action) {
        return actionChecker.isActionAllowed(action);
    }

    /**
     * Gets the underlying action checker.
     * 
     * @return The document action checker
     */
    public DocumentActionChecker getActionChecker() {
        return actionChecker;
    }
}