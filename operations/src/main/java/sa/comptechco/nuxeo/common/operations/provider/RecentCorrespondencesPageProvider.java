package sa.comptechco.nuxeo.common.operations.provider;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.QuickFilter;
import org.nuxeo.ecm.platform.query.api.WhereClauseDefinition;
import org.nuxeo.ecm.platform.query.nxql.NXQLQueryBuilder;
import org.nuxeo.elasticsearch.provider.ElasticSearchNxqlPageProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class RecentCorrespondencesPageProvider extends ElasticSearchNxqlPageProvider {


    private final static String generalClause = "ecm:mixinType != 'HiddenInNavigation' AND ecm:isProxy = 0 AND ecm:isVersion = 0 AND ecm:isTrashed = 0 AND ";

    private static final String PRIMARY_TYPE = "system_primaryType";
    private static final String MIXIN_TYPE = "system_mixinType";
    private static final String LIFE_CYCLE = "system_currentLifeCycleState";
    private static final String TO = "Correspondence_to";
    private static final String FROM = "Correspondence_from";
    private static final String OWNER = "Correspondence_owner";
    private static final String CREATOR = "dublincore_creator";
    private static final String DELEGATION_INFO = "DelegateInfo_delegateInfoList_delegatedUser";
    private static final String Incoming = "IncomingCorrespondence";
    private static final String Outgoing = "OutgoingCorrespondence";
    private static final String Internal = "InternalCorrespondence";
    private static final String SYS_SECRETARY = "cts_role_sys_Secretary";
    private static final String SYS_MANAGER = "cts_role_sys_Manager";
    private static final String SYS_SPECIAL_USERS = "cts_role_sys_specialUsers";
    private static final String DRAFT = "draft";
    private static final String IN_PROGRESS = "inProgress";
    private static final String REGISTERED = "registered";
    private static final String ASSIGNED = "assigned";
    private static final String SENT = "sent";
    private static final String APPROVED = "approved";
    private static final String CLOSED = "closed";
    private static final String DELEGATE_SCHEMA = "DelegateInfo";

    private SortInfo[]  getsortInfo()
    {
        List<SortInfo> sort = null;
        List<QuickFilter> quickFilters = getQuickFilters();
        String quickFiltersClause = "";

        if (quickFilters != null && !quickFilters.isEmpty()) {
            sort = new ArrayList<>();
            for (QuickFilter quickFilter : quickFilters) {
                String clause = quickFilter.getClause();
                if (clause != null) {
                    if (!quickFiltersClause.isEmpty()) {
                        quickFiltersClause = NXQLQueryBuilder.appendClause(quickFiltersClause, clause);
                    } else {
                        quickFiltersClause = clause;
                    }
                }
                sort.addAll(quickFilter.getSortInfos());
            }
        } else if (sortInfos != null) {
            sort = sortInfos;
        }

        SortInfo[] sortArray = null;
        if (sort != null) {
            sortArray = sort.toArray(new SortInfo[] {});
        }
        return sortArray;
    }

    @Override
    protected void buildQuery(CoreSession coreSession) {
        final PageProviderDefinition def = this.getDefinition();
        final WhereClauseDefinition whereClause = def.getWhereClause();
        String newQuery;
        if (whereClause == null) {
            String updatedClause = generalClause;

            if(coreSession.getPrincipal().getTenantId()!=null)
            {
                updatedClause=generalClause + " ecm:path STARTSWITH '/"+coreSession.getPrincipal().getTenantId()+"' And ";
            }
            String originalPattern = def.getPattern();
            String pattern = StringUtils.containsIgnoreCase(originalPattern, " WHERE ")
                    ? NXQLQueryBuilder.appendClause(originalPattern, generalClause + buildIncomingQuery(coreSession) + ")")
                    : originalPattern
                    + " WHERE "
                    + updatedClause
                    + "(" +
                    buildIncomingQuery(coreSession)
                    + " OR "
                    + buildInternalQuery(coreSession)
                    + " OR "
                    + buildOutgoingQuery(coreSession)
                    + ")";

            newQuery = NXQLQueryBuilder.getQuery(pattern, getParameters(), def.getQuotePatternParameters(),
                    def.getEscapePatternParameters(), getSearchDocumentModel(),getsortInfo());
        } else {
            final DocumentModel searchDocumentModel = this.getSearchDocumentModel();
            if (searchDocumentModel == null) {
                throw new NuxeoException(String.format("Cannot build query of provider '%s': no search document model is set", this.getName()));
            }
            newQuery = NXQLQueryBuilder.getQuery(searchDocumentModel, whereClause, this.getParameters(),getsortInfo());
        }
        if (this.query != null && newQuery != null && !newQuery.equals(this.query)) {
            this.refresh();
        }
        this.query = newQuery;
    }

    public String buildIncomingQuery(CoreSession coreSession) {
        NuxeoPrincipal principal = coreSession.getPrincipal();
        List<String> userGroups = principal.getAllGroups();

        StringBuilder incomingQuery = new StringBuilder();
        StringBuilder userStatement = new StringBuilder();
        StringBuilder handlerStatement = new StringBuilder();
        StringBuilder registrarStatement = new StringBuilder();
        StringBuilder senderStatement = new StringBuilder();
        StringBuilder ownerStatement = new StringBuilder();
        StringBuilder defaultStatement = new StringBuilder();

        String primaryType = " ecm:primaryType = 'IncomingCorrespondence' ";

        Boolean isIncHandlerCreator =false;
        Boolean isIncHandlerRegister =false;
        Boolean isIncHandlerSender =false;
        Boolean isIncHandlerOwner =false;
        if(coreSession.getPrincipal().getTenantId()!=null)
        {
            //tenant_{tenant}_cts_incoming_handler_FM
            final String tenantIncomingHandler = "tenant_"+coreSession.getPrincipal().getTenantId()+"_cts_incoming_handler_";
            final String tenantIncomingRegistrar = "tenant_"+coreSession.getPrincipal().getTenantId()+"_cts_incoming_registrar_";
            final String tenantIncomingSender = "tenant_"+coreSession.getPrincipal().getTenantId()+"_cts_incoming_sender_";
            final String tenantIncomingOwner = "tenant_"+coreSession.getPrincipal().getTenantId()+"_cts_incoming_owner_";

            //String tenantIncomingHandler="tenant_"++"_cts_handler_";
            //tenant_@{companyCode}_cts_handler_@{Document.title}_Incoming
            isIncHandlerCreator = userGroups.stream()
                    .anyMatch(str -> str.toLowerCase().startsWith(tenantIncomingHandler.toLowerCase()));
            isIncHandlerRegister = userGroups.stream()
                    .anyMatch(str -> str.toLowerCase().startsWith(tenantIncomingRegistrar.toLowerCase()));
            isIncHandlerSender = userGroups.stream()
                    .anyMatch(str -> str.toLowerCase().startsWith(tenantIncomingSender.toLowerCase()));
            isIncHandlerOwner = userGroups.stream()
                    .anyMatch(str -> str.toLowerCase().startsWith(tenantIncomingOwner.toLowerCase()));
        }
        String deptRegistrarList = extractRoleDepartments(coreSession.getPrincipal().getTenantId(), userGroups, "incoming_registrar");
        String deptSenderList = extractRoleDepartments(coreSession.getPrincipal().getTenantId(), userGroups, "incoming_sender");
        String deptHandlerList = extractRoleDepartments(coreSession.getPrincipal().getTenantId(), userGroups, "incoming_handler");
        String deptOwnerList = extractRoleDepartments(coreSession.getPrincipal().getTenantId(), userGroups, "incoming_owner");


        if (isIncHandlerCreator) {
            handlerStatement.append("(dc:creator='"+principal.getName()+"' AND ecm:currentLifeCycleState IN ('draft'))");
        }
        if (isIncHandlerRegister) {
            registrarStatement.append("(ecm:currentLifeCycleState IN ('readyToRegister','registered') AND corr:to IN "+deptRegistrarList+")");
        }
        if (isIncHandlerSender) {
            senderStatement.append("(ecm:currentLifeCycleState IN ('registered') AND corr:to IN "+deptSenderList+")");
        }
       /* if (isIncHandlerOwner) {
            userStatement.append("(ecm:currentLifeCycleState IN ('draft','registered','inProgress','closed'))");
        }*/
        if (userGroups.stream().anyMatch(s -> s.contains("_Manager"))) {

            /*String stringGroup = userGroups.stream().filter(s -> s.contains("_Manager")).findAny().get().toString();

            if(coreSession.getPrincipal().getTenantId()!=null) {

                stringGroup = stringGroup.replace("tenant_" + coreSession.getPrincipal().getTenantId() + "_cts_role_", "");
            }
            else {
                stringGroup = stringGroup.replace("cts_role_", "");
            }
            stringGroup = stringGroup.replace("_Manager", "");*/
            String deptList = extractManagerDepartments(coreSession.getPrincipal().getTenantId(), userGroups);

            String to = " (corr:to IN " + deptList + ") ";
            String currentLifeCycle = " (ecm:currentLifeCycleState IN ('inProgress') AND corr:owner IS NULL) ";
            userStatement.append(concatenateWithAnd(to, currentLifeCycle));


        }
        if (isIncHandlerCreator) {
            if (userStatement.isEmpty()){
                userStatement.append(handlerStatement.toString());
            }
            else
            {
                userStatement.append(" OR " + handlerStatement.toString());
            }

        }
        if (isIncHandlerRegister) {
            if (userStatement.isEmpty()) {
                userStatement.append(registrarStatement.toString());
            } else {
                if (!registrarStatement.isEmpty()) {
                    userStatement.append(" OR " + registrarStatement.toString());
                }
            }
        }
        if (isIncHandlerSender) {
            if (userStatement.isEmpty()) {
                userStatement.append(senderStatement.toString());
            } else {
                if (!senderStatement.isEmpty()) {
                    userStatement.append(" OR " + senderStatement.toString());
                }
            }
        }
                /*if (isManager) {
                    if (userStatement.isEmpty()) {
                        userStatement.append(managerStatement.toString());
                    } else {
                        if (!managerStatement.isEmpty()) {
                            userStatement.append(" OR " + managerStatement.toString());
                        }
                    }
                }*/
        if (!ownerStatement.isEmpty()) {
            if (userStatement.isEmpty()) {
                userStatement.append(ownerStatement.toString());
            } else {
                userStatement.append(" OR " + ownerStatement.toString());
            }
        }

        {
            String owner = " (corr:owner = '" + principal.getName() + "')";
            String currentLifeCycle = "(ecm:currentLifeCycleState  IN ('inProgress','assigned','closed','redirect'))";
            defaultStatement.append(concatenateWithAnd(owner, currentLifeCycle));

            if(isIncHandlerOwner)
            {


                String ownerDeptList = extractRoleDepartments(coreSession.getPrincipal().getTenantId(), userGroups,"incoming_owner");
                String ownerGroup = " (corr:owner IS NULL AND ecm:currentLifeCycleState='inProgress' AND corr:to IN "+ownerDeptList+")";
                ownerStatement.append(ownerGroup);


                if (!ownerStatement.isEmpty()) {
                    defaultStatement.append(" OR " + ownerStatement.toString());
                }

            }

        }

        String delegationStatement = "(ecm:mixinType = 'DelegateInfo' AND delegInfo:delegateInfoList/*/delegatedUser = '" + principal.getName() + "')";

        if(StringUtils.isEmpty(handlerStatement.toString()))
        {
            if(!StringUtils.isEmpty(userStatement.toString())) {
                incomingQuery.append(concatenateWithAnd(primaryType, concatenateWithOr(defaultStatement.toString(), concatenateWithOr(userStatement.toString(), delegationStatement))));
            }
            else{
                incomingQuery.append(concatenateWithAnd(primaryType, concatenateWithOr(defaultStatement.toString(),  delegationStatement)));
            }

        }
        else {
            incomingQuery.append(concatenateWithAnd(primaryType, concatenateWithOr(defaultStatement.toString(), concatenateWithOr(handlerStatement.toString(), delegationStatement))));
        }

        return incomingQuery.toString();
    }

    public String buildOutgoingQuery(CoreSession coreSession) {
        NuxeoPrincipal principal = coreSession.getPrincipal();
        List<String> userGroups = principal.getAllGroups();

        StringBuilder outgoingQuery = new StringBuilder();
        StringBuilder userStatement = new StringBuilder();
        StringBuilder managerStatement = new StringBuilder();
        StringBuilder ownerStatement = new StringBuilder();
        StringBuilder handlerStatement= new StringBuilder();
        StringBuilder senderStatement= new StringBuilder();
        StringBuilder registrarStatement= new StringBuilder();
        StringBuilder pHandlerStatement= new StringBuilder();


        String primaryType = " ecm:primaryType = 'OutgoingCorrespondence' ";
        Boolean isOutRegistrar=false;
        Boolean isPerOutHandler=false;
        Boolean isOutSender=false;

        String outgoinHandlerGroupsCriteria ="";
        String perHandlerGroupsCriteria = "";
        Boolean isManager= false;

        if(coreSession.getPrincipal().getTenantId()!=null) {
            List<String> outgoinHandlerGroups = new ArrayList<>();
            List<String> perHandlerGroups = new ArrayList<>();

            //final String tenantOutgoingHandler = "tenant_"+coreSession.getPrincipal().getTenantId()+"_cts_handler_";
            //final String tenantPersonalOutgoingHandler = "tenant_"+coreSession.getPrincipal().getTenantId()+"_cts_personalhandler_";

            final String tenantOutgoingHandler = "tenant_" + coreSession.getPrincipal().getTenantId() + "_cts_outgoing_personalhandler_";
            final String tenantOutgoingRegistrar = "tenant_" + coreSession.getPrincipal().getTenantId() + "_cts_outgoing_registrar_";
            final String tenantOutgoingSender = "tenant_" + coreSession.getPrincipal().getTenantId() + "_cts_outgoing_sender_";
            //final String tenantIncomingOwner = "tenant_"+coreSession.getPrincipal().getTenantId()+"_cts_outgoing_approver_";
            //String tenantIncomingHand`ler="tenant_"++"_cts_handler_";
            //tenant_@{companyCode}_cts_handler_@{Document.title}_Incoming
            isOutRegistrar = userGroups.stream()
                    .anyMatch(str -> str.toLowerCase().startsWith(tenantOutgoingRegistrar.toLowerCase()));
            isOutSender = userGroups.stream()
                    .anyMatch(str -> str.toLowerCase().startsWith(tenantOutgoingSender.toLowerCase()));
            isPerOutHandler = userGroups.stream()
                    .anyMatch(str -> str.toLowerCase().startsWith(tenantOutgoingHandler.toLowerCase()));
          /*  outgoinHandlerGroups = userGroups.stream()
                    .filter(str -> str.toLowerCase().startsWith(tenantOutgoingHandler.toLowerCase()) && str.toLowerCase().endsWith("outgoing")).collect(Collectors.toList());

            outgoinHandlerGroups=outgoinHandlerGroups.stream().map(str -> str.replace("tenant_" + coreSession.getPrincipal().getTenantId() + "_cts_handler_", "").replace("_Outgoing","")).collect(Collectors.toList());

            outgoinHandlerGroupsCriteria= StringUtils.join(outgoinHandlerGroups.stream().map(str -> "'".concat(str).concat("'")).collect(Collectors.toList()), ",");

            isPerOutHandler = userGroups.stream()
                    .anyMatch(str -> str.toLowerCase().startsWith(tenantPersonalOutgoingHandler.toLowerCase()));*/



            /*perHandlerGroups = userGroups.stream()
                    .filter(str -> str.toLowerCase().startsWith(tenantPersonalOutgoingHandler.toLowerCase())).collect(Collectors.toList());
            perHandlerGroups=perHandlerGroups.stream().map(str -> str.replace("tenant_" + coreSession.getPrincipal().getTenantId() + "_cts_personalhandler_", "").replace("_Outgoing","")).collect(Collectors.toList());
            perHandlerGroupsCriteria=StringUtils.join(perHandlerGroups.stream().map(str -> "'".concat(str).concat("'")).collect(Collectors.toList()), ",");
*/
            String deptManagerList = extractManagerDepartments(coreSession.getPrincipal().getTenantId(), userGroups);
            String deptRegistrarList = extractRoleDepartments(coreSession.getPrincipal().getTenantId(), userGroups, "outgoing_registrar");
            String deptSenderList = extractRoleDepartments(coreSession.getPrincipal().getTenantId(), userGroups, "outgoing_sender");
            String deptPerList = extractRoleDepartments(coreSession.getPrincipal().getTenantId(), userGroups, "outgoing_personalhandler");


            if (isOutRegistrar) {


                //userStatement.append("(ecm:currentLifeCycleState IN ('approved','registered','sent','closed','under_sign') AND corr:from IN ("+outgoinHandlerGroupsCriteria+"))");
                registrarStatement.append("(ecm:currentLifeCycleState IN ('approved') AND corr:from IN " + deptRegistrarList + ")");

            }

            if (isOutSender) {


                //userStatement.append("(ecm:currentLifeCycleState IN ('approved','registered','sent','closed','under_sign') AND corr:from IN ("+outgoinHandlerGroupsCriteria+"))");
                senderStatement.append("(ecm:currentLifeCycleState IN ('registered','sent','closed') AND corr:from IN " + deptSenderList + ")");

            }

            if (isPerOutHandler) {

                //userStatement.append("(ecm:currentLifeCycleState IN ('draft','assigned','approved','registered','sent','closed','under_sign') and corr:owner= '" + principal.getName() + "') AND corr:from IN ("+perHandlerGroupsCriteria+")");
                pHandlerStatement.append("(ecm:currentLifeCycleState IN ('draft','assigned','approved','registered','sent','closed','under_sign') and corr:owner= '" + principal.getName() + "') AND corr:from IN " + deptPerList + "");

            }
            /*if (userGroups.stream().anyMatch(s -> s.contains("_Manager"))) {

                String stringGroup = userGroups.stream().filter(s -> s.contains("_Manager")).findAny().get().toString();
                if (coreSession.getPrincipal().getTenantId() != null) {

                    stringGroup = stringGroup.replace("tenant_" + coreSession.getPrincipal().getTenantId() + "_cts_role_", "");
                } else {
                    stringGroup = stringGroup.replace("cts_role_", "");
                }
                //stringGroup = stringGroup.replace("cts_role_", "");
                stringGroup = stringGroup.replace("_Manager", "");
                String from = " (corr:from = '" + stringGroup + "') ";
                String currentLifeCycle = (" (ecm:currentLifeCycleState IN ('draft','assigned') ) ");
                //userStatement.append(concatenateWithAnd(from, currentLifeCycle));
                managerStatement.append(concatenateWithAnd(from, currentLifeCycle));
                isManager = true;
            }*/
            {

                String creator = " (dc:creator = '" + principal.getName() + "')";
                String currentLifeCycle = (" (ecm:currentLifeCycleState IN ('draft','assigned') ) ");
                ownerStatement.append(concatenateWithAnd(creator, currentLifeCycle));
                String finalQuery = "";
                if (isOutRegistrar) {
                    userStatement.append(registrarStatement.toString());
                }
                if (isOutSender) {
                    if (userStatement.isEmpty()) {
                        userStatement.append(senderStatement.toString());
                    } else {
                        if (!senderStatement.isEmpty()) {
                            userStatement.append(" OR " + senderStatement.toString());
                        }
                    }
                }
                if (isPerOutHandler) {
                    if (userStatement.isEmpty()) {
                        userStatement.append(pHandlerStatement.toString());
                    } else {
                        if (!pHandlerStatement.isEmpty()) {
                            userStatement.append(" OR " + pHandlerStatement.toString());
                        }
                    }
                }
                /*if (isManager) {
                    if (userStatement.isEmpty()) {
                        userStatement.append(managerStatement.toString());
                    } else {
                        if (!managerStatement.isEmpty()) {
                            userStatement.append(" OR " + managerStatement.toString());
                        }
                    }
                }*/
                if (!ownerStatement.isEmpty()) {
                    if (userStatement.isEmpty()) {
                        userStatement.append(ownerStatement.toString());
                    } else {
                        userStatement.append(" OR " + ownerStatement.toString());
                    }
                }


            /*if(isManager)
            {
                userStatement.append(concatenateWithOr(managerStatement.toString(),concatenateWithAnd(creator, currentLifeCycle)));
            }
            else {
                userStatement.append(concatenateWithAnd(creator, currentLifeCycle));
            }*/
            }
            String delegationStatement = "(ecm:mixinType = 'DelegateInfo' AND delegInfo:delegateInfoList/*/delegatedUser = '" + principal.getName() + "')";


            outgoingQuery.append(concatenateWithAnd(primaryType, concatenateWithOr(userStatement.toString(), delegationStatement)));
        }
        return outgoingQuery.toString();
    }

    public String buildInternalQuery(CoreSession coreSession) {
        NuxeoPrincipal principal = coreSession.getPrincipal();
        List<String> userGroups = principal.getAllGroups();

        StringBuilder internalQuery = new StringBuilder();
        StringBuilder userStatement = new StringBuilder();
        StringBuilder managerStatement = new StringBuilder();

        String primaryType = " ecm:primaryType = 'InternalCorrespondence' ";

        StringBuilder ownerStatement = new StringBuilder();
        StringBuilder handlerStatement= new StringBuilder();
        StringBuilder senderStatement= new StringBuilder();
        StringBuilder registrarStatement= new StringBuilder();
        StringBuilder pHandlerStatement= new StringBuilder();

        if(coreSession.getPrincipal().getTenantId()!=null) {
            final String tenantInternalHandler = "tenant_" + coreSession.getPrincipal().getTenantId() + "_cts_internal_personalhandler_";
            final String tenantInternalRegistrar = "tenant_" + coreSession.getPrincipal().getTenantId() + "_cts_internal_registrar_";
            final String tenantInternalSender = "tenant_" + coreSession.getPrincipal().getTenantId() + "_cts_internal_sender_";
            final String tenantInternalOwner = "tenant_" + coreSession.getPrincipal().getTenantId() + "_cts_internal_owner_";

            Boolean isIntRegistrar = false;
            Boolean isPerIntHandler = false;
            Boolean isIntSender = false;
            Boolean isIntOwner = false;

            isIntRegistrar = userGroups.stream()
                    .anyMatch(str -> str.toLowerCase().startsWith(tenantInternalRegistrar.toLowerCase()));
            isIntSender = userGroups.stream()
                    .anyMatch(str -> str.toLowerCase().startsWith(tenantInternalSender.toLowerCase()));
            isPerIntHandler = userGroups.stream()
                    .anyMatch(str -> str.toLowerCase().startsWith(tenantInternalHandler.toLowerCase()));
            isIntOwner = userGroups.stream()
                    .anyMatch(str -> str.toLowerCase().startsWith(tenantInternalOwner.toLowerCase()));
          /*  outgoinHandlerGroups = userGroups.stream()
                    .filter(str -> str.toLowerCase().startsWith(tenantOutgoingHandler.toLowerCase()) && str.toLowerCase().endsWith("outgoing")).collect(Collectors.toList());

            outgoinHandlerGroups=outgoinHandlerGroups.stream().map(str -> str.replace("tenant_" + coreSession.getPrincipal().getTenantId() + "_cts_handler_", "").replace("_Outgoing","")).collect(Collectors.toList());

            outgoinHandlerGroupsCriteria= StringUtils.join(outgoinHandlerGroups.stream().map(str -> "'".concat(str).concat("'")).collect(Collectors.toList()), ",");

            isPerOutHandler = userGroups.stream()
                    .anyMatch(str -> str.toLowerCase().startsWith(tenantPersonalOutgoingHandler.toLowerCase()));*/



            /*perHandlerGroups = userGroups.stream()
                    .filter(str -> str.toLowerCase().startsWith(tenantPersonalOutgoingHandler.toLowerCase())).collect(Collectors.toList());
            perHandlerGroups=perHandlerGroups.stream().map(str -> str.replace("tenant_" + coreSession.getPrincipal().getTenantId() + "_cts_personalhandler_", "").replace("_Outgoing","")).collect(Collectors.toList());
            perHandlerGroupsCriteria=StringUtils.join(perHandlerGroups.stream().map(str -> "'".concat(str).concat("'")).collect(Collectors.toList()), ",");
*/
            String deptManagerList = extractManagerDepartments(coreSession.getPrincipal().getTenantId(), userGroups);
            String deptRegistrarList = extractRoleDepartments(coreSession.getPrincipal().getTenantId(), userGroups, "internal_registrar");
            String deptSenderList = extractRoleDepartments(coreSession.getPrincipal().getTenantId(), userGroups, "internal_sender");
            String deptPerList = extractRoleDepartments(coreSession.getPrincipal().getTenantId(), userGroups, "internal_personalhandler");


            if (isIntRegistrar) {


                //userStatement.append("(ecm:currentLifeCycleState IN ('approved','registered','sent','closed','under_sign') AND corr:from IN ("+outgoinHandlerGroupsCriteria+"))");
                registrarStatement.append("(ecm:currentLifeCycleState IN ('approved') AND corr:from IN " + deptRegistrarList + ")");

            }

            if (isIntSender) {


                //userStatement.append("(ecm:currentLifeCycleState IN ('approved','registered','sent','closed','under_sign') AND corr:from IN ("+outgoinHandlerGroupsCriteria+"))");
                senderStatement.append("(ecm:currentLifeCycleState IN ('registered','sent','closed') AND corr:from IN " + deptSenderList + ")");

            }

            if (isPerIntHandler) {

                //userStatement.append("(ecm:currentLifeCycleState IN ('draft','assigned','approved','registered','sent','closed','under_sign') and corr:owner= '" + principal.getName() + "') AND corr:from IN ("+perHandlerGroupsCriteria+")");
                pHandlerStatement.append("(ecm:currentLifeCycleState IN ('draft','assigned','approved','registered','sent','closed','under_sign') and corr:owner='" + principal.getName() + "' AND corr:from IN " + deptPerList + ")");

            }

            if (isIntRegistrar) {
                userStatement.append(registrarStatement.toString());
            }
            if (isIntSender) {
                if (userStatement.isEmpty()) {
                    userStatement.append(senderStatement.toString());
                } else {
                    if (!senderStatement.isEmpty()) {
                        userStatement.append(" OR " + senderStatement.toString());
                    }
                }
            }
            if (isPerIntHandler) {
                if (userStatement.isEmpty()) {
                    userStatement.append(pHandlerStatement.toString());
                } else {
                    if (!pHandlerStatement.isEmpty()) {
                        userStatement.append(" OR " + pHandlerStatement.toString());
                    }
                }
            }
                /*if (isManager) {
                    if (userStatement.isEmpty()) {
                        userStatement.append(managerStatement.toString());
                    } else {
                        if (!managerStatement.isEmpty()) {
                            userStatement.append(" OR " + managerStatement.toString());
                        }
                    }
                }*/

            if (isIntOwner) {
                String ownerDeptList = extractRoleDepartments(coreSession.getPrincipal().getTenantId(), userGroups,"internal_owner");
                String ownerGroup = " (corr:owner IS NULL AND ecm:currentLifeCycleState='sent' AND corr:to IN "+ownerDeptList+")";
                ownerStatement.append(ownerGroup);

                if (userStatement.isEmpty()) {
                    userStatement.append(ownerStatement.toString());
                } else {
                    if (!ownerStatement.isEmpty()) {
                        userStatement.append(" OR " + ownerStatement.toString());
                    }
                }
            }
            boolean isManager = false;
            if (userGroups.stream().anyMatch(s -> s.contains("_Manager"))) {

            /*String stringGroup = userGroups.stream().filter(s -> s.contains("_Manager")).findAny().get().toString();
            if(coreSession.getPrincipal().getTenantId()!=null) {

                stringGroup = stringGroup.replace("tenant_" + coreSession.getPrincipal().getTenantId() + "_cts_role_", "");
            }
            else {
                stringGroup = stringGroup.replace("cts_role_", "");
            }
            stringGroup = stringGroup.replace("_Manager", "");*/
                String deptlist = extractManagerDepartments(coreSession.getPrincipal().getTenantId(), userGroups);
                String currentLifeCycle = (" (ecm:currentLifeCycleState IN ('sent') ) ");

                String to = " (corr:owner IS NULL AND corr:to IN " + deptlist + ") ";
                //String from = "";// (corr:from = '" + stringGroup + "') ";
                //userStatement.append(concatenateWithAnd(currentLifeCycle, concatenateWithOr(to, from)));
                managerStatement.append(concatenateWithAnd(currentLifeCycle, to));
                isManager = true;
            }
            {
                String creator = " (dc:creator = '" + principal.getName() + "')";
                String currentLifeCycle = "(ecm:currentLifeCycleState IN ('draft','registered','approved'))";
                String creatorAssignment = "(ecm:currentLifeCycleState ='assigned' AND inter_corr:previousCorrState in ('draft','approved'))";

                String creatorCriteria = concatenateWithOr(concatenateWithAnd(creator, currentLifeCycle), concatenateWithAnd(creator, creatorAssignment));

                String owner = " (corr:owner = '" + principal.getName() + "')";

                String ownerLifeCycle = "(ecm:currentLifeCycleState IN ('sent','closed'))";
                String ownerAssignment = "(ecm:currentLifeCycleState ='assigned' AND inter_corr:previousCorrState in ('sent'))";
                String ownerCriteria = concatenateWithOr(concatenateWithAnd(owner, ownerLifeCycle), concatenateWithAnd(owner, ownerAssignment));
                if (isManager) {
                    if (userStatement.isEmpty()) {
                        userStatement.append(concatenateWithOr(managerStatement.toString(), concatenateWithOr(creatorCriteria, ownerCriteria)));
                    } else {
                        userStatement.append(" OR " + concatenateWithOr(managerStatement.toString(), concatenateWithOr(creatorCriteria, ownerCriteria)));
                    }
                } else {

                    if (userStatement.isEmpty()) {
                        userStatement.append(concatenateWithOr(creatorCriteria, ownerCriteria));
                    } else {
                        userStatement.append(" OR " + concatenateWithOr(creatorCriteria, ownerCriteria));
                    }

                }
            }

            String delegationStatement = "(ecm:mixinType = 'DelegateInfo' AND delegInfo:delegateInfoList/*/delegatedUser = '" + principal.getName() + "')";

            internalQuery.append(concatenateWithAnd(primaryType, concatenateWithOr(userStatement.toString(), delegationStatement)));
        }
        return internalQuery.toString();
    }


    public String concatenateWithAnd(String stStatement, String ndStatement) {
        return "(" + stStatement + " AND " + ndStatement + ")";

    }


    public String concatenateWithOr(String stStatement, String ndStatement) {
        return "(" + stStatement + " OR " + ndStatement + ")";

    }


    public static String extractManagerDepartments(String tenant, List<String> userGroups) {
        List<String> departments = new ArrayList<>();
        String prefix = "tenant_"+tenant+"_cts_role_";
        for (String group : userGroups) {
            if (group.startsWith(prefix) && group.contains("_Manager")) {
                String[] parts = group.split("_");
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].equals("role") && i + 1 < parts.length) {
                        if(!departments.contains(parts[i + 1]))
                        {
                            departments.add(parts[i + 1]);
                        }
                        break;

                    }
                }
            }
        }
        StringJoiner joiner = new StringJoiner("','", "('", "')");
        for (String dept : departments) {
            joiner.add(dept);
        }
        return joiner.toString();
    }

    public static String extractRoleDepartments(String tenant, List<String> userGroups, String role) {
        List<String> departments = new ArrayList<>();
        String prefix = "tenant_"+tenant+"_cts_";
        for (String group : userGroups) {
            if (group.startsWith(prefix) && group.contains(role)) {
                String[] parts = group.replace(role+"_","").split("_");
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].equals("cts") && i + 1 < parts.length) {
                        if(!departments.contains(parts[i + 1]))
                        {
                            departments.add(parts[i + 1]);
                        }
                        break;
                    }
                }
            }
        }
        StringJoiner joiner = new StringJoiner("','", "('", "')");
        for (String dept : departments) {
            joiner.add(dept);
        }
        return joiner.toString();
    }


}
