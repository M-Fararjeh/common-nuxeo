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
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.platform.types.localconfiguration.UITypesConfigurationConstants;
import org.nuxeo.runtime.api.Framework;
import sa.comptechco.nuxeo.common.services.api.CustomRestrictionsService;
import sa.comptechco.nuxeo.common.services.model.ActionEnum;

import java.util.*;

import static java.util.stream.Collectors.toSet;

/**
 * Operation to retrieve available document actions based on user permissions and restrictions.
 * 
 * This operation evaluates what actions a user can perform on a document by checking:
 * - Document type restrictions
 * - Path-based restrictions  
 * - User group memberships
 * - Local UI type configurations
 */
@Operation(
    id = DocumentActionsOperation.ID, 
    category = Constants.CAT_DOCUMENT, 
    label = "Get Document Actions", 
    description = "Retrieve available document actions based on user permissions and restrictions."
)
public class DocumentActionsOperation {

    public static final String ID = "Document.DocumentActionsOperation";
    
    private static final String OPERATION_DOCUMENT_ACTIONS = "Document.DocumentActionsOperation";

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
        AutomationService automationService = Framework.getService(AutomationService.class);
        
        try {
            OperationType operation = automationService.getOperation(OPERATION_DOCUMENT_ACTIONS);
            OperationContext operationContext = createOperationContext(document);
            
            @SuppressWarnings("unchecked")
            List<String> actions = (List<String>) automationService.run(
                operationContext, 
                operation.getId(), 
                Collections.emptyMap()
            );
            
            return filterActionsBasedOnRestrictions(document, actions);
            
        } catch (OperationException e) {
            throw new NuxeoException("Failed to retrieve actions for document: " + document.getId(), e);
        }
    }

    /**
     * Creates an operation context for the document actions operation.
     */
    private OperationContext createOperationContext(DocumentModel document) {
        OperationContext operationContext = new OperationContext(session);
        operationContext.setInput(document);
        return operationContext;
    }

    /**
     * Filters actions based on custom restrictions and user permissions.
     * 
     * @param document The document to check
     * @param actions The initial list of actions
     * @return Filtered list of allowed actions
     */
    private List<String> filterActionsBasedOnRestrictions(DocumentModel document, List<String> actions) {
        CustomRestrictionsService restrictionsService = Framework.getService(CustomRestrictionsService.class);
        
        if (restrictionsService == null) {
            return actions;
        }

        List<String> userGroups = session.getPrincipal().getAllGroups();
        String documentPath = getDocumentPath(document);
        String documentType = document.getType();

        return actions.stream()
            .filter(action -> isActionAllowed(restrictionsService, documentPath, documentType, userGroups, action))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * Checks if a specific action is allowed for the user on the document.
     */
    private boolean isActionAllowed(CustomRestrictionsService restrictionsService, 
                                  String documentPath, 
                                  String documentType, 
                                  List<String> userGroups, 
                                  String action) {
        try {
            ActionEnum actionEnum = ActionEnum.valueOf(action.toLowerCase());
            return restrictionsService.checkPathOrTypeAllowed(documentPath, documentType, userGroups, actionEnum);
        } catch (IllegalArgumentException e) {
            // If action is not in enum, allow by default
            return true;
        }
    }

    /**
     * Safely gets the document path, handling null cases.
     */
    private String getDocumentPath(DocumentModel document) {
        return document.getPath() != null ? document.getPath().toString() : "";
    }

    /**
     * Computes subtypes that can be created under this document.
     * This method handles UI type configurations and custom restrictions.
     */
    public Collection<String> computeSubtypes(DocumentModel document) {
        Collection<String> defaultSubtypes = document.getDocumentType().getAllowedSubtypes();
        
        if (hasUITypesConfiguration(document)) {
            defaultSubtypes = computeLocalConfigurationSubtypes(document, defaultSubtypes);
        }

        return filterSubtypesByRestrictions(document, defaultSubtypes);
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

    /**
     * Filters subtypes based on custom restrictions for creation permissions.
     */
    private Collection<String> filterSubtypesByRestrictions(DocumentModel document, 
                                                          Collection<String> subtypes) {
        CustomRestrictionsService restrictionsService = Framework.getService(CustomRestrictionsService.class);
        
        if (restrictionsService == null) {
            return subtypes;
        }

        List<String> userGroups = session.getPrincipal().getAllGroups();
        String documentPath = getDocumentPath(document);

        // Check if create_child is allowed on parent document
        if (!restrictionsService.checkPathOrTypeAllowed(documentPath, document.getType(), 
                                                       userGroups, ActionEnum.create_child)) {
            return Collections.emptySet();
        }

        // Filter subtypes based on create permissions
        return subtypes.stream()
            .filter(subtype -> restrictionsService.checkPathOrTypeAllowed(null, subtype, 
                                                                        userGroups, ActionEnum.create))
            .collect(toSet());
    }

    // Helper methods for property access

    private Boolean getBooleanProperty(DocumentModel document, String propertyName) {
        try {
            return (Boolean) document.getPropertyValue(propertyName);
        } catch (Exception e) {
            return null;
        }
    }

    private String[] getStringArrayProperty(DocumentModel document, String propertyName) {
        try {
            return (String[]) document.getPropertyValue(propertyName);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> arrayToList(String[] array) {
        return array == null ? Collections.emptyList() : Arrays.asList(array);
    }
}