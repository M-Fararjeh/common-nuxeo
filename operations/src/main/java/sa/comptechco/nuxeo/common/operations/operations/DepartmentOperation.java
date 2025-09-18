package sa.comptechco.nuxeo.common.operations.operations;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.*;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.platform.comment.api.CommentManager;
import org.nuxeo.runtime.api.Framework;
import sa.comptechco.nuxeo.common.operations.utils.DepartmentUnrestrictedRun;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
@Operation(id = DepartmentOperation.ID, category = Constants.CAT_USERS_GROUPS, label = "Add User To Group", description = "Add keycloak  User To dept Group.")
public class DepartmentOperation {

    public static final String ID = "Services.AddKeyCloakUserToDeptGroup";

    @Param(name = "department")
    protected String department = "";

    @Param(name = "role")
    protected String role = null;

    @Param(name = "username")
    protected String username;


    @OperationMethod
    public void run() {

        DepartmentUnrestrictedRun departmentUnrestrictedRun = new DepartmentUnrestrictedRun(null, department);
        departmentUnrestrictedRun.runUnrestricted();
        DocumentModel departmentDoc = departmentUnrestrictedRun.getDepartment();
        if (department != null) {

            AutomationService service = Framework.getService(AutomationService.class);

            OperationType operation = null;
            try {

                operation = service.getOperation("AC_Keyclock_User_AddToDepartment");

                Map<String, Object> params = new HashMap<>();
                OperationContext operationContext = new OperationContext();
                operationContext.setCoreSession(null);


                if (StringUtils.isEmpty(role)) {
                    role = "Employee";
                }
                if(departmentDoc.getType().equals("OSDepartment"))
                {
                    role = "Contributor";
                }
                operationContext.put("role", role);
                operationContext.put("department", departmentDoc.getTitle());
                operationContext.put("username", username);
                service.run(operationContext, operation.getId(), params);
            } catch (OperationException e) {
                e.printStackTrace();
                throw new NuxeoException("failed to add user to department");

            }
        }
    }
}
