package sa.comptechco.nuxeo.common.operations.operations;

import org.apache.commons.collections.CollectionUtils;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.multi.tenant.MultiTenantService;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Operation to determine what document types a user can create based on their roles and permissions.
 * 
 * This operation evaluates user permissions for creating different types of correspondence
 * and workspace documents based on:
 * - User's department memberships
 * - Tenant-specific configurations
 * - Department-level permissions
 */
@Operation(id = UserActionsOperation.ID, category = Constants.CAT_DOCUMENT, label = "Get User Allowed Actions", description = "Get User Allowed Actions.")
public class UserActionsOperation {

    public static final String ID = "User.UserActionsOperation";

    // Document type constants
    private static final String INCOMING_CORRESPONDENCE = "IncomingCorrespondence";
    private static final String OUTGOING_CORRESPONDENCE = "OutgoingCorrespondence";
    private static final String INTERNAL_CORRESPONDENCE = "InternalCorrespondence";
    private static final String WORKSPACE = "Workspace";

    // Group prefix constants
    private static final String CTS_USER_GROUP_SUFFIX = "_cts_role_sys_User";
    private static final String DEPARTMENT_GROUP_PREFIX = "_cts_role_";
    private static final String COMMUNICATION_MANAGER_SUFFIX = "_cts_role_Communication_Manager";
    private static final String VIRTUAL_DEPARTMENT_PREFIX = "_cts_role_virtual_department";
    private static final String INCOMING_HANDLER_PREFIX = "_cts_incoming_handler_";

    @Context
    protected CoreSession session;

    @OperationMethod
    public List<String> run() {
        List<String> availableActions = new ArrayList<>();
        NuxeoPrincipal currentUser = session.getPrincipal();
        List<String> userGroups = currentUser.getGroups();
        String tenantId = getTenantId(currentUser);

        if (!isUserAndTenantActive(tenantId)) {
            return availableActions;
        }

        UserPermissions permissions = analyzeUserPermissions(userGroups, tenantId);
        TenantConfiguration tenantConfig = getTenantConfiguration(tenantId);
        
        return determineAvailableActions(permissions, tenantConfig);
    }

    /**
     * Gets the tenant ID for the current user.
     */
    private String getTenantId(NuxeoPrincipal currentUser) {
        MultiTenantService multiTenantService = Framework.getService(MultiTenantService.class);
        return multiTenantService.isTenantIsolationEnabled(session) ? currentUser.getTenantId() : null;
    }

    /**
     * Analyzes user permissions based on their group memberships.
     */
    private UserPermissions analyzeUserPermissions(List<String> userGroups, String tenantId) {
        if (tenantId == null) {
            return new UserPermissions();
        }

        String tenantPrefix = "tenant_" + tenantId;
        String ctsUserGroup = tenantPrefix + CTS_USER_GROUP_SUFFIX;
        String communicationManagerGroup = tenantPrefix + COMMUNICATION_MANAGER_SUFFIX;
        String virtualDepartmentPrefix = tenantPrefix + VIRTUAL_DEPARTMENT_PREFIX;
        String departmentGroupPrefix = tenantPrefix + DEPARTMENT_GROUP_PREFIX;
        String incomingHandlerPrefix = tenantPrefix + INCOMING_HANDLER_PREFIX;

        boolean isDepartmentMember = userGroups.stream()
            .filter(group -> !group.toLowerCase().equals(ctsUserGroup.toLowerCase()))
            .filter(group -> !group.toLowerCase().startsWith(virtualDepartmentPrefix.toLowerCase()))
            .anyMatch(group -> group.toLowerCase().startsWith(departmentGroupPrefix.toLowerCase()));

        boolean isIncomingHandler = userGroups.stream()
            .anyMatch(group -> group.toLowerCase().startsWith(incomingHandlerPrefix.toLowerCase()));

        List<String> userDepartments = extractUserDepartments(userGroups, tenantPrefix, departmentGroupPrefix, 
                                                             ctsUserGroup, communicationManagerGroup);

        return new UserPermissions(isDepartmentMember, isIncomingHandler, userDepartments);
    }

