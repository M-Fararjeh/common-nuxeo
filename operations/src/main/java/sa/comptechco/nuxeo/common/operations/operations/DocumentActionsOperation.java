package sa.comptechco.nuxeo.common.operations.operations;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.OperationType;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.types.localconfiguration.UITypesConfigurationConstants;
import org.nuxeo.runtime.api.Framework;

import java.util.*;

import static java.util.stream.Collectors.toSet;

/**
 * Operation to retrieve available document actions based on user permissions.
 * 
 * This operation evaluates what actions a user can perform on a document by checking:
 * - Document type permissions
 * - User group memberships
 * - Local UI type configurations
 */
@Operation(
    id = DocumentActionsOperation.ID, 
    category = Constants.CAT_DOCUMENT, 
    label = "Get Document Actions", 
    description = "Retrieve available document actions based on user permissions."
)
public class DocumentActionsOperation {

    public static final String ID = "Document.DocumentActionsOperation";
    
    // Standard document actions
    private static final String ACTION_CREATE = "create";
    private static final String ACTION_EDIT = "edit";
    private static final String ACTION_DELETE = "delete";
    private static final String ACTION_CREATE_CHILD = "create_child";

    @Context
    protected CoreSession session;

    @OperationMethod
    public List<String> run(DocumentModel input) {
        try {
            return getDocumentActions(input);
        } catch (Exception e) {
            throw new NuxeoException("Failed to retrieve document actions", e);
        }
    }

    /**
     * Retrieves the list of available actions for the given document.
     * 
     * @param document The document to check actions for
     * @return List of available action names
     */
    private List<String> getDocumentActions(DocumentModel document) {
        List<String> actions = new ArrayList<>();
        
        // Check basic permissions
        if (canPerformAction(document, ACTION_CREATE)) {
            actions.add(ACTION_CREATE);
        }
        
        if (canPerformAction(document, ACTION_EDIT)) {
            actions.add(ACTION_EDIT);
        }
        
        if (canPerformAction(document, ACTION_DELETE)) {
            actions.add(ACTION_DELETE);
        }
        
        if (canPerformAction(document, ACTION_CREATE_CHILD)) {
            actions.add(ACTION_CREATE_CHILD);
        }
        
        return actions;
    }

    /**
     * Checks if the current user can perform a specific action on the document.
     */
    private boolean canPerformAction(DocumentModel document, String action) {
        try {
            switch (action) {
                case ACTION_CREATE:
                    return hasCreatePermission(document);
                case ACTION_EDIT:
                    return hasEditPermission(document);
                case ACTION_DELETE:
                    return hasDeletePermission(document);
                case ACTION_CREATE_CHILD:
                    return hasCreateChildPermission(document);
                default:
                    return false;
            }
        } catch (Exception e) {
            // Log error and deny permission by default
            return false;
        }
    }

    /**
     * Checks if user has create permission.
     */
    private boolean hasCreatePermission(DocumentModel document) {
        return session.hasPermission(document.getRef(), "Write");
    }

    /**
     * Checks if user has edit permission.
     */
    private boolean hasEditPermission(DocumentModel document) {
        return session.hasPermission(document.getRef(), "Write");
    }

    /**
     * Checks if user has delete permission.
     */
    private boolean hasDeletePermission(DocumentModel document) {
        return session.hasPermission(document.getRef(), "Remove");
    }

    /**
     * Checks if user has permission to create child documents.
     */
    private boolean hasCreateChildPermission(DocumentModel document) {
        if (!document.isFolder()) {
            return false;
        }
        
        Collection<String> allowedSubtypes = computeSubtypes(document);
        return !allowedSubtypes.isEmpty() && session.hasPermission(document.getRef(), "AddChildren");
    }

    /**
     * Computes subtypes that can be created under this document.
     * This method handles UI type configurations.
     */
    private Collection<String> computeSubtypes(DocumentModel document) {
        Collection<String> defaultSubtypes = document.getDocumentType().getAllowedSubtypes();
        
        if (hasUITypesConfiguration(document)) {
            defaultSubtypes = computeLocalConfigurationSubtypes(document, defaultSubtypes);
        }

        return defaultSubtypes;
    }

    /**
     * Checks if document has UI types configuration facet.
     */
    private boolean hasUITypesConfiguration(DocumentModel document) {
        return document.hasFacet(UITypesConfigurationConstants.UI_TYPES_CONFIGURATION_FACET);
    }

    /**
     * Computes subtypes based on local UI configuration.
     */
    private Collection<String> computeLocalConfigurationSubtypes(DocumentModel document, 
                                                               Collection<String> defaultSubtypes) {
        Boolean denyAllTypes = getBooleanProperty(document, 
            UITypesConfigurationConstants.UI_TYPES_CONFIGURATION_DENY_ALL_TYPES_PROPERTY);
            
        if (BooleanUtils.isNotTrue(denyAllTypes)) {
            String[] allowedTypes = getStringArrayProperty(document, 
                UITypesConfigurationConstants.UI_TYPES_CONFIGURATION_ALLOWED_TYPES_PROPERTY);
            String[] deniedTypes = getStringArrayProperty(document, 
                UITypesConfigurationConstants.UI_TYPES_CONFIGURATION_DENIED_TYPES_PROPERTY);
                
            List<String> allowedTypesList = arrayToList(allowedTypes);
            List<String> deniedTypesList = arrayToList(deniedTypes);
            
            return defaultSubtypes.stream()
                .filter(subtype -> !deniedTypesList.contains(subtype))
                .filter(subtype -> allowedTypesList.isEmpty() || allowedTypesList.contains(subtype))
                .collect(toSet());
        }
        
        return Collections.emptySet();
    }

    // Helper methods for property access

    /**
     * Safely gets a boolean property value.
     */
    private Boolean getBooleanProperty(DocumentModel document, String propertyName) {
        try {
            return (Boolean) document.getPropertyValue(propertyName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Safely gets a string array property value.
     */
    private String[] getStringArrayProperty(DocumentModel document, String propertyName) {
        try {
            return (String[]) document.getPropertyValue(propertyName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Converts string array to list, handling null values.
     */
    private List<String> arrayToList(String[] array) {
        return array == null ? Collections.emptyList() : Arrays.asList(array);
    }
}