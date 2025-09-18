package sa.comptechco.nuxeo.common.operations.operations;

import org.apache.commons.collections.CollectionUtils;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.util.DocumentHelper;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.utils.DocumentModelUtils;
import org.nuxeo.ecm.multi.tenant.MultiTenantService;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Operation(id = UserActionsOperation.ID, category = Constants.CAT_DOCUMENT, label = "Get User Allowed Actions", description = "Get User Allowed Actions.")
public class UserActionsOperation {

    public static final String ID = "User.UserActionsOperation";

    private static final String INCOMING_CORRESPONDENCE = "IncomingCorrespondence";
    private static final String OUTGOING_CORRESPONDENCE = "OutgoingCorrespondence";
    private static final String INTERNAL_CORRESPONDENCE = "InternalCorrespondence";
    private static final String WORKSPACE = "Workspace";


    @Context
    protected CoreSession session;

    @OperationMethod
    public List<String> run() {

        List<String> availableActions = new ArrayList<>();

        NuxeoPrincipal currentUser = session.getPrincipal();

        //get user groups

        List<String> userGroups = currentUser.getGroups();


        MultiTenantService multiTenantService = Framework.getService(MultiTenantService.class);

        String tenant = null;
        if (multiTenantService.isTenantIsolationEnabled(session)) {

            tenant =  currentUser.getTenantId();
        }

        if(!isUserAndTenantStatusActive(tenant))
        {
            return availableActions;
        }
        // check if tenant is not disabled



        String ctsUserGroupPrefix = "tenant_" + tenant + "_cts_role_sys_User";
        String departmentGroupPrefix = "tenant_" + tenant + "_cts_role_";
        String communicationManagerPrefix = "tenant_" + tenant + "_cts_role_Communication_Manager";
        String vrDepartmentPrefix = "tenant_" + tenant + "_cts_role_virtual_department";
        String incomingHandlerGroupPrefix = "tenant_" + tenant + "_cts_incoming_handler_";

        Boolean isDepartmentMember =userGroups.stream().filter(str -> !(str.toLowerCase().equals(ctsUserGroupPrefix.toLowerCase()))).
                filter(str -> !(str.toLowerCase().startsWith(vrDepartmentPrefix.toLowerCase())))
                .anyMatch(str -> str.toLowerCase().startsWith(departmentGroupPrefix.toLowerCase()));
        Boolean isIncHandler = userGroups.stream()
                .anyMatch(str -> str.toLowerCase().startsWith(incomingHandlerGroupPrefix.toLowerCase()) );
        Boolean allowedtoSendExternal= false;
        Boolean deptCanCreateOutgoing = Boolean.FALSE;
        Boolean internalIsValid = Boolean.FALSE;
        Boolean workspaceIsValid = Boolean.TRUE;
        if(isDepartmentMember){
            // first check the document type

            // add can create outgoing config in tenant
            try {
                DocumentModel systemConfigurationDoc = getSystemConfiguration(tenant);
                try {
                    if (systemConfigurationDoc.getProperty("sys_config:canCreateOutgoing") != null) {
                        deptCanCreateOutgoing = (Boolean) systemConfigurationDoc.getPropertyValue("sys_config:canCreateOutgoing");
                    }
                } catch (Exception e) {
                    deptCanCreateOutgoing = Boolean.FALSE;
                }
                try {
                    if (systemConfigurationDoc.getProperty("sys_config:internalIsValid") != null) {
                        internalIsValid = (Boolean) systemConfigurationDoc.getPropertyValue("sys_config:internalIsValid");
                    } else {
                        internalIsValid = Boolean.FALSE;
                    }
                } catch (Exception e) {
                    internalIsValid = Boolean.FALSE;
                }
                try {
                    if (systemConfigurationDoc.getProperty("sys_config:workspaceIsValid") != null) {
                        workspaceIsValid = (Boolean) systemConfigurationDoc.getPropertyValue("sys_config:workspaceIsValid");
                    } else {
                        workspaceIsValid = Boolean.TRUE;
                    }
                } catch (Exception e) {
                    workspaceIsValid = Boolean.TRUE;
                }
            } catch (Exception e) {
                deptCanCreateOutgoing = Boolean.FALSE;
                internalIsValid = Boolean.FALSE;
                workspaceIsValid = Boolean.TRUE;
            }
                List<String> userDepartment = userGroups.stream().filter(str -> !(str.toLowerCase().equals(ctsUserGroupPrefix.toLowerCase())))
                    .filter(str -> !(str.toLowerCase().equals(communicationManagerPrefix.toLowerCase())))
                    .filter(str -> str.toLowerCase().startsWith(departmentGroupPrefix.toLowerCase())).map(str -> {
                        String role = str.replace(departmentGroupPrefix, "");
                        role = role.substring(0, role.indexOf("_"));
                        return role;
                    }).distinct().collect(Collectors.toList());

            // we need to check if department allowed to send external if dept can't create outgoing
            if(deptCanCreateOutgoing.equals(Boolean.FALSE)||isIncHandler)
            {
                for(int i=0; i < userDepartment.size();i++)
                {
                    try {
    //                    boolean tx = TransactionHelper.startTransaction();
                        StringBuilder sb = new StringBuilder();
                        sb.append("SELECT * FROM Department WHERE comp:companyCode");
                        sb.append(" = ");
                        sb.append(NXQL.escapeString(tenant));
                        sb.append(" AND ");
                        sb.append("dc:title");
                        sb.append(" = ");
                        sb.append(NXQL.escapeString(userDepartment.get(i)));
                        String q = sb.toString();

                        DocumentModelList list = session.query(q);
      //                  TransactionHelper.commitOrRollbackTransaction();
                        if (!CollectionUtils.isEmpty(list)) {
                            DocumentModel department = list.get(0);
                            Boolean isAllowed = (Boolean) department.getPropertyValue("dept:isAllowRecExternal");
                            if(isAllowed)
                            {
                                allowedtoSendExternal = true;
                                break;

                            }
                        }
                    } catch (QueryParseException qe) {
                        allowedtoSendExternal = false;
                    }
                }
            }


            if(deptCanCreateOutgoing || allowedtoSendExternal)
            {
                availableActions.add(OUTGOING_CORRESPONDENCE);
            }
            if(userDepartment.size()>0)
            {
                if(workspaceIsValid)
                {
                    availableActions.add(WORKSPACE);
                }
                if(internalIsValid)
                {
                    availableActions.add(INTERNAL_CORRESPONDENCE);
                }
            }

        }
        if(isIncHandler && allowedtoSendExternal )
        {
            availableActions.add(INCOMING_CORRESPONDENCE);
        }

       return availableActions;

    }