    /**
     * Extracts department names from user groups.
     */
    private List<String> extractUserDepartments(List<String> userGroups, String tenantPrefix, 
                                               String departmentGroupPrefix, String ctsUserGroup, 
                                               String communicationManagerGroup) {
        return userGroups.stream()
            .filter(group -> !group.toLowerCase().equals(ctsUserGroup.toLowerCase()))
            .filter(group -> !group.toLowerCase().equals(communicationManagerGroup.toLowerCase()))
            .filter(group -> group.toLowerCase().startsWith(departmentGroupPrefix.toLowerCase()))
            .map(group -> extractDepartmentName(group, departmentGroupPrefix))
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * Extracts department name from a group string.
     */
    private String extractDepartmentName(String groupName, String departmentGroupPrefix) {
        String role = groupName.replace(departmentGroupPrefix, "");
        int underscoreIndex = role.indexOf("_");
        return underscoreIndex > 0 ? role.substring(0, underscoreIndex) : role;
    }

    /**
     * Gets tenant-specific configuration settings.
     */
    private TenantConfiguration getTenantConfiguration(String tenantId) {
        try {
            DocumentModel systemConfig = getSystemConfiguration(tenantId);
            return new TenantConfiguration(systemConfig);
        } catch (Exception e) {
            return new TenantConfiguration();
        }
    }

    /**
     * Determines available actions based on user permissions and tenant configuration.
     */
    private List<String> determineAvailableActions(UserPermissions permissions, TenantConfiguration config) {
        List<String> actions = new ArrayList<>();

        if (permissions.isDepartmentMember) {
            addDepartmentMemberActions(actions, permissions, config);
        }

        if (permissions.isIncomingHandler) {
            addIncomingHandlerActions(actions, permissions, config);
        }

        return actions;
    }

    /**
     * Adds actions available to department members.
     */
    private void addDepartmentMemberActions(List<String> actions, UserPermissions permissions, 
                                          TenantConfiguration config) {
        boolean canSendExternal = config.canCreateOutgoing || 
                                checkDepartmentExternalPermissions(permissions.userDepartments);

        if (canSendExternal) {
            actions.add(OUTGOING_CORRESPONDENCE);
        }

        if (!permissions.userDepartments.isEmpty()) {
            if (config.workspaceIsValid) {
                actions.add(WORKSPACE);
            }
            if (config.internalIsValid) {
                actions.add(INTERNAL_CORRESPONDENCE);
            }
        }
    }

    /**
     * Adds actions available to incoming handlers.
     */
    private void addIncomingHandlerActions(List<String> actions, UserPermissions permissions, 
                                         TenantConfiguration config) {
        boolean canSendExternal = checkDepartmentExternalPermissions(permissions.userDepartments);
        
        if (canSendExternal) {
            actions.add(INCOMING_CORRESPONDENCE);
        }
    }

    /**
     * Checks if any of the user's departments allow external correspondence.
     */
    private boolean checkDepartmentExternalPermissions(List<String> userDepartments) {
        for (String department : userDepartments) {
            if (isDepartmentAllowedExternalCorrespondence(department)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a specific department allows external correspondence.
     */
    private boolean isDepartmentAllowedExternalCorrespondence(String departmentName) {
        try {
            String tenantId = session.getPrincipal().getTenantId();
            String query = buildDepartmentQuery(tenantId, departmentName);
            DocumentModelList departments = session.query(query);

            if (!CollectionUtils.isEmpty(departments)) {
                DocumentModel department = departments.get(0);
                Boolean isAllowed = (Boolean) department.getPropertyValue("dept:isAllowRecExternal");
                return Boolean.TRUE.equals(isAllowed);
            }
        } catch (QueryParseException e) {
            // Log error and return false
        }
        return false;
    }

    /**
     * Builds NXQL query to find department by tenant and name.
     */
    private String buildDepartmentQuery(String tenantId, String departmentName) {
        StringBuilder query = new StringBuilder();
        query.append("SELECT * FROM Department WHERE comp:companyCode = ")
             .append(NXQL.escapeString(tenantId))
             .append(" AND dc:title = ")
             .append(NXQL.escapeString(departmentName));
        return query.toString();
    }

    /**
     * Gets system configuration document for the tenant.
     */
    private DocumentModel getSystemConfiguration(String tenantId) {
        String configPath = "/" + tenantId + "/workspaces/CTS/System Configurations";
        DocumentModel configFile = session.createDocumentModel(configPath, "system configuration", "SystemConfiguration");
        
        if (!session.exists(configFile.getRef())) {
            throw new NuxeoException("System configuration not found for tenant: " + tenantId);
        }
        
        return session.getDocument(configFile.getRef());
    }

    /**
     * Checks if user and tenant are in active status.
     */
    private boolean isUserAndTenantActive(String tenantId) {
        if (tenantId == null) {
            return true;
        }

        try {
            return isTenantActive(tenantId) && isUserActiveInTenant(tenantId);
        } catch (QueryParseException e) {
            return false;
        }
    }

    /**
     * Checks if the tenant domain is active.
     */
    private boolean isTenantActive(String tenantId) {
        String query = "SELECT * FROM TenantDomain WHERE tendom:code = " + NXQL.escapeString(tenantId);
        DocumentModelList domains = session.query(query);

        if (CollectionUtils.isEmpty(domains)) {
            return false;
        }

        DocumentModel domain = domains.get(0);
        return "activate".equals(domain.getCurrentLifeCycleState());
    }

    /**
     * Checks if the current user is active in the specified tenant.
     */
    private boolean isUserActiveInTenant(String tenantId) {
        String userQuery = "SELECT * FROM UserTenantConfig WHERE tenus:user = " + 
                          NXQL.escapeString(session.getPrincipal().getName());
        DocumentModelList userConfigs = session.query(userQuery);

        if (CollectionUtils.isEmpty(userConfigs)) {
            return false;
        }

        return checkUserTenantStatus(userConfigs.get(0), tenantId);
    }

    /**
     * Checks user's status in the specific tenant.
     */
    @SuppressWarnings("unchecked")
    private boolean checkUserTenantStatus(DocumentModel userConfig, String tenantId) {
        List<Map<String, Serializable>> tenantList = 
            (List<Map<String, Serializable>>) userConfig.getPropertyValue("tenus:tenantList");

        String domainQuery = "SELECT * FROM TenantDomain WHERE tendom:tenantId = " + NXQL.escapeString(tenantId);
        DocumentModelList domains = session.query(domainQuery);

        if (CollectionUtils.isEmpty(domains)) {
            return false;
        }

        String domainId = domains.get(0).getId();
        
        return tenantList.stream()
            .filter(tenant -> extractTenantId(tenant).equals(domainId))
            .anyMatch(tenant -> "enable".equals(tenant.get("state")));
    }

    /**
     * Extracts tenant ID from tenant map, handling prefixed IDs.
     */
    private String extractTenantId(Map<String, Serializable> tenant) {
        String tenantId = tenant.get("tenantId").toString();
        int prefixIndex = tenantId.indexOf(":");
        return prefixIndex > 0 ? tenantId.substring(prefixIndex + 1) : tenantId;
    }

    // Inner classes for better organization

    /**
     * Holds user permission information.
     */
    private static class UserPermissions {
        final boolean isDepartmentMember;
        final boolean isIncomingHandler;
        final List<String> userDepartments;

        UserPermissions() {
            this(false, false, new ArrayList<>());
        }

        UserPermissions(boolean isDepartmentMember, boolean isIncomingHandler, List<String> userDepartments) {
            this.isDepartmentMember = isDepartmentMember;
            this.isIncomingHandler = isIncomingHandler;
            this.userDepartments = userDepartments;
        }
    }

    /**
     * Holds tenant configuration settings.
     */
    private static class TenantConfiguration {
        final boolean canCreateOutgoing;
        final boolean internalIsValid;
        final boolean workspaceIsValid;

        TenantConfiguration() {
            this(false, false, true);
        }

        TenantConfiguration(DocumentModel systemConfig) {
            this.canCreateOutgoing = getBooleanProperty(systemConfig, "sys_config:canCreateOutgoing", false);
            this.internalIsValid = getBooleanProperty(systemConfig, "sys_config:internalIsValid", false);
            this.workspaceIsValid = getBooleanProperty(systemConfig, "sys_config:workspaceIsValid", true);
        }

        TenantConfiguration(boolean canCreateOutgoing, boolean internalIsValid, boolean workspaceIsValid) {
            this.canCreateOutgoing = canCreateOutgoing;
            this.internalIsValid = internalIsValid;
            this.workspaceIsValid = workspaceIsValid;
        }

        private static boolean getBooleanProperty(DocumentModel doc, String propertyName, boolean defaultValue) {
            try {
                Boolean value = (Boolean) doc.getPropertyValue(propertyName);
                return value != null ? value : defaultValue;
            } catch (Exception e) {
                return defaultValue;
            }
        }
    }
}