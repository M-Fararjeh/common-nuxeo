package sa.comptechco.nuxeo.common.operations.operations;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.*;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import sa.comptechco.nuxeo.common.operations.utils.DepartmentUnrestrictedRun;

import java.util.HashMap;
import java.util.Map;

/**
 * Operation for adding Keycloak users to department groups.
 * 
 * This operation handles the integration between Keycloak user management
 * and Nuxeo department-based group assignments. It automatically determines
 * the appropriate role based on department type.
 */
@Operation(id = DepartmentOperation.ID, category = Constants.CAT_USERS_GROUPS, label = "Add User To Group", description = "Add keycloak  User To dept Group.")
public class DepartmentOperation {

    public static final String ID = "Services.AddKeyCloakUserToDeptGroup";

    private static final String KEYCLOAK_USER_ADD_OPERATION = "AC_Keyclock_User_AddToDepartment";
    private static final String DEFAULT_ROLE = "Employee";
    private static final String CONTRIBUTOR_ROLE = "Contributor";
    private static final String OS_DEPARTMENT_TYPE = "OSDepartment";

    @Param(name = "department")
    protected String department;

    @Param(name = "role")
    protected String role;

    @Param(name = "username")
    protected String username;

    @OperationMethod
    public void run() {
        validateInputParameters();
        
        DocumentModel departmentDoc = findDepartmentDocument();
        if (departmentDoc == null) {
            throw new NuxeoException("Department not found: " + department);
        }

        String resolvedRole = determineUserRole(departmentDoc);
        addUserToDepartmentGroup(departmentDoc, resolvedRole);
    }

    /**
     * Validates that required parameters are provided.
     */
    private void validateInputParameters() {
        if (StringUtils.isBlank(department)) {
            throw new IllegalArgumentException("Department parameter is required");
        }
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("Username parameter is required");
        }
    }

    /**
     * Finds the department document using unrestricted access.
     */
    private DocumentModel findDepartmentDocument() {
        DepartmentUnrestrictedRun departmentRunner = new DepartmentUnrestrictedRun(null, department);
        departmentRunner.runUnrestricted();
        return departmentRunner.getDepartment();
    }

    /**
     * Determines the appropriate role for the user based on department type.
     */
    private String determineUserRole(DocumentModel departmentDoc) {
        if (StringUtils.isNotBlank(role)) {
            return role;
        }

        return OS_DEPARTMENT_TYPE.equals(departmentDoc.getType()) ? CONTRIBUTOR_ROLE : DEFAULT_ROLE;
    }

    /**
     * Adds the user to the department group using automation.
     */
    private void addUserToDepartmentGroup(DocumentModel departmentDoc, String userRole) {
        AutomationService automationService = Framework.getService(AutomationService.class);
        
        try {
            OperationType operation = automationService.getOperation(KEYCLOAK_USER_ADD_OPERATION);
            OperationContext operationContext = createOperationContext(departmentDoc, userRole);
            
            automationService.run(operationContext, operation.getId(), new HashMap<>());
            
        } catch (OperationException e) {
            throw new NuxeoException("Failed to add user to department group", e);
        }
    }

    /**
     * Creates operation context with required parameters.
     */
    private OperationContext createOperationContext(DocumentModel departmentDoc, String userRole) {
        OperationContext context = new OperationContext();
        context.setCoreSession(null);
        context.put("role", userRole);
        context.put("department", departmentDoc.getTitle());
        context.put("username", username);
        return context;
    }
}