    private DocumentModel getSystemConfiguration(String tenantId) {
        String parentPath = "/" + tenantId + "/workspaces/CTS/System Configurations";

        DocumentModel config = null;
        DocumentModel configFile = session.createDocumentModel(parentPath, "system configuration", "SystemConfiguration");
        if (session.exists(configFile.getRef())) {
            config = session.getDocument(configFile.getRef());
        } else {
            throw new NuxeoException("System configuration is not found");
        }
        return config;
    }

    private Boolean isUserAndTenantStatusActive(String tenantId) {
        try {
            //        boolean tx = TransactionHelper.startTransaction();
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT * FROM TenantDomain WHERE tendom:code ");
            sb.append(" = ");
            sb.append(NXQL.escapeString(tenantId));
            String q = sb.toString();

            DocumentModelList list = session.query(q);
            //                  TransactionHelper.commitOrRollbackTransaction();
            if (!CollectionUtils.isEmpty(list)) {
                DocumentModel domain = list.get(0);
                Boolean isAllowed = domain.getCurrentLifeCycleState().equals("activate");
                if (!isAllowed) {
                    return Boolean.FALSE;
                }
                sb = new StringBuilder();
                sb.append("SELECT * FROM UserTenantConfig WHERE ");
                sb.append("tenus:user");
                sb.append(" = ");
                sb.append(NXQL.escapeString(session.getPrincipal().getName()));
                q = sb.toString();

                list = session.query(q);

                if (!CollectionUtils.isEmpty(list)) {
                    List<Map<String, Serializable>> tenantlist = (List<Map<String, Serializable>>) list.get(0).getPropertyValue("tenus:tenantList");
                    for (Map<String, Serializable> tenant : tenantlist) {

                        String tenantDomain = tenant.get("tenantId").toString();
                        Integer prefix = tenantDomain.indexOf(":");
                        if(prefix > 0)
                        {
                            tenantDomain = tenantDomain.substring(prefix+1);
                        }

                        if (tenantDomain.equals(domain.getId())) {
                            if (!tenant.get("state").equals("enable")) {
                                return Boolean.FALSE;
                            } else
                                return Boolean.TRUE;

                        }
                    }
                }
            }
//                    boolean tx = TransactionHelper.startTransaction();
            return Boolean.FALSE;
        } catch (QueryParseException qe) {
            return Boolean.FALSE;
        }
    }

}