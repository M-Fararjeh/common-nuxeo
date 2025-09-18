package sa.comptechco.nuxeo.common.operations.operations;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingService;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskService;
import org.nuxeo.runtime.api.Framework;
import sa.comptechco.nuxeo.common.operations.utils.CorrespondenceDataProperties;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
@Operation(id = DocumentActionsOperation.ID, category = Constants.CAT_DOCUMENT, label = "Get Document Allowed Actions", description = "Get Document Allowed Actions.")
public class DocumentActionsOperation {

    public static final String ID = "Document.DocumentActionsOperation";
    private static final String DOCTYPE_ASSIGNMENT = "Assignment";
    private static final String DOCTYPE_WORKSPACE = "CorrWorkspace";
    private static final String DOCTYPE_INCOMING_CORRESPONDENCE = "IncomingCorrespondence";
    private static final String DOCTYPE_OUTGOING_CORRESPONDENCE = "OutgoingCorrespondence";
    private static final String DOCTYPE_INTERNAL_CORRESPONDENCE = "InternalCorrespondence";
    private static final String SCHEMA_COMPANY = "Company";
    private static final String PROPERTY_COMPANY_CODE = "comp:companyCode";
    private static final String AUTOMATION_FETCH_BY_PROPERTY = "Document.FetchByProperty";
    private static final String AUTOMATION_PAGE_PROVIDER = "Repository.PageProvider";
    private static final String eSign_SERVICE_ACTIVE = "esign.egtrust.active";
    private static final String SIGN_PLATFORM_ACTIVE = "sign.platform.active";
    private static final String CREATED_FROM_TEMPLATE_FACET = "CrreatedFromOfficeTemplate";
    private static final String EG_TRUST = "EgTrust" ;

    private DocumentModel systemConfigurationDoc;
    @Context
    protected CoreSession session;

    @Context
    protected TaskService taskService;
    @Context
    OperationContext operationContext;

    @Context
    AutomationService automationService;

    @OperationMethod
    public List<String> run(DocumentModel doc) {
        operationContext = new OperationContext(session);

        automationService = Framework.getService(AutomationService.class);

        List<String> availableActions = new ArrayList<>();

        NuxeoPrincipal currentUser = session.getPrincipal();
        String contribution = Framework.getProperty("comptechco.nuxeo.cts.contrib","default");



        if(!currentUser.getName().equals("HubService") && !isUserAndTenantStatusActive(currentUser.getTenantId()))
        {
            return availableActions;
        }
        //get user groups
        Boolean isDigitalySigned = false;


        List<String> userGroups = currentUser.getGroups();

        //get state of document
        String state = doc.getCurrentLifeCycleState();

        //get creator of document
        String creator = (String) doc.getPropertyValue("dc:creator");

        //get tenant of document
        String tenant = "default";
        if (doc.hasSchema(EG_TRUST))
        {
            try {
                if ((Boolean) doc.getPropertyValue("eg-trust:status")) {
                    isDigitalySigned = true;
                }
            }
            catch (Exception e)
            {
                // do nothing the schema is not found because it is old doc
            }

        }
        if (doc.hasSchema(SCHEMA_COMPANY)) {
            //System.out.println("doc has company schema");
            String companyCode = (String) doc.getPropertyValue(PROPERTY_COMPANY_CODE);
            if (companyCode != null && !(companyCode.isEmpty()))
            {
                tenant = (String) doc.getPropertyValue(PROPERTY_COMPANY_CODE);
                // user can't call document on action in a different tenant so no need for the next check
                /*if(!isUserAndTenantStatusActive(tenant))
                {
                    return availableActions;
                }*/
            }
            else
                tenant = currentUser.getTenantId();
        }

        if ((!tenant.equals("default")) && !tenant.equals(currentUser.getTenantId())) {
            return availableActions;

        }



        //add view action for every document
        availableActions.add("view");
        Boolean isPSignActive = false;

        //String dSignActive= Framework.getProperty(dSign_SERVICE_ACTIVE,"false");
        String dSignActive= Framework.getProperty(SIGN_PLATFORM_ACTIVE,"false"); // digital signature

        String pSigneeGroup = "tenant_" + tenant + "_cts_role_sys_Signee";
        String vipGroup = "tenant_" + tenant + "_cts_role_vip_user";
        if(dSignActive.equals("true") && userGroups.contains(pSigneeGroup))
        {
            isPSignActive = true;
        }

        try {

            //get systemConfiguration document of tenant
            systemConfigurationDoc = GetSystemConfiguration(tenant);
            /////////////////////////////////////Assignment/////////////////////////////
            if (doc.getType().equals(DOCTYPE_ASSIGNMENT)) {
                String correspondenceId = (String) doc.getProperty(CorrespondenceDataProperties.correspondence).getValue();
                DocumentModel correspondence = session.getDocument(new IdRef(correspondenceId));
                DocumentRoutingService documentRoutingService = Framework.getService(DocumentRoutingService.class);
                if (correspondence.hasSchema(EG_TRUST))
                {
                    try {
                        if ((Boolean) doc.getPropertyValue("eg-trust:status")) {
                            isDigitalySigned = true;
                        }
                    }
                    catch (Exception e)
                        {
                            // do nothing the schema is not found because it is old doc
                        }

                }
                //String correspondenceState = correspondence.getCurrentLifeCycleState();
                String corrSecrecyLevel = (String) correspondence.getPropertyValue("corr:secrecyLevel");
                String corrDirection = (String) correspondence.getPropertyValue("corr:direction");
                corrDirection = StringUtils.lowerCase(corrDirection);
                String department = (String) doc.getPropertyValue("assign:department");
                String departmentCode = StringUtils.split(department, "_")[4];
                String assignmentCreator = (String) doc.getPropertyValue("dc:creator");
                String assignmentApprover = (String) doc.getPropertyValue("assign:approver");
                String assignee = (String) doc.getPropertyValue("assign:assignee");
                Boolean canReAssign = (Boolean) doc.getPropertyValue("assign:canReAssign");
                String assignmentReceiverGroup = "tenant_" + tenant + "_cts_" + corrDirection + "_assignments_receiver_" + departmentCode + "_" + corrSecrecyLevel.replace(" ", "_");
                String recommendedParent = (String) doc.getPropertyValue("assign:recommendedParent");
                Boolean isPrivate = (Boolean) doc.getPropertyValue("assign:private");
                String assignmentType = (String) doc.getPropertyValue("assign:typee");
                Boolean hasOpenTasksOfDepartment = HasOpenTask(doc, assignmentReceiverGroup, "department");
                Boolean hasOpenTasksOfIndividual = HasOpenTask(doc, currentUser.getName(), "individual");
                List<Task> assignmentstasks = documentRoutingService.getTasks(doc, "",
                        null, null, session);
                String assignmentActor = "";
                if(!CollectionUtils.isEmpty(assignmentstasks))
                   assignmentActor = assignmentstasks.get(0).getActors().get(0);
                Boolean userhasAssignmentTasks = !CollectionUtils.isEmpty(assignmentstasks) && (currentUser.getName().equals(assignmentActor) || currentUser.getGroups().contains(assignmentActor));
                if (state.equals("inProgress")) {

                    String assignmentState="";
                    if(doc.hasSchema("AssignmentState")) {
                        assignmentState = (String) doc.getPropertyValue("assignState:state");
                    }
                    if (!StringUtils.isEmpty(assignmentType) && assignmentType.equals("redirection") && assignmentState.equals("redirectionSelection") && currentUser.getName().equals(assignee)) {

                        if (correspondence.isLocked() && correspondence.getLockInfo().getOwner().equals(currentUser.getName())) {
                            availableActions.add("unlock");
                        }
                        else
                        {
                            if (!correspondence.isLocked()) {
                                availableActions.add("redirect");
                            }
                        }

                    } else {
                        if (!StringUtils.isEmpty(assignmentType) && assignmentType.equals("signature") && assignmentState.equals("underSign") && currentUser.getName().equals(assignee)) {
                            if (correspondence.isLocked() && correspondence.getLockInfo().getOwner().equals(currentUser.getName())) {
                                availableActions.add("unlock");
                            }
                            else {
                                if (!correspondence.isLocked()) {
                                    if(!isDigitalySigned && !correspondence.getType().equals("IncomingCorrespondence") && !contribution.equals("default"))
                                    {
                                        availableActions.add("pSign");
                                    }
                                    else if(!isDigitalySigned  && contribution.equals("default"))
                                    {
                                        availableActions.add("pSign");
                                    }

                                    if (isPSignActive && correspondence.getPropertyValue("corr:gRegisterDate") != null && correspondence.getType().equals("OutgoingCorrespondence")) {
                                        availableActions.add("dSign");
                                    }
                                }
                            }
                        }

                    else {
                            if (StringUtils.isEmpty(assignmentType) || !(assignmentType.equals("redirection")||(assignmentType.equals("signature")))){
                                Boolean assignToSubDepartment = ConfigIsActive(SystemConfigurationAction.DEPARTMENTS_CAN_ASSIGN_TO_SUB);
                            if (userGroups.contains(assignmentReceiverGroup) && hasOpenTasksOfDepartment && ((StringUtils.isEmpty(recommendedParent) || !(assignmentCreator.equals(assignmentApprover))) || ((!StringUtils.isEmpty(recommendedParent)) && assignToSubDepartment))) {

                        //        if ((!StringUtils.isEmpty(recommendedParent)) && assignToSubDepartment) {
                                    availableActions.add("addSubAssignment");
                          //      }

                                if (assignee.startsWith("tenant_" + tenant + "_cts_" + corrDirection.toLowerCase() + "_assignments_receiver")) {
                                    if (StringUtils.isEmpty(assignmentType) || !(assignmentType.equals("redirection") || (assignmentType.equals("signature")))) {
                                        availableActions.add("assignmentClaim");
                                    }
                                }
                                if (!isPrivate) {
                                    if (correspondence.getType().equals(DOCTYPE_INTERNAL_CORRESPONDENCE)) {
                                        String previousState = (String) correspondence.getPropertyValue("inter_corr:previousCorrState");
                                        if (!previousState.equals(OutgoingCorrespondenceLifeCycleState.SENT.getState())) {
                                            if (correspondence.isLocked() && correspondence.getLockInfo().getOwner().equals(currentUser.getName())) {
                                                availableActions.add("unlock");

                                            } else {
                                                if (!correspondence.isLocked()) {
                                                    if (!isDigitalySigned) {
                                                        availableActions.add("annotate");
                                                    //    availableActions.add("pSign");
                                                    }

                                                    if (isPSignActive && contribution.equals("default")) {
                                                    //    availableActions.add("dSign");
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        if (correspondence.isLocked() && correspondence.getLockInfo().getOwner().equals(currentUser.getName())) {
                                            availableActions.add("unlock");

                                        } else {
                                            if (!correspondence.isLocked() ) {
                                                if (!isDigitalySigned) {
                                                    availableActions.add("annotate");
                                                    if(!correspondence.getType().equals("IncomingCorrespondence") && !contribution.equals("default"))
                                                    {
                                                        //availableActions.add("pSign");
                                                    }
                                                    else if(contribution.equals("default"))
                                                    {
                                                        //availableActions.add("pSign");
                                                    }
                                                }

                                                if (isPSignActive && contribution.equals("default")) {
                                                   // availableActions.add("dSign");
                                                }
                                            }
                                        }
                                    }
                                }
                                availableActions.add("assignmentRequest");
                                if (canReAssign) {
                                    availableActions.add("assignmentReAssign");
                                }
                            }
                        }
                            if (hasOpenTasksOfIndividual && !(StringUtils.isEmpty(recommendedParent)) && assignmentCreator.equals(assignmentApprover)) {
                                // availableActions.add("assignmentRequest");
                                if (StringUtils.isEmpty(assignmentType) || !(assignmentType.equals("redirection")||(assignmentType.equals("signature")))){
                                    if (correspondence.getType().equals(DOCTYPE_INTERNAL_CORRESPONDENCE)) {
                                    String previousState = (String) correspondence.getPropertyValue("inter_corr:previousCorrState");
                                    if (!previousState.equals(OutgoingCorrespondenceLifeCycleState.SENT.getState())) {
                                        if (correspondence.isLocked() && correspondence.getLockInfo().getOwner().equals(currentUser.getName())) {
                                            availableActions.add("unlock");

                                        } else {
                                            if (!correspondence.isLocked()) {
                                                if (!isDigitalySigned) {
                                                    availableActions.add("annotate");
                                                //    availableActions.add("pSign");
                                                }

                                                if (isPSignActive && contribution.equals("default")) {
                                                //    availableActions.add("dSign");
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    if (correspondence.isLocked() && correspondence.getLockInfo().getOwner().equals(currentUser.getName())) {
                                        availableActions.add("unlock");

                                    } else {
                                        if (!correspondence.isLocked()) {
                                            if (!isDigitalySigned ) {
                                               // availableActions.add("annotate");
                                                if(!correspondence.getType().equals("IncomingCorrespondence") && !contribution.equals("default"))
                                                {
                                                    //availableActions.add("pSign");
                                                }
                                                else if(contribution.equals("default"))
                                                {
                                                    //availableActions.add("pSign");
                                                }
                                            }

                                            if (isPSignActive && contribution.equals("default")) {
                                                {
                                                    //availableActions.add("dSign");
                                                }
                                            }
                                        }
                                    }
                                }
                                if (canReAssign) {
                                    availableActions.add("assignmentReAssign");
                                }
                            }
                            }
                            if (currentUser.getName().equals(assignmentApprover)) {

                                if (correspondence.isLocked() && correspondence.getLockInfo().getOwner().equals(currentUser.getName())) {
                                    availableActions.add("unlock");

                                } else {
                                    if (!correspondence.isLocked()) {
                                        if (!StringUtils.isEmpty(assignmentType) && assignmentType.equals("redirection")) {
                                            if (ConfigIsActive(SystemConfigurationAction.VIP_IS_ACTIVE)) {

                                                if (assignmentState.equals("redirectionConfirmation")) {
                                                    availableActions.add("redirectConfirm");
                                                } else {
                                                    if (ConfigIsActive(SystemConfigurationAction.ASSIGNMENT_ABORT))
                                                        availableActions.add("redirectCancel");
                                                }


                                            }
                                        } else if (!StringUtils.isEmpty(assignmentType) && assignmentType.equals("signature")) {
                                            if (ConfigIsActive(SystemConfigurationAction.VIP_IS_ACTIVE)) {

                                                if (assignmentState.equals("signConfirmation")) {
                                                    availableActions.add("signConfirm");
                                                } else {
                                                    availableActions.add("signatureCancel");
                                                }
                                            }
                                        } else {
                                            availableActions.add("assignmentAbort");
                                        }
                                    }
                                }
                                if (!(hasOpenTasksOfIndividual) && !(userhasAssignmentTasks) && (canDoReminder(doc))&& !(assignmentType.equals("redirection")||(assignmentType.equals("signature")))) {
                                    availableActions.add("assignmentReminder");
                                    if (canDoEscalation(doc)) {
                                        System.out.println("escalation ---");
                                        availableActions.add("assignmentEscalate");
                                    }
                                }
                            }
                            if (correspondence.getType().equals(DOCTYPE_INTERNAL_CORRESPONDENCE)) {
                                String corrCreator = (String) correspondence.getPropertyValue("dc:creator");
                                String previousState = (String) correspondence.getPropertyValue("inter_corr:previousCorrState");
                                if (!previousState.equals(OutgoingCorrespondenceLifeCycleState.SENT.getState()) && corrCreator.equals(currentUser.getName())) {
                                    // only if there is recomendation
                                    availableActions.add("assignmentExplore");

                                } else {
                                    String owner = (String) correspondence.getPropertyValue("corr:owner");
                                    if (previousState.equals(OutgoingCorrespondenceLifeCycleState.SENT.getState()) && owner.equals(currentUser.getName())) {
                                        availableActions.add("assignmentExplore");
                                    }
                                }
                            } else {
                                String owner = (String) correspondence.getPropertyValue("corr:owner");
                                if (owner.equals(currentUser.getName())) {
                                    availableActions.add("assignmentExplore");
                                }
                            }

//                    try {
//                        if (HasAssignmentWorkFlow(doc)) {
//                            availableActions.add("addWorkspace");
//                        }
//                    } catch (OperationException e) {
//                        throw new RuntimeException(e);
//                   }
                        }
                    }
                }


            }

            /////////////////////////////////////WorkSpace/////////////////////////////
            else if (doc.getType().equals(DOCTYPE_WORKSPACE)) {
                if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                    availableActions.add("unlock");
                    if (currentUser.getName().equals(creator) && state.equals(WorkSpaceState.NEW.getState())) {
                        String workSpacePath = doc.getPath().toString();
                        if (workSpacePath.startsWith("/" + tenant + "/workspaces/CTS/WorkspacesDrafts/"))
                            availableActions.add("wsCreateInternalCorrespondence");
                       /* if (!(workSpacePath.startsWith("/" + tenant + "/workspaces/CTS/WorkspacesDrafts/"))) {
                            String relatedCorrId = (String) doc.getPropertyValue("cts_common:correspondence");
                            DocumentModel relatedCorr = session.getDocument(new IdRef(relatedCorrId));
                            String relatedCorrDirection = (String) relatedCorr.getPropertyValue("corr:direction");
                            relatedCorrDirection = relatedCorrDirection.toLowerCase();
                            if (relatedCorrDirection.equals("internal"))
                                availableActions.add("wsCreateReply");

                        }*/
                    }

                } else {
                    String workSpacePath = doc.getPath().toString();
                    String[] members = (String[]) doc.getPropertyValue("corrws:members");
                    if (members == null) {
                        members = new String[0];
                    }
                    List<String> membersList = List.of(members);
                    if (currentUser.getName().equals(creator) && state.equals(WorkSpaceState.NEW.getState())) {
                        if (doc.hasFacet(CREATED_FROM_TEMPLATE_FACET) && doc.getPropertyValue("file:content") == null) {
                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals("system")) {
                                availableActions.add("onlyOfficeUpdate");
                            }
                            if (!doc.isLocked()) {
                                availableActions.add("onlyOfficeFinish");
                                availableActions.add("onlyOfficeUpdate");
                            }

                        }
                        if (!doc.isLocked()) {
                            availableActions.add("wsAddMember");
                            availableActions.add("wsRemoveMember");
                            if (doc.getPropertyValue("file:content") != null) {
                                availableActions.add("wsSetFinalDocument");
                                availableActions.add("wsAddAnnotate");
                            }

                            availableActions.add("wsClose");

                            availableActions.add("wsAddAttachment");
                            availableActions.add("wsAddComment");
                            availableActions.add("wsRestoreVersion");

                        }
                        if (workSpacePath.startsWith("/" + tenant + "/workspaces/CTS/WorkspacesDrafts/") && doc.getPropertyValue("file:content") != null)
                            availableActions.add("wsCreateInternalCorrespondence");
                        /*if (!(workSpacePath.startsWith("/" + tenant + "/workspaces/CTS/WorkspacesDrafts/"))) {
                            String relatedCorrId = (String) doc.getPropertyValue("cts_common:correspondence");

                            DocumentModel relatedCorr = session.getDocument(new IdRef(relatedCorrId));
                            String relatedCorrDirection = (String) relatedCorr.getPropertyValue("corr:direction");
                            relatedCorrDirection = relatedCorrDirection.toLowerCase();
                            if (relatedCorrDirection.equals("internal"))
                                availableActions.add("wsCreateReply");

                        }*/


                    } else if (currentUser.getName().equals(creator) && state.equals(WorkSpaceState.CLOSED.getState())) {
                        availableActions.add("wsReOpen");
                        availableActions.add("wsArchive");
                    } else if (membersList.contains(currentUser.getName()) && state.equals(WorkSpaceState.NEW.getState())) {

                        //availableActions.add("unlock");
                        if (doc.hasFacet(CREATED_FROM_TEMPLATE_FACET) && doc.getPropertyValue("file:content") == null) {
                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals("system") || !doc.isLocked()) {
                                availableActions.add("onlyOfficeUpdate");
                            }
                        }
                        if (!doc.isLocked()) {
                            if (doc.getPropertyValue("file:content") != null) {
                                availableActions.add("wsAddAnnotate");

                                availableActions.add("wsAddAttachment");
                                availableActions.add("wsAddComment");
                            }
                        }
                    }
                }
            }


            /////////////////////////////////////Correspondences/////////////////////////////
            else if (doc.hasSchema("Correspondence")) {


//                if(!contribution.equals("default"))
//                {
//                    availableActions.add("printCorrespondence");
//                }
                if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                    availableActions.add("unlock");
                }
                String owner = (String) doc.getPropertyValue("corr:owner");
                String toDepartment = (String) doc.getPropertyValue("corr:to");
                String fromDepartment = (String) doc.getPropertyValue("corr:from");
                String secrecyLevel = (String) doc.getPropertyValue("corr:secrecyLevel");
                GregorianCalendar registerDate= null;
                if(doc.getPropertyValue("corr:gRegisterDate")!= null) {
                    registerDate = (GregorianCalendar)doc.getPropertyValue("corr:gRegisterDate");
                }

                availableActions.add("comment");
                if (ConfigIsActive(SystemConfigurationAction.CORRESPONDENCE_SET_READ_UNREAD)) {
                    if (doc.hasFacet("Viewers")) {
                        List<Map<String, Serializable>> viewers = (List<Map<String, Serializable>>) doc.getPropertyValue("viewers:users");

                        Optional<Map<String, Serializable>> viewer = viewers.stream()
                                .filter(map -> currentUser.getName().equals(map.get("username")))
                                .findFirst();
                        if (viewer.isPresent()) {
                            if (((String) viewer.get().get("state")).equals("read")) {
                                availableActions.add("unread");
                            } else {
                                availableActions.add("read");
                            }
                        } else {
                            availableActions.add("read");
                        }

                    } else {
                        availableActions.add("read");
                    }

                }


                /////////////////////////////////////Incoming Correspondence/////////////////////////////
                if (doc.getType().equals(DOCTYPE_INCOMING_CORRESPONDENCE)) {
                    //tenant_@{companyCode}_cts_incoming_sender_@{departmentCode} _@{secrecyLevel}
                    // manager group tenant_@{companyCode}_cts_role_@{departmentCode}_Manager _@{secrecyLevel}
                    // owner group tenant_@{companyCode}_cts_incoming_owner_@{departmentCode} _@{secrecyLevel})
                    //assignment receiver group tenant_@{companyCode}_cts_incoming_assignments_receiver_@{departmentCode} _@{secrecyLevel}


                    String managerGroup = "tenant_" + tenant + "_cts_role_" + toDepartment + "_Manager_" + secrecyLevel.replace(" ", "_");
                    String ownerGroup = "tenant_" + tenant + "_cts_incoming_owner_" + toDepartment + "_" + secrecyLevel.replace(" ", "_");


                    Boolean isVirtualDept = false;
                    Boolean isCommDeptVirtual = false;
                    String registrarGroup = "";
                    Boolean createByhub= false;
                    Boolean isDeptEmpty = false;
                    if(doc.getProperty("corr_source:creationSource") != null && doc.getPropertyValue("corr_source:creationSource") != null && doc.getPropertyValue("corr_source:creationSource").equals("hub")) {
                        createByhub = true;
                    }
                    if(StringUtils.isEmpty(toDepartment))
                    {
                        if(createByhub) {
                            createByhub = true;
                            String hubReceiverDepartment = (String) doc.getPropertyValue( "hub:receiverDepartmentCode");

                            if (StringUtils.isEmpty(hubReceiverDepartment))
                            {
                                isDeptEmpty=true;
                                String commDepartment = getCommunicationDepartment();

                                if(commDepartment.equals("$VD"))
                                {
                                    isVirtualDept=true;
                                    isCommDeptVirtual = true;
                                    registrarGroup = "tenant_" + tenant + "_cts_role_virtual_department_"+ secrecyLevel.replace(" ", "_");
                                }
                                else
                                {

                                    registrarGroup = "tenant_" + tenant + "_cts_incoming_registrar_"+ commDepartment+"_"+secrecyLevel.replace(" ", "_");
                                    isCommDeptVirtual = false;
                                }

                            }
                        }

                    }
                    else {

                        registrarGroup= "tenant_" + tenant + "_cts_incoming_registrar_" + toDepartment + "_" + secrecyLevel.replace(" ", "_");
                    }

                    String senderGroup = "tenant_" + tenant + "_cts_incoming_sender_" + toDepartment + "_" + secrecyLevel.replace(" ", "_");
                    System.out.println(managerGroup);
                    System.out.println(ownerGroup);
                    System.out.println(registrarGroup);
                    System.out.println(senderGroup);
                    System.out.println(owner);


//            if(secrecyLevel.equals(SecrecyLevel.NORMAL)) {//Amjad
//
//            } else {
//
//            }

                    if (currentUser.getName().equals(creator)) {
                        if (state.equals(IncomingCorrespondenceLifeCycleState.DRAFT.getState())) {
                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                availableActions.add("unlock");

                            } else {
                                availableActions.add("makeReady");
                                availableActions.add("updateIncoming");
                                availableActions.add("deleteIncoming");
                                availableActions.add("uploadNewVersion");
                                if(!isDigitalySigned)
                                {
                                    availableActions.add("annotate");
                                    if(contribution.equals("default"))
                                       availableActions.add("pSign");
                                }


                                if(isPSignActive && contribution.equals("default")){
                                    availableActions.add("dSign");
                                }
                                // availableActions.add("addToFavorite");//Amjad

                            }

                            availableActions.add("addAttachment");
                            availableActions.add("addRelation");
                            // availableActions.add("incomingReply"); //////Amjad
                            availableActions.add("tag");

                        }
                    }

                    if (state.equals(IncomingCorrespondenceLifeCycleState.IN_PROGRESS.getState())
                            && userGroups.contains(managerGroup)
                            && ConfigIsActive(SystemConfigurationAction.CORRESPONDENCE_RESET_OWNER)) {
                        if (!doc.isLocked()) {
                            availableActions.add("reAssign");
                        }

                    }
                    if (state.equals(IncomingCorrespondenceLifeCycleState.IN_PROGRESS.getState()) && StringUtils.isEmpty(owner) && userGroups.contains(ownerGroup)) {
                        //check if able to claim owner group
                        availableActions.add("setOwner");
                    }
                    if (currentUser.getName().equals(owner)) {

                        if (state.equals(IncomingCorrespondenceLifeCycleState.IN_PROGRESS.getState())) {
                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                availableActions.add("unlock");

                            } else {
                                Boolean needReply = (Boolean) doc.getPropertyValue( "corr:requireReply");
                                if(needReply) {
                                    availableActions.add("addCircular");
                                    if (!ConfigIsActive(SystemConfigurationAction.CORRESPONDENCE_CLOSE_WITHOUT_REPLY)) {
                                        Boolean replied = (Boolean) doc.getPropertyValue("corr:replied");
                                        if (replied) {
                                            availableActions.add("closeIncoming");
                                        }
                                    } else {
                                        availableActions.add("closeIncoming");
                                    }
                                }
                                else {
                                    availableActions.add("closeIncoming");
                                    availableActions.add("addCircular");
                                }
                                availableActions.add("return");
                                availableActions.add("addAssignment");
                                if(ConfigIsActive(SystemConfigurationAction.VIP_IS_ACTIVE)) {
                                    availableActions.add("redirectRequest");
                                }
                                // availableActions.add("addToFavorite");//Amjad


                            }
                            // should check if qrcode exist
                            availableActions.add("printQrcode");
                            availableActions.add("addRelation");
                            availableActions.add("tag");
                            if(ConfigIsActive(SystemConfigurationAction.DEPARTMENTS_CAN_CREATE_OUTGOING) ||checkIfUserAllowedToSendExternal(tenant) )
                            {
                                availableActions.add("incomingReply");
                            }


                        } else if (state.equals(IncomingCorrespondenceLifeCycleState.ASSIGHNED.getState())) {
                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                availableActions.add("unlock");

                            } else {
                                availableActions.add("addAssignment");
                                if(ConfigIsActive(SystemConfigurationAction.VIP_IS_ACTIVE)) {
                                    availableActions.add("redirectRequest");
                                }
                                if(!isDigitalySigned)
                                {
                                    availableActions.add("annotate");
                                    if(contribution.equals("default"))
                                       availableActions.add("pSign");
                                }

                                if(isPSignActive && contribution.equals("default")){
                                    availableActions.add("dSign");
                                }
                                //availableActions.add("addToFavorite");//Amjad
                            }
                            availableActions.add("printQrcode");
                            availableActions.add("addRelation");
                            if(ConfigIsActive(SystemConfigurationAction.DEPARTMENTS_CAN_CREATE_OUTGOING) ||checkIfUserAllowedToSendExternal(tenant) )
                            {
                                availableActions.add("incomingReply");
                            }

                            availableActions.add("tag");
                            /*if(hasAssignmentWorkFlowOnCorrespondenced(doc))
                            {
                                availableActions.add("addWorkspace");
                            }*/

                        } else if (state.equals(IncomingCorrespondenceLifeCycleState.CLOSED.getState())) {
                            if (ConfigIsActive(SystemConfigurationAction.CORRESPONDENCE_MANUALARCHIVE))
                                availableActions.add("archiveIncoming");
                            if (ConfigIsActive(SystemConfigurationAction.CORRESPONDENCE_REOPEN))
                                availableActions.add("reOpenIncoming");
                            if(ConfigIsActive(SystemConfigurationAction.DEPARTMENTS_CAN_CREATE_OUTGOING) || checkIfUserAllowedToSendExternal(tenant) )
                            {
                                availableActions.add("incomingReply");

                            }

                            availableActions.add("addRelation");
                            availableActions.add("tag");
                            //availableActions.add("addToFavorite");//Amjad
                            availableActions.add("printQrcode");//Amjad
                        } else if (state.equals(IncomingCorrespondenceLifeCycleState.ARCHIVED.getState())) {

                            if(ConfigIsActive(SystemConfigurationAction.DEPARTMENTS_CAN_CREATE_OUTGOING) ||checkIfUserAllowedToSendExternal(tenant) )
                            {
                                availableActions.add("incomingReply");
                            }

                            availableActions.add("printQrcode");///Amjad
                            // availableActions.add("addToFavorite");//Amjad
                        }


                    }
                    if (state.equals(IncomingCorrespondenceLifeCycleState.ASSIGHNED.getState())) {
                        if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                            availableActions.add("unlock");

                        }
                        if(hasAssignmentWorkFlowOnCorrespondenced(doc)) {
                            if(ConfigIsActive(SystemConfigurationAction.DEPARTMENTS_CAN_CREATE_OUTGOING) ||checkIfUserAllowedToSendExternal(tenant) )
                            {
                                availableActions.add("incomingReply");
                            }


                            if (isWorkspaceIsEnabled()) {
                                availableActions.add("addWorkspace");
                            }
                        }

                    }
//                if (userGroups.contains(managerGroup)) {
//                    if (!doc.isLocked()) {
//                        availableActions.add("reAssign");
//                        //availableActions.add("addToFavorite");//Amjad
//                    }
//
//                }

                    if (state.equals(IncomingCorrespondenceLifeCycleState.READY_TO_REGISTER.getState())) {


                        if (userGroups.contains(registrarGroup)) {
                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                availableActions.add("unlock");

                            } else {
                                if (createByhub) {
                                    if(!isDeptEmpty)
                                    {
                                        availableActions.add("rejectIncoming");
                                        availableActions.add("registerIncoming");

                                        if (!doc.hasFacet("FlashLight")) {
                                            availableActions.add("flashlight");
                                        } else {
                                            availableActions.add("removeFlashlight");
                                        }
                                        if(doc.getProperty("hub:virtualDepartmentReceive") != null && ((Boolean)doc.getPropertyValue("hub:virtualDepartmentReceive").equals(true)))
                                        {
                                            availableActions.add("returnDepartment");
                                        }

                                    }
                                    else
                                    if(!isVirtualDept) {
                                        if(!isCommDeptVirtual)
                                        {
                                            availableActions.add("reassignIncoming");
                                            availableActions.add("rejectIncoming");
                                        }
                                        // not accesible
                                        /*else {
                                            availableActions.add("rejectIncoming");
                                            availableActions.add("registerIncoming");

                                            if (!doc.hasFacet("FlashLight")) {
                                                availableActions.add("flashlight");
                                            } else {
                                                availableActions.add("removeFlashlight");
                                            }
                                        }*/
                                    }
                                     else
                                    {
                                        availableActions.add("reassignIncoming");
                                        availableActions.add("rejectIncoming");

                                    }

                                }
                                else
                                {
                                    availableActions.add("registerIncoming");
                                    //////////Add Drafts Action to ReadyToRegister State WP-189//////////
                                    if(!contribution.equals("default")) {
                                        availableActions.add("updateIncoming");
                                        availableActions.add("deleteIncoming");
                                        availableActions.add("uploadNewVersion");
                                        availableActions.add("addAttachment");
                                        availableActions.add("addRelation");
                                        if (!isDigitalySigned) {
                                            availableActions.add("annotate");
                                        }
                                        if(contribution.equals("default"))
                                           availableActions.add("pSign");
                                        if (isPSignActive && contribution.equals("default")) {
                                            availableActions.add("dSign");
                                        }

                                    }
                                ////////////////End Add Drafts Action to ReadyToRegister State WP-189//////

                                }
                                //availableActions.add("annotate");
                            }
                            //availableActions.add("addToFavorite");//Amjad
                        }
                        if(userGroups.contains(managerGroup)) {
                            //////////
                            if (createByhub) {
                                   if (doc.hasFacet("FlashLight")) {
                                        availableActions.add("removeFlashlight");

                                    }
                                //////
                            }
                        }

                    }
                    if (state.equals(IncomingCorrespondenceLifeCycleState.REGISTERED.getState())) {
                        if (userGroups.contains(registrarGroup) || userGroups.contains(senderGroup)) {
                            if(!contribution.equals("default"))
                            {
                                availableActions.add("reviewTicket");
                            }

                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                availableActions.add("unlock");

                            } else {
                                if (userGroups.contains(senderGroup)) {

                                    availableActions.add("sendIncoming");
                                    if(!isDigitalySigned)
                                    {
                                        availableActions.add("annotate");
                                        if(contribution.equals("default"))
                                           availableActions.add("pSign");
                                    }

                                    if(isPSignActive && contribution.equals("default")){
                                        availableActions.add("dSign");
                                    }
                                }


                                if (!doc.hasFacet("QrCodeSticker")) {
                                    if(ConfigIsActive(SystemConfigurationAction.CORRESPONDENCE_SET_QRCODE))
                                    {
                                        availableActions.add("selectQrcode");
                                    }
                                    else
                                    {
                                        availableActions.add("addQrcode");
                                    }
                                }
                                availableActions.add("printQrcode");

                                //availableActions.add("addToFavorite");//Amjad
                            }

                        }

                    }

                }
                /////////////////////////////////////Outgoing Correspondence/////////////////////////////
                else if (doc.getType().equals(DOCTYPE_OUTGOING_CORRESPONDENCE)) {
                    String outReceiveNoticeDelvired = (String) doc.getPropertyValue("out_corr:status");


                    String outManagerGroup = "tenant_" + tenant + "_cts_role_" + fromDepartment + "_Manager_" + secrecyLevel.replace(" ", "_");
                    String outPersonalGroup = "tenant_" + tenant + "_cts_outgoing_personalhandler_" + fromDepartment + "_" + secrecyLevel.replace(" ", "_");
                    String outRegistrarGroup = "tenant_" + tenant + "_cts_outgoing_registrar_" + fromDepartment + "_" + secrecyLevel.replace(" ", "_");
                    String outSenderGroup = "tenant_" + tenant + "_cts_outgoing_sender_" + fromDepartment + "_" + secrecyLevel.replace(" ", "_");
                    String outAssignmentReceiversGroup = "tenant_" + tenant + "_cts_outgoing_assignments_receive_" + fromDepartment + "_" + secrecyLevel.replace(" ", "_");
                    String legalDepartmentCode = (String) systemConfigurationDoc.getPropertyValue("sys_config:legalDepartment");
                    String legalEmployeeGroup = "tenant_" + tenant + "_cts_role_" + legalDepartmentCode + "_Employee_" + secrecyLevel.replace(" ", "_");
                    String legalManagerGroup = "tenant_" + tenant + "_cts_role_" + legalDepartmentCode + "_Manager_" + secrecyLevel.replace(" ", "_");
                    boolean currentUserIslegal = currentUser.getAllGroups().contains(legalEmployeeGroup) || currentUser.getAllGroups().contains(legalManagerGroup) ? true : false;
                    String ceoDepartmentCode = (String) systemConfigurationDoc.getPropertyValue("sys_config:CEO");
                    String signedFromCEO = (String) doc.getPropertyValue("out_corr:signee");

                    if (currentUser.getName().equals(creator)) {
                        if (state.equals(OutgoingCorrespondenceLifeCycleState.DRAFT.getState())) {
                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                availableActions.add("unlock");

                            } else {
                                // check if created from template
                                if(doc.hasFacet(CREATED_FROM_TEMPLATE_FACET) && !doc.isLocked() && !contribution.equals("default")){
                                    availableActions.add("startReviewOutgoing");
                                }
                                if (doc.hasFacet(CREATED_FROM_TEMPLATE_FACET) && doc.getPropertyValue("file:content") == null) {
                                    /*
                                    if(doc.isLocked() && doc.getLockInfo().getOwner().equals("system")) {
                                        availableActions.add("onlyOfficeUpdate");
                                    }
                                    if(!doc.isLocked()) {
                                        availableActions.add("onlyOfficeFinish");
                                        availableActions.add("onlyOfficeUpdate");
                                    }

                                }
                                if(!doc.isLocked()) {

                                    */
                                    if (doc.isLocked() && doc.getLockInfo().getOwner().equals("system")) {
                                        availableActions.add("onlyOfficeUpdate");
                                    } else {
                                        if (!doc.isLocked()) {
                                            //availableActions.add("onlyOfficeUpdate");
                                            availableActions.add("updateOutgoing");
                                            availableActions.add("onlyOfficeUpdate");
                                            availableActions.add("deleteOutgoing");
                                            if(contribution.equals("default"))
                                               availableActions.add("onlyOfficeFinish");

                                        }
                                    }

                                } else {
                                    if (!doc.isLocked()){
                                       if(contribution.equals("default"))
                                          availableActions.add("startReviewOutgoing");
                                       else if(!doc.hasFacet(CREATED_FROM_TEMPLATE_FACET) && !contribution.equals("default")){
                                           availableActions.add("startReviewOutgoing");
                                       }
                                    if (ConfigIsActive(SystemConfigurationAction.ASSIGNMENT_OUTGOING))
                                        availableActions.add("addAssignment");
                                    availableActions.add("updateOutgoing");
                                    availableActions.add("deleteOutgoing");
                                    availableActions.add("uploadNewVersion");
                                    if (!isDigitalySigned) {
                                        if(contribution.equals("default"))
                                        {
                                            availableActions.add("annotate");
                                            availableActions.add("pSign");
                                        }
                                        else if(!contribution.equals("default") && doc.getPropertyValue("file:content") != null)
                                        {
                                            availableActions.add("annotate");
                                            availableActions.add("pSign");
                                        }

                                    }

                                    if (isPSignActive && contribution.equals("default")) {
                                        availableActions.add("dSign");
                                    }

                                    // add vip request for sign
                                    if (ConfigIsActive(SystemConfigurationAction.VIP_IS_ACTIVE)) {
                                        availableActions.add("signRequest");
                                    }

                                    if (isWorkspaceIsEnabled()) {
                                        availableActions.add("addWorkspace");
                                    }
                                    // availableActions.add("addToFavorite");/////Amjad

                                    // correspondence must follow the approval steps first
                                    /* if (userGroups.contains(outPersonalGroup)) { //|| userGroups.contains(outRegistrarGroup)) {
                                        availableActions.add("registerOutgoing");
                                        availableActions.add("sendWithoutApproval"); // to be approved

                                    }*/
                                }
                            }
                            }

                            availableActions.add("addAttachment");
                            availableActions.add("addRelation");
                            availableActions.add("tag");

                        }
                        if (state.equals(OutgoingCorrespondenceLifeCycleState.ASSIGNED.getState())) {

                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                availableActions.add("unlock");

                            } else {
                                if (registerDate == null) // disallow assignments after register outgoing
                                {
                                    if (ConfigIsActive(SystemConfigurationAction.ASSIGNMENT_OUTGOING)) {
                                        availableActions.add("addAssignment");
                                        // add vip request for sign
                                      /*  if (ConfigIsActive(SystemConfigurationAction.VIP_IS_ACTIVE)) {
                                            availableActions.add("signRequest");
                                        }*/
                                    }
                                if (!doc.isLocked()) {
                                    if (!isDigitalySigned) {
                                        if(contribution.equals("default"))
                                        {
                                            availableActions.add("annotate");
                                            availableActions.add("pSign");
                                        }
                                        else if(!contribution.equals("default") && doc.getPropertyValue("file:content") != null)
                                        {
                                            availableActions.add("annotate");
                                            availableActions.add("pSign");
                                        }
                                    }

                                    //disabling digital sign if not  register because qrcode will invalidate it
                                    /*if (isPSignActive) {
                                        availableActions.add("dSign");
                                    }*/
                                }
                            }
                            }
                            if (registerDate == null && isWorkspaceIsEnabled() && hasAssignmentWorkFlowOnCorrespondenced(doc)) {
                                availableActions.add("addWorkspace");
                            }
                            availableActions.add("addRelation");
                            availableActions.add("tag");
                            //availableActions.add("addToFavorite");/////Amjad
                        }
                        if (state.equals(OutgoingCorrespondenceLifeCycleState.UNDER_REVISION.getState())) {
                            //String corrSecrecyLevel = (String) doc.getPropertyValue("corr:secrecyLevel");
                            //String departmentCode = (String) doc.getPropertyValue("corr:from");
                            //String approverGroup = "tenant_" + session.getPrincipal().getTenantId() + "_cts_outgoing_approver_" + departmentCode + "_" + corrSecrecyLevel.replace(" ", "_");
                            DocumentRoutingService documentRoutingService = Framework.getService(DocumentRoutingService.class);
                            List<Task> tasks = documentRoutingService.getTasks(doc, "",
                                    null, null, session);
                            if (!CollectionUtils.isEmpty(tasks)) {
                                String actor = tasks.get(0).getActors().get(0);
                                if (currentUser.getName().equals(actor) || currentUser.getGroups().contains(actor)) {
                                    if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                        availableActions.add("unlock");

                                    } else {
                                        if (currentUser.getName().equals(creator) && tasks.get(0).getType().equals("WF_OutgoingCorrespondence_Review-20210617160931550-approve-task")&&doc.hasFacet(CREATED_FROM_TEMPLATE_FACET)) {
                                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals("system")) {
                                                availableActions.add("onlyOfficeUpdate");
                                                if(contribution.equals("default"))
                                                  availableActions.add("onlyOfficePreview");
                                                else if(!contribution.equals("default") && doc.getPropertyValue("file:content") != null)
                                                    availableActions.add("onlyOfficePreview");
                                            }
                                            else if(!doc.isLocked()) {
                                                availableActions.add("updateOutgoing");
                                                availableActions.add("onlyOfficeUpdate");
                                                if(contribution.equals("default"))
                                                    availableActions.add("onlyOfficePreview");
                                                else if(!contribution.equals("default") && doc.getPropertyValue("file:content") != null)
                                                    availableActions.add("onlyOfficePreview");
                                                if(contribution.equals("default"))
                                                  availableActions.add("onlyOfficeFinish");

                                            }
                                        }
                                        if (!doc.isLocked()) {
                                            if (!isDigitalySigned) {
                                                if(contribution.equals("default"))
                                                {
                                                    availableActions.add("annotate");
                                                    availableActions.add("pSign");
                                                }
                                                else if(!contribution.equals("default") && doc.getPropertyValue("file:content") != null)
                                                {
                                                    availableActions.add("annotate");
                                                    availableActions.add("pSign");
                                                }
                                            }

                                            if (isPSignActive && contribution.equals("default")) {
                                                availableActions.add("dSign");
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (canDoReminder(doc)) {
                                    availableActions.add("reviewReminder");
                                }
                            }
                        }
                    } else {
                        if (state.equals(OutgoingCorrespondenceLifeCycleState.ASSIGNED.getState())) {
                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                availableActions.add("unlock");

                            }
                            if (isWorkspaceIsEnabled() && hasAssignmentWorkFlowOnCorrespondenced(doc)) {
                                availableActions.add("addWorkspace");
                            }
                        }

                        if (state.equals(OutgoingCorrespondenceLifeCycleState.UNDER_REVISION.getState())) {

                            String corrSecrecyLevel = (String) doc.getPropertyValue("corr:secrecyLevel");
                            String departmentCode = (String) doc.getPropertyValue("corr:from");
                            //String approverGroup = "tenant_" + session.getPrincipal().getTenantId() + "_cts_outgoing_approver_" + departmentCode + "_" + corrSecrecyLevel.replace(" ", "_");
                            DocumentRoutingService documentRoutingService = Framework.getService(DocumentRoutingService.class);
                            List<Task> tasks = documentRoutingService.getTasks(doc, "",
                                    null, null, session);
                            if (!CollectionUtils.isEmpty(tasks)) {
                                String actor = tasks.get(0).getActors().get(0);
                                DocumentModel wfDocument= GetWorkFlowDocument(tasks.get(0));
                                String reviewCurrentDepartment = (String)wfDocument.getPropertyValue("var_WF_OutgoingCorrespondence_Review:currentApprovalDepartment");
                                if (currentUser.getName().equals(actor) || currentUser.getGroups().contains(actor)) {
                                    if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                        availableActions.add("unlock");

                                    } else {
                                       /* if (currentUser.getName().equals(creator) && tasks.get(0).getType().equals("WF_OutgoingCorrespondence_Review-20210617160931550-approve-task")&&doc.hasFacet(CREATED_FROM_TEMPLATE_FACET)) {
                                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals("system")) {
                                                availableActions.add("onlyOfficeUpdate");
                                                availableActions.add("onlyOfficePreview");
                                            }
                                            availableActions.add("onlyOfficeUpdate");
                                            availableActions.add("onlyOfficeFinish");
                                            availableActions.add("onlyOfficePreview");
                                        }*/
                                        ///////////////////////Legal Review Actions For Create From template WP-186/////////////
                                        if(!contribution.equals("default")
                                              &&reviewCurrentDepartment.equals(legalDepartmentCode)
                                                && currentUserIslegal
                                                && tasks.get(0).getType().equals("WF_OutgoingCorrespondence_Review-20210617154521372-accept-reject-task")
                                                && doc.hasFacet(CREATED_FROM_TEMPLATE_FACET)){
                                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals("system")) {
                                                availableActions.add("onlyOfficeUpdate");
                                                if(doc.getPropertyValue("file:content") != null)
                                                   availableActions.add("onlyOfficePreview");
                                            }
                                            else if(!doc.isLocked()) {
                                                if(signedFromCEO.equals(ceoDepartmentCode) && tasks.get(0).getActors().get(0).equals(legalManagerGroup))
                                                    availableActions.add("reAssignApprovalTask");
                                                if(signedFromCEO.equals(ceoDepartmentCode) && !tasks.get(0).getActors().get(0).equals(legalManagerGroup))
                                                    availableActions.add("returnApprovalTask");
                                                // availableActions.add("updateOutgoing");
                                                availableActions.add("onlyOfficeUpdate");
                                                availableActions.add("onlyOfficeFinish");
                                                if(doc.getPropertyValue("file:content") != null)
                                                   availableActions.add("onlyOfficePreview");
                                            }
                                        }
                                        /////////////////////End Legal Review Actions//////////
                                        if (!doc.isLocked()) {
                                            if(!contribution.equals("default"))
                                            {
                                                if(!(doc.getPropertyValue("file:content") != null)  && doc.hasFacet(CREATED_FROM_TEMPLATE_FACET)) {
                                                    availableActions.add("onlyOfficeUpdate");
                                                }

                                            }
                                            if(!contribution.equals("default") && doc.getPropertyValue("file:content") != null)
                                            {
                                                if(!(doc.getPropertyValue("file:content") != null) && doc.hasFacet(CREATED_FROM_TEMPLATE_FACET))
                                                {
                                                    availableActions.add("onlyOfficePreview");
                                                }
                                            }
                                            if (!isDigitalySigned) {
                                                if(contribution.equals("default"))
                                                {
                                                    availableActions.add("annotate");
                                                    availableActions.add("pSign");
                                                }
                                                else if(!contribution.equals("default") && doc.getPropertyValue("file:content") != null)
                                                {
                                                    availableActions.add("annotate");
                                                    availableActions.add("pSign");
                                                }
                                            }

                                            if (isPSignActive && contribution.equals("default")) {
                                                availableActions.add("dSign");
                                            }
                                        }
                                    }
                                }
                            }


                        }
                    }

                    if (state.equals(OutgoingCorrespondenceLifeCycleState.APPROVED.getState())) {
                        if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                            availableActions.add("unlock");

                        } else {
                            Boolean isAllowedToSend = checkIfDepartmentAllowedToSendExternal(fromDepartment, tenant, session);
                            if(!isAllowedToSend)
                            {
                                Boolean registerByOtherDepartment = ConfigIsActive(SystemConfigurationAction.DEPARTMENTS_CAN_CREATE_OUTGOING);
                                if(registerByOtherDepartment) {
                                    String externalDepartment = null;
                                    Boolean externalSelectedByUser =ConfigIsActive(SystemConfigurationAction.OUTGOING_USER_SELECT_EXTERNAL) || !contribution.equals("default");
                                   if(externalSelectedByUser)
                                   {
                                       externalDepartment = (String)doc.getPropertyValue("out_corr:externalDepartment");

                                   }
                                   else{
                                        if (secrecyLevel.equals(SecrecyLevel.SECRET.getSecrecy()) || secrecyLevel.equals(SecrecyLevel.TOP_SECRET.getSecrecy())) {
                                            externalDepartment = (String) systemConfigurationDoc.getPropertyValue("sys_config:externalDepartment/normal");
                                        } else {
                                            externalDepartment = (String) systemConfigurationDoc.getPropertyValue("sys_config:externalDepartment/secret");
                                        }
                                    }
                                    Boolean isExternalDeptAllowedToSendExternal = checkIfDepartmentAllowedToSendExternal(externalDepartment, tenant, session);

                                   if(isExternalDeptAllowedToSendExternal) {
                                       String outExternalRegistrarGroup = "tenant_" + tenant + "_cts_outgoing_registrar_" + externalDepartment + "_" + secrecyLevel.replace(" ", "_");
                                       if (userGroups.contains(outExternalRegistrarGroup)) {
                                           if (!doc.isLocked()) {
                                               availableActions.add("registerOutgoing");
                                           }
                                       }
                                   }
                                }
                            }
                            if ((isAllowedToSend && userGroups.contains(outRegistrarGroup)) || (userGroups.contains(outPersonalGroup) && currentUser.getName().equals(creator))) {
                                if (!doc.isLocked()) {
                                    availableActions.add("registerOutgoing");
                                }
                                //availableActions.add("annotate");
                                // availableActions.add("addToFavorite");//Amjad
                            }
                        }
                    }
                    if (state.equals(OutgoingCorrespondenceLifeCycleState.REGISTERED.getState())) {
                        if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                            availableActions.add("unlock");

                        } else {

                            Boolean isAllowedToSend = checkIfDepartmentAllowedToSendExternal(fromDepartment, tenant, session);
                            if (!isAllowedToSend) {
                                Boolean registerByOtherDepartment = ConfigIsActive(SystemConfigurationAction.DEPARTMENTS_CAN_CREATE_OUTGOING);
                                if (registerByOtherDepartment) {
                                    String externalDepartment = null;
                                    Boolean externalSelectedByUser = ConfigIsActive(SystemConfigurationAction.OUTGOING_USER_SELECT_EXTERNAL) || !contribution.equals("default");
                                    if (externalSelectedByUser) {
                                        externalDepartment = (String) doc.getPropertyValue("out_corr:externalDepartment");

                                    } else {
                                        if (secrecyLevel.equals(SecrecyLevel.SECRET.getSecrecy()) || secrecyLevel.equals(SecrecyLevel.TOP_SECRET.getSecrecy())) {
                                            externalDepartment = (String) systemConfigurationDoc.getPropertyValue("sys_config:externalDepartment/normal");
                                        } else {
                                            externalDepartment = (String) systemConfigurationDoc.getPropertyValue("sys_config:externalDepartment/secret");
                                        }
                                    }
                                    Boolean isExternalDeptAllowedToSendExternal = checkIfDepartmentAllowedToSendExternal(externalDepartment, tenant, session);

                                    if (isExternalDeptAllowedToSendExternal) {
                                        String outExternalSenderGroup = "tenant_" + tenant + "_cts_outgoing_sender_" + externalDepartment + "_" + secrecyLevel.replace(" ", "_");
                                        if (userGroups.contains(outExternalSenderGroup)) {
                                            if (!doc.isLocked()) {
                                                if (!doc.hasFacet("QrCodeSticker")) {
                                                    if (ConfigIsActive(SystemConfigurationAction.CORRESPONDENCE_SET_QRCODE)) {
                                                        if (!doc.isLocked()) {
                                                            availableActions.add("selectQrcode");
                                                        }
                                                    } else {
                                                        if (!doc.isLocked()) {
                                                            availableActions.add("addQrcode");
                                                        }
                                                    }
                                                } else {
                                                    availableActions.add("printQrcode");
                                                }
                                                if (ConfigIsActive(SystemConfigurationAction.OUTGOING_SEND_BY_HUB)) {

                                                    String outgoingToAgency = (String) doc.getPropertyValue("corr:toAgency");
                                                    if (!StringUtils.isEmpty(outgoingToAgency)) {
                                                        // check to agency subscription
                                                        if (ConfigIsActiveTenant(SystemConfigurationAction.OUTGOING_SEND_BY_HUB, outgoingToAgency)) {
                                                            if (!doc.isLocked()) {
                                                                availableActions.add("sendOutgoingByHub");
                                                            }
                                                        } else {
                                                            if (!doc.isLocked()) {
                                                                availableActions.add("sendOutgoing");

                                                            }
                                                        }
                                                    } else {
                                                        ArrayList outgoingToRecievers = (ArrayList) doc.getPropertyValue("out_corr:multiRecivers");
                                                        if (!CollectionUtils.isEmpty(outgoingToRecievers)) {
                                                            Boolean allActive = false;
                                                            for (int i = 0; i < outgoingToRecievers.size(); i++) {
                                                                if (ConfigIsActiveTenant(SystemConfigurationAction.OUTGOING_SEND_BY_HUB, (String) ((HashMap) outgoingToRecievers.get(i)).get("toAgency"))) {
                                                                    allActive = true;
                                                                } else {
                                                                    allActive = false;
                                                                    break;
                                                                }

                                                            }
                                                            // check to agencies subscription

                                                            if (allActive) {
                                                                if (!doc.isLocked()) {
                                                                    availableActions.add("sendOutgoingByHub");
                                                                }

                                                            } else {
                                                                if (!doc.isLocked()) {
                                                                    availableActions.add("sendOutgoing");
                                                                }
                                                            }
                                                        } else {
                                                            if (!doc.isLocked()) {
                                                                availableActions.add("sendOutgoing");
                                                            }
                                                        }
                                                    }

                                                    //availableActions.add("sendOutgoing");
                                                } else {
                                                    if (!doc.isLocked()) {
                                                        availableActions.add("sendOutgoing");
                                                        if (!contribution.equals("default")) {
                                                            availableActions.add("printCorrespondence");
                                                        }
                                                    }
                                                }
                                                availableActions.add("signRequest");
                                            }
                                        }
                                    }
                                }
                            }
                            if ((isAllowedToSend && userGroups.contains(outSenderGroup)) || (userGroups.contains(outPersonalGroup) && currentUser.getName().equals(creator))) {
                                if (!doc.isLocked()) {
                                    if (!doc.hasFacet("QrCodeSticker")) {
                                        if (ConfigIsActive(SystemConfigurationAction.CORRESPONDENCE_SET_QRCODE)) {
                                            if (!doc.isLocked()) {
                                                availableActions.add("selectQrcode");
                                            }
                                        } else {
                                            if (!doc.isLocked()) {
                                                availableActions.add("addQrcode");
                                            }
                                        }
                                    } else {
                                        availableActions.add("printQrcode");
                                    }
                                    if (ConfigIsActive(SystemConfigurationAction.OUTGOING_SEND_BY_HUB)) {

                                        String outgoingToAgency = (String) doc.getPropertyValue("corr:toAgency");
                                        if (!StringUtils.isEmpty(outgoingToAgency)) {
                                            // check to agency subscription
                                            if (ConfigIsActiveTenant(SystemConfigurationAction.OUTGOING_SEND_BY_HUB, outgoingToAgency)) {
                                                if (!doc.isLocked()) {
                                                    availableActions.add("sendOutgoingByHub");
                                                }
                                            } else {
                                                if (!doc.isLocked()) {
                                                    availableActions.add("sendOutgoing");

                                                }
                                            }
                                        } else {
                                            ArrayList outgoingToRecievers = (ArrayList) doc.getPropertyValue("out_corr:multiRecivers");
                                            if (!CollectionUtils.isEmpty(outgoingToRecievers)) {
                                                Boolean allActive = false;
                                                for (int i = 0; i < outgoingToRecievers.size(); i++) {
                                                    if (ConfigIsActiveTenant(SystemConfigurationAction.OUTGOING_SEND_BY_HUB, (String) ((HashMap) outgoingToRecievers.get(i)).get("toAgency"))) {
                                                        allActive = true;
                                                    } else {
                                                        allActive = false;
                                                        break;
                                                    }

                                                }
                                                // check to agencies subscription

                                                if (allActive) {
                                                    if (!doc.isLocked()) {
                                                        availableActions.add("sendOutgoingByHub");
                                                    }

                                                } else {
                                                    if (!doc.isLocked()) {
                                                        availableActions.add("sendOutgoing");
                                                    }
                                                }
                                            } else {
                                                if (!doc.isLocked()) {
                                                    availableActions.add("sendOutgoing");
                                                }
                                            }
                                        }

                                        //availableActions.add("sendOutgoing");
                                    } else {
                                        if (!doc.isLocked()) {
                                            availableActions.add("sendOutgoing");
                                            if (!contribution.equals("default")) {
                                                availableActions.add("printCorrespondence");
                                            }
                                        }
                                    }
                                    if (ConfigIsActive(SystemConfigurationAction.VIP_IS_ACTIVE)) {
                                        availableActions.add("signRequest");
                                    }
                                }
                                //availableActions.add("annotate");
                                // availableActions.add("addToFavorite");//Amjad
                            }
                             /*   if (userGroups.contains(outSenderGroup) || (userGroups.contains(outPersonalGroup) && currentUser.getName().equals(creator))) {
                                    //Boolean isAllowedToSend = checkIfDepartmentAllowedToSendExternal(fromDepartment, tenant, session);
                                    if (!doc.isLocked()) {
                                        if(ConfigIsActive(SystemConfigurationAction.VIP_IS_ACTIVE))
                                        {
                                            availableActions.add("signRequest");
                                        }
                                    }
                                    if (isAllowedToSend) {
                                        if (ConfigIsActive(SystemConfigurationAction.OUTGOING_SEND_BY_HUB)) {

                                            String outgoingToAgency = (String) doc.getPropertyValue("corr:toAgency");
                                            if (!StringUtils.isEmpty(outgoingToAgency)) {
                                                // check to agency subscription
                                                if (ConfigIsActiveTenant(SystemConfigurationAction.OUTGOING_SEND_BY_HUB, outgoingToAgency)) {
                                                    if (!doc.isLocked()) {
                                                        availableActions.add("sendOutgoingByHub");
                                                    }
                                                } else {
                                                    if (!doc.isLocked()) {
                                                        availableActions.add("sendOutgoing");

                                                    }
                                                }
                                            } else {
                                                ArrayList outgoingToRecievers = (ArrayList) doc.getPropertyValue("out_corr:multiRecivers");
                                                if (!CollectionUtils.isEmpty(outgoingToRecievers)) {
                                                    Boolean allActive = false;
                                                    for (int i = 0; i < outgoingToRecievers.size(); i++) {
                                                        if (ConfigIsActiveTenant(SystemConfigurationAction.OUTGOING_SEND_BY_HUB, (String) ((HashMap) outgoingToRecievers.get(i)).get("toAgency"))) {
                                                            allActive = true;
                                                        } else {
                                                            allActive = false;
                                                            break;
                                                        }

                                                    }
                                                    // check to agencies subscription

                                                    if (allActive) {
                                                        if (!doc.isLocked()) {
                                                            availableActions.add("sendOutgoingByHub");
                                                        }

                                                    } else {
                                                        if (!doc.isLocked()) {
                                                            availableActions.add("sendOutgoing");
                                                        }
                                                    }
                                                } else {
                                                    if (!doc.isLocked()) {
                                                        availableActions.add("sendOutgoing");
                                                    }
                                                }
                                            }

                                            //availableActions.add("sendOutgoing");
                                        } else {
                                            if (!doc.isLocked()) {
                                                availableActions.add("sendOutgoing");
                                                if(!contribution.equals("default"))
                                                {
                                                    availableActions.add("printCorrespondence");
                                                }
                                            }
                                        }
                                    }
                                    }*/
                            //availableActions.add("annotate");

                            // availableActions.add("addToFavorite");//Amjad

                        }


                    }
                    if (state.equals(OutgoingCorrespondenceLifeCycleState.SENT.getState())) {
                        Boolean isAllowedToSend = checkIfDepartmentAllowedToSendExternal(fromDepartment, tenant, session);
                        if (!isAllowedToSend) {

                            Boolean externalSelectedByUser = ConfigIsActive(SystemConfigurationAction.OUTGOING_USER_SELECT_EXTERNAL) || !contribution.equals("default");
                            String externalDepartment = (String) doc.getPropertyValue("corr:from");
                            if (externalSelectedByUser) {
                                externalDepartment = (String) doc.getPropertyValue("out_corr:externalDepartment");

                            } else {
                                if (secrecyLevel.equals(SecrecyLevel.SECRET.getSecrecy()) || secrecyLevel.equals(SecrecyLevel.TOP_SECRET.getSecrecy())) {
                                    externalDepartment = (String) systemConfigurationDoc.getPropertyValue("sys_config:externalDepartment/normal");
                                } else {
                                    externalDepartment = (String) systemConfigurationDoc.getPropertyValue("sys_config:externalDepartment/secret");
                                }
                            }
                            Boolean isExternalDeptAllowedToSendExternal = checkIfDepartmentAllowedToSendExternal(externalDepartment, tenant, session);

                            if (isExternalDeptAllowedToSendExternal) {
                                outSenderGroup = "tenant_" + tenant + "_cts_outgoing_sender_" + externalDepartment + "_" + secrecyLevel.replace(" ", "_");

                            }
                        }
                        {
                                if (userGroups.contains(outSenderGroup) || (userGroups.contains(outPersonalGroup) && currentUser.getName().equals(creator))) {

                            availableActions.add("printQrcode");
                            //availableActions.add("addToFavorite");//Amjad

                            if(!doc.hasFacet("HubCorrespondence")) {
                                if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                    availableActions.add("unlock");

                                }
                                if (!doc.isLocked()) {
                                    availableActions.add("closeOutgoing");
                                    if (!outReceiveNoticeDelvired.equals("Delivered")) {
                                        availableActions.add("addReceiveNotice");
                                        availableActions.add("uploadReceiveNotice");
                                    }
                                }
                            }
                        }
                        }
                    }
                    if (state.equals(OutgoingCorrespondenceLifeCycleState.CLOSED.getState())) {
                        Boolean isAllowedToSend = checkIfDepartmentAllowedToSendExternal(fromDepartment, tenant, session);
                        if (!isAllowedToSend) {

                            Boolean externalSelectedByUser = ConfigIsActive(SystemConfigurationAction.OUTGOING_USER_SELECT_EXTERNAL) || !contribution.equals("default");
                            String externalDepartment = (String) doc.getPropertyValue("corr:from");
                            if (externalSelectedByUser) {
                                externalDepartment = (String) doc.getPropertyValue("out_corr:externalDepartment");

                            } else {
                                if (secrecyLevel.equals(SecrecyLevel.SECRET.getSecrecy()) || secrecyLevel.equals(SecrecyLevel.TOP_SECRET.getSecrecy())) {
                                    externalDepartment = (String) systemConfigurationDoc.getPropertyValue("sys_config:externalDepartment/normal");
                                } else {
                                    externalDepartment = (String) systemConfigurationDoc.getPropertyValue("sys_config:externalDepartment/secret");
                                }
                            }
                            Boolean isExternalDeptAllowedToSendExternal = checkIfDepartmentAllowedToSendExternal(externalDepartment, tenant, session);

                            if (isExternalDeptAllowedToSendExternal) {
                                outSenderGroup = "tenant_" + tenant + "_cts_outgoing_sender_" + externalDepartment + "_" + secrecyLevel.replace(" ", "_");

                            }
                        }
                        if (userGroups.contains(outSenderGroup) || (userGroups.contains(outPersonalGroup) && currentUser.getName().equals(creator))) {
                            if (ConfigIsActive(SystemConfigurationAction.CORRESPONDENCE_MANUALARCHIVE))
                                availableActions.add("archiveOutgoing");
                            if (ConfigIsActive(SystemConfigurationAction.CORRESPONDENCE_REOPEN))
                                if(!doc.hasFacet("HubCorrespondence")) {
                                    if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                        availableActions.add("unlock");

                                    }
                                    if (!doc.isLocked()) {
                                        availableActions.add("reOpenOutgoing");
                                    }
                                }
                            availableActions.add("printQrcode");
                            // availableActions.add("addToFavorite");//Amjad
                        }
                    }
                    if (state.equals(OutgoingCorrespondenceLifeCycleState.ARCHIVED.getState())) {
                        availableActions.add("printQrcode");
                        // availableActions.add("addToFavorite");//Amjad
                    }


                }
                /////////////////////////////////////Internal Correspondence/////////////////////////////
                else if (doc.getType().equals(DOCTYPE_INTERNAL_CORRESPONDENCE) && ConfigIsActive(SystemConfigurationAction.CORRESPONDENCE_INTERNAL_ACTIVE)) {
                    String previousState = (String) doc.getPropertyValue("inter_corr:previousCorrState");

                    String managerGroup = "tenant_" + tenant + "_cts_role_" + toDepartment + "_Manager_" + secrecyLevel.replace(" ", "_");
                    String intFromPersonalGroup = "tenant_" + tenant + "_cts_internal_personalhandler_" + fromDepartment + "_" + secrecyLevel.replace(" ", "_");
                    String intFromRegistrarGroup = "tenant_" + tenant + "_cts_internal_registrar_" + fromDepartment + "_" + secrecyLevel.replace(" ", "_");
                    String intFromSenderGroup = "tenant_" + tenant + "_cts_internal_sender_" + fromDepartment + "_" + secrecyLevel.replace(" ", "_");
                    String intToOwnerGroup = "tenant_" + tenant + "_cts_internal_owner_" + toDepartment + "_" + secrecyLevel.replace(" ", "_");

                    if (currentUser.getName().equals(creator)) {
                        if (state.equals(OutgoingCorrespondenceLifeCycleState.DRAFT.getState())) {
                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                availableActions.add("unlock");

                            } else {

                                if (doc.hasFacet(CREATED_FROM_TEMPLATE_FACET) && doc.getPropertyValue("file:content") == null) {

                                    if (doc.isLocked() && doc.getLockInfo().getOwner().equals("system")) {
                                        availableActions.add("onlyOfficeUpdate");
                                    } else {
                                        if (!doc.isLocked()) {
                                            availableActions.add("updateInternal");
                                            availableActions.add("onlyOfficeUpdate");
                                            //availableActions.add("onlyOfficeFinish");
                                            availableActions.add("deleteInternal");
                                            availableActions.add("startReviewInternal");
                                        }
                                    }

                                } else {
                                    if (!doc.isLocked()) {
                                        availableActions.add("startReviewInternal");
                                        availableActions.add("addAssignment");
                                        availableActions.add("updateInternal");
                                        availableActions.add("deleteInternal");
                                        availableActions.add("uploadNewVersion");
                                        if (!isDigitalySigned) {
                                            availableActions.add("annotate");
                                            availableActions.add("pSign");
                                        }

                                        if (isPSignActive && contribution.equals("default")) {
                                            availableActions.add("dSign");
                                        }
                                    }
                                    if(isWorkspaceIsEnabled())
                                    {
                                        availableActions.add("addWorkspace");
                                    }
                                    // availableActions.add("addToFavorite");/////Amjad

                                    //removed for personal handler, correspondence must follow the approval
                                    /*if (userGroups.contains(intFromPersonalGroup)) {
                                        availableActions.add("registerInternal");
                                        //availableActions.add("sendWithoutApproval");

                                    }*/
                                }
                            }

                            availableActions.add("addAttachment");
                            availableActions.add("addRelation");
                            availableActions.add("tag");

                        }
                        if (state.equals(OutgoingCorrespondenceLifeCycleState.ASSIGNED.getState())
                                && (previousState.equals(OutgoingCorrespondenceLifeCycleState.DRAFT.getState())
                                || previousState.equals(OutgoingCorrespondenceLifeCycleState.APPROVED.getState()))) {

                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                availableActions.add("unlock");

                            } else {
                                availableActions.add("addAssignment");
                                if (!doc.isLocked()) {
                                    if (!isDigitalySigned) {
                                        availableActions.add("annotate");
                                        availableActions.add("pSign");
                                    }

                                    if (isPSignActive && contribution.equals("default")) {
                                        availableActions.add("dSign");
                                    }
                                }
                            }
                            if (state.equals(OutgoingCorrespondenceLifeCycleState.ASSIGNED.getState()) && hasAssignmentWorkFlowOnCorrespondenced(doc)) {
                                availableActions.add("addWorkspace");
                            }
                            availableActions.add("addRelation");
                            availableActions.add("tag");


                            // availableActions.add("addToFavorite");/////Amjad
                        }
                        if (state.equals(OutgoingCorrespondenceLifeCycleState.UNDER_REVISION.getState())) {
                            //String corrSecrecyLevel = (String) doc.getPropertyValue("corr:secrecyLevel");
                            //String departmentCode = (String) doc.getPropertyValue("corr:from");
                            //String approverGroup = "tenant_" + session.getPrincipal().getTenantId() + "_cts_outgoing_approver_" + departmentCode + "_" + corrSecrecyLevel.replace(" ", "_");
                            DocumentRoutingService documentRoutingService = Framework.getService(DocumentRoutingService.class);
                            List<Task> tasks = documentRoutingService.getTasks(doc, "",
                                    null, null, session);
                            if (!CollectionUtils.isEmpty(tasks)) {
                                String actor = tasks.get(0).getActors().get(0);
                                if (currentUser.getName().equals(actor) || currentUser.getGroups().contains(actor)) {
                                    if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                        availableActions.add("unlock");

                                    } else {


                                        if (currentUser.getName().equals(creator) && tasks.get(0).getType().equals("WF_OutgoingCorrespondence_Review-20210617160931550-approve-task") && doc.hasFacet(CREATED_FROM_TEMPLATE_FACET)) {
                                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals("system")) {
                                                availableActions.add("onlyOfficeUpdate");
                                                if(doc.getPropertyValue("file:content") != null)
                                                    availableActions.add("onlyOfficePreview");

                                            }

                                            if (!doc.isLocked()) {
                                                availableActions.add("onlyOfficeUpdate");
                                                //availableActions.add("onlyOfficeFinish");

                                                availableActions.add("onlyOfficePreview");
                                            }

                                        }
                                        if (!doc.isLocked()) {
                                            if (!isDigitalySigned) {
                                                availableActions.add("annotate");
                                                availableActions.add("pSign");
                                            }

                                            if (isPSignActive && contribution.equals("default")) {
                                                availableActions.add("dSign");
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (canDoReminder(doc)) {
                                    availableActions.add("reviewReminder");
                                }
                            }
                        }
                    } else {
                        if (state.equals(OutgoingCorrespondenceLifeCycleState.ASSIGNED.getState())) {

                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                availableActions.add("unlock");
                            }
                            if (state.equals(OutgoingCorrespondenceLifeCycleState.ASSIGNED.getState()) && hasAssignmentWorkFlowOnCorrespondenced(doc)) {
                                availableActions.add("addWorkspace");
                            }
                        }
                        if (state.equals(OutgoingCorrespondenceLifeCycleState.UNDER_REVISION.getState())) {

                            String legalDepartmentCode = (String) systemConfigurationDoc.getPropertyValue("sys_config:legalDepartment");
                            String legalEmployeeGroup = "tenant_" + tenant + "_cts_role_" + legalDepartmentCode + "_Employee_" + secrecyLevel.replace(" ", "_");
                            String legalManagerGroup = "tenant_" + tenant + "_cts_role_" + legalDepartmentCode + "_Manager_" + secrecyLevel.replace(" ", "_");
                            boolean currentUserIslegal = currentUser.getAllGroups().contains(legalEmployeeGroup) || currentUser.getAllGroups().contains(legalManagerGroup) ? true : false;

                            String corrSecrecyLevel = (String) doc.getPropertyValue("corr:secrecyLevel");
                            String departmentCode = (String) doc.getPropertyValue("corr:from");
                            //String approverGroup = "tenant_" + session.getPrincipal().getTenantId() + "_cts_outgoing_approver_" + departmentCode + "_" + corrSecrecyLevel.replace(" ", "_");
                            DocumentRoutingService documentRoutingService = Framework.getService(DocumentRoutingService.class);
                            List<Task> tasks = documentRoutingService.getTasks(doc, "",
                                    null, null, session);
                            if (!CollectionUtils.isEmpty(tasks)) {
                                String actor = tasks.get(0).getActors().get(0);
                                DocumentModel wfDocument= GetWorkFlowDocument(tasks.get(0));

                                String reviewCurrentDepartment = (String)wfDocument.getPropertyValue("var_WF_OutgoingCorrespondence_Review:currentApprovalDepartment");

                                if (currentUser.getName().equals(actor) || currentUser.getGroups().contains(actor)) {
                                    if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                        availableActions.add("unlock");

                                    } else {


                                       /* if (currentUser.getName().equals(creator) && tasks.get(0).getType().equals("WF_OutgoingCorrespondence_Review-20210617160931550-approve-task")&&doc.hasFacet(CREATED_FROM_TEMPLATE_FACET)) {
                                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals("system")) {
                                                availableActions.add("onlyOfficeUpdate");
                                                availableActions.add("onlyOfficePreview");
                                            }
                                            availableActions.add("onlyOfficeUpdate");
                                            availableActions.add("onlyOfficeFinish");
                                            availableActions.add("onlyOfficePreview");
                                        }*/
                                        ///////////////////////Legal Review Actions For Create From template WP-186/////////////
                                        if(!contribution.equals("default")
                                                &&reviewCurrentDepartment.equals(legalDepartmentCode)
                                                && currentUserIslegal
                                                && tasks.get(0).getType().equals("WF_OutgoingCorrespondence_Review-20210617154521372-accept-reject-task")
                                                && doc.hasFacet(CREATED_FROM_TEMPLATE_FACET)){
                                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals("system")) {
                                                availableActions.add("onlyOfficeUpdate");
                                                if(doc.getPropertyValue("file:content") != null)
                                                    availableActions.add("onlyOfficePreview");
                                            }
                                            else if(!doc.isLocked()) {
                                                   // availableActions.add("updateOutgoing");
                                                availableActions.add("onlyOfficeUpdate");
                                                availableActions.add("onlyOfficeFinish");
                                                if(doc.getPropertyValue("file:content") != null)
                                                    availableActions.add("onlyOfficePreview");
                                            }
                                        }
                                        /////////////////////End Legal Review Actions//////////
                                        if (!doc.isLocked()) {
                                            if(!contribution.equals("default"))
                                            {
                                                if(!(doc.getPropertyValue("file:content") != null)  && doc.hasFacet(CREATED_FROM_TEMPLATE_FACET)) {
                                                    availableActions.add("onlyOfficeUpdate");
                                                }

                                            }
                                            if(!contribution.equals("default") && doc.getPropertyValue("file:content") != null)
                                            {
                                                if(!(doc.getPropertyValue("file:content") != null) && doc.hasFacet(CREATED_FROM_TEMPLATE_FACET))
                                                {
                                                    availableActions.add("onlyOfficePreview");
                                                }
                                            }
                                            if (!isDigitalySigned) {
                                                if(contribution.equals("default"))
                                                {
                                                    availableActions.add("annotate");
                                                    availableActions.add("pSign");
                                                }
                                                else if(!contribution.equals("default") && doc.getPropertyValue("file:content") != null)
                                                {
                                                    availableActions.add("annotate");
                                                    availableActions.add("pSign");
                                                }
                                            }

                                            if (isPSignActive && contribution.equals("default")) {
                                                availableActions.add("dSign");
                                            }
                                        }
                                        /*if (!doc.isLocked()) {
                                            if (!isDigitalySigned) {
                                                availableActions.add("annotate");
                                                availableActions.add("pSign");
                                            }

                                            if (isPSignActive && contribution.equals("default")) {
                                                availableActions.add("dSign");
                                            }
                                        }*/
                                    }
                                }
                            }

                        }
                    }
                    if (state.equals(OutgoingCorrespondenceLifeCycleState.SENT.getState()) && StringUtils.isEmpty(owner) && userGroups.contains(intToOwnerGroup) && ConfigIsActive(SystemConfigurationAction.CORRESPONDENCE_RESET_OWNER)) {
                        if (!doc.isLocked()) {
                            availableActions.add("setOwner");
                        }
                        //availableActions.add("printQrcode");
                    }
                    if (state.equals(OutgoingCorrespondenceLifeCycleState.SENT.getState()) && userGroups.contains(managerGroup)) {
                        if (!doc.isLocked()) {
                            availableActions.add("reAssign");
                        }
                    }
                    if (currentUser.getName().equals(owner) && (!(state.equals(OutgoingCorrespondenceLifeCycleState.DRAFT.getState()))
                            && !(state.equals(OutgoingCorrespondenceLifeCycleState.REGISTERED.getState()))
                            && !(state.equals(OutgoingCorrespondenceLifeCycleState.APPROVED.getState()))
                            && !(state.equals(OutgoingCorrespondenceLifeCycleState.ASSIGNED.getState())))
                            || (state.equals(OutgoingCorrespondenceLifeCycleState.ASSIGNED.getState()) && previousState.equals(OutgoingCorrespondenceLifeCycleState.SENT.getState()))) {
                        availableActions.add("internalReply");
                        availableActions.add("printQrcode");
                    }
                    if (state.equals(OutgoingCorrespondenceLifeCycleState.APPROVED.getState())) {

                        if (userGroups.contains(intFromRegistrarGroup) || (userGroups.contains(intFromPersonalGroup) && currentUser.getName().equals(creator))) {
                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                availableActions.add("unlock");

                            } else {
                                if (!doc.isLocked()) {
                                    availableActions.add("registerInternal");
                                }
                                //availableActions.add("annotate");
                            }
                            // availableActions.add("addToFavorite");//Amjad
                        }
                       /* if (currentUser.getName().equals(creator)) {
                            availableActions.add("addAssignment");
                        }*/
                    }
                    if (state.equals(OutgoingCorrespondenceLifeCycleState.REGISTERED.getState())) {
                        if (userGroups.contains(intFromRegistrarGroup) || userGroups.contains(intFromSenderGroup) || (userGroups.contains(intFromPersonalGroup) && currentUser.getName().equals(creator))) {

                            if (userGroups.contains(intFromSenderGroup) || (userGroups.contains(intFromPersonalGroup) && currentUser.getName().equals(creator))) {
                                if (!doc.isLocked()) {
                                    availableActions.add("sendInternal");
                                }
                            }
                            //availableActions.add("annotate");
                            // availableActions.add("addToFavorite");//Amjad
                            if (!doc.hasFacet("QrCodeSticker")) {
                                if(ConfigIsActive(SystemConfigurationAction.CORRESPONDENCE_SET_QRCODE))
                                {
                                    if (!doc.isLocked()) {
                                        availableActions.add("selectQrcode");
                                    }
                                }
                                else
                                {
                                    if (!doc.isLocked()) {
                                        availableActions.add("addQrcode");
                                    }
                                }
                            }
                            availableActions.add("printQrcode");
                        }
                    }
                    if (currentUser.getName().equals(owner)) {
                        // availableActions.add("addRelation");
                        if (state.equals(OutgoingCorrespondenceLifeCycleState.SENT.getState())) {
                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                availableActions.add("unlock");

                            } else {
                                Boolean needReply = (Boolean) doc.getPropertyValue( "corr:requireReply");
                                if(needReply){
                                if(!ConfigIsActive(SystemConfigurationAction.CORRESPONDENCE_CLOSE_WITHOUT_REPLY)) {
                                    Boolean replied = (Boolean) doc.getPropertyValue("corr:replied");
                                    if(!doc.isLocked()) {
                                        availableActions.add("addCircular");

                                        if (replied) {
                                            availableActions.add("closeInternal");

                                        }
                                    }
                                }
                                else
                                {
                                    if (!doc.isLocked()) {
                                        availableActions.add("closeInternal");
                                        availableActions.add("addCircular");
                                    }
                                }
                                }
                                else {
                                    if (!doc.isLocked()) {
                                        availableActions.add("closeInternal");
                                        availableActions.add("addCircular");

                                    }
                                }
                                //availableActions.add("return");
                                availableActions.add("addAssignment");
                                //availableActions.add("annotate");
                                // availableActions.add("addToFavorite");//Amjad

                            }
                            //availableActions.add("printQrcode");
                            availableActions.add("addRelation");
                            availableActions.add("tag");
                            // availableActions.add("reply");
                            // availableActions.add("addToFavorite");//Amjad

                        }
                        if (state.equals(OutgoingCorrespondenceLifeCycleState.ASSIGNED.getState())
                                && previousState.equals(OutgoingCorrespondenceLifeCycleState.SENT.getState())) {
                            if (doc.isLocked() && doc.getLockInfo().getOwner().equals(currentUser.getName())) {
                                availableActions.add("unlock");

                            } else {
                                availableActions.add("addAssignment");
                                // availableActions.add("annotate");
                            }
                        }

                        if (state.equals(OutgoingCorrespondenceLifeCycleState.CLOSED.getState())) {
                            if (ConfigIsActive(SystemConfigurationAction.CORRESPONDENCE_MANUALARCHIVE))
                                if (!doc.isLocked()) {
                                    availableActions.add("archiveInternal");
                                }
                            if (ConfigIsActive(SystemConfigurationAction.CORRESPONDENCE_REOPEN))
                                if (!doc.isLocked()) {
                                    availableActions.add("reOpenInternal");
                                }
                            availableActions.add("addRelation");
                            availableActions.add("tag");
                            //availableActions.add("addToFavorite");//Amjad
                            //availableActions.add("printQrcode");//Amjad

                            // availableActions.add("addToFavorite");//Amjad

                        }
                    }
                    if (state.equals(OutgoingCorrespondenceLifeCycleState.ARCHIVED.getState())) {
                        //availableActions.add("addToFavorite");//Amjad
                    }


                }

            }
            availableActions = availableActions.stream().distinct().collect(Collectors.toList());
        } catch (Exception E) {
            return availableActions;
        }
        return availableActions;

    }

    private boolean isWorkspaceIsEnabled() {
        try {
            if (systemConfigurationDoc.getProperty(SystemConfigurationAction.CORRESPONDECE_WORKSPACE.getState()) != null) {
                return (Boolean) systemConfigurationDoc.getPropertyValue(SystemConfigurationAction.CORRESPONDECE_WORKSPACE.getState());
            }
            return Boolean.TRUE;
        } catch (Exception e) {
            return Boolean.TRUE;
        }
    }


    private Boolean HasAssignmentWorkFlow(DocumentModel assignment) throws OperationException {
        String state = (String) assignment.getCurrentLifeCycleState();
        if (!(state.equals("inProgress"))) {
            return false;
        } else {
            // String correspondenceId = (String) assignment.getPropertyValue("cts_common:correspondence");
            Map<String, Serializable> paramsFetch = new HashMap<>();
//            String query = "ecm:primaryType = 'DocumentRoute' and ecm:currentLifeCycleState = 'running' and var_WF_IncomingCorrespondence_Assigment:assignment = '"+assignment.getId()+"'";
//            paramsFetch.put("property", "dc:title");
//            paramsFetch.put("values", "WF_IncomingCorrespondence_Assigment");
            String query = "ecm:mixinType = 'HiddenInNavigation' AND nt:type IN ('WF_IncomingCorrespondence_Assigment-20210705133153717-simple-task') and ecm:currentLifeCycleState = 'opened' and nt:targetDocumentsIds ='" + assignment.getId() + "'";
            paramsFetch.put("property", "ecm:mixinType");
            paramsFetch.put("values", "RoutingTask");
            paramsFetch.put("query", query);
            operationContext.setInput(assignment);
            DocumentModelList activeTasks = (DocumentModelList) automationService.run(operationContext, AUTOMATION_FETCH_BY_PROPERTY, paramsFetch);
            if (activeTasks == null || activeTasks.isEmpty()) {
                return false;
            } else {
                return true;
            }
//            if(assignmentsWF != null && !assignmentsWF.isEmpty()) {
//                String flowId = assignmentsWF.get(0).getId();
//                Map<String, Serializable> paramsPageProvider = new HashMap<>();
//                paramsPageProvider.put("providerName", "PP_Correspondence_Assignment_ActiveTasks");
//                paramsPageProvider.put("queryParams", flowId);
//                operationContext.setInput(assignment);
//                DocumentModelList activeTasks = (DocumentModelList) automationService.run(operationContext, AUTOMATION_PAGE_PROVIDER, paramsPageProvider);
//                if(activeTasks == null || activeTasks.isEmpty()){
//                    return false;
//                }
//                else {
//                    return true;
//                }
//            }

        }
        //return false;
    }

    private Boolean hasAssignmentWorkFlowOnCorrespondenced(DocumentModel correspondence) throws OperationException {
        String state = (String) correspondence.getCurrentLifeCycleState();
        if (!(state.equals("assigned"))) {
            return false;
        } else {

            Map<String, Serializable> paramsFetch = new HashMap<>();
            String query = "ecm:mixinType = 'HiddenInNavigation' AND dc:title ='WF_IncomingCorrespondence_Assigment' and ecm:currentLifeCycleState = 'running' and var_WF_IncomingCorrespondence_Assigment:correspondence ='" + correspondence.getId() + "'";
            paramsFetch.put("property", "ecm:mixinType");
            paramsFetch.put("values", "DocumentRoute");
            paramsFetch.put("query", query);
            operationContext.setInput(null);
            DocumentModelList runningWorkflow = (DocumentModelList) automationService.run(operationContext, AUTOMATION_FETCH_BY_PROPERTY, paramsFetch);
            if (runningWorkflow == null || runningWorkflow.isEmpty()) {
                return false;
            } else {
                paramsFetch = new HashMap<>();
                query = "ecm:mixinType = 'HiddenInNavigation' AND nt:type IN ('WF_IncomingCorrespondence_Assigment-20210705133153717-simple-task') and ecm:currentLifeCycleState = 'opened' and nt:processId='" + runningWorkflow.get(0).getId() + "'";
                paramsFetch.put("property", "ecm:mixinType");
                paramsFetch.put("values", "RoutingTask");
                paramsFetch.put("query", query);
                operationContext.setInput(null);
                DocumentModelList activeTasks = (DocumentModelList) automationService.run(operationContext, AUTOMATION_FETCH_BY_PROPERTY, paramsFetch);
                if (activeTasks == null || activeTasks.isEmpty()) {
                    return false;
                } else {
                    return true;
                }
            }
        }

    }

    private Boolean canDoReminder(DocumentModel doc) {
        String xPATH = "";
        if (doc.getType().equals(DOCTYPE_ASSIGNMENT))
            xPATH = "assign:lastReminder";
        else if (doc.getType().equals(DOCTYPE_OUTGOING_CORRESPONDENCE))
            xPATH = "out_corr:lastReminder";
        else if (doc.getType().equals(DOCTYPE_INTERNAL_CORRESPONDENCE))
            xPATH = "inter_corr:lastReminder";

        GregorianCalendar lasReminder = (GregorianCalendar) doc.getPropertyValue(xPATH);
        if (lasReminder == null)
            return true;
        else {
            Date currentDate = new Date();
            //  Date lastReminderDate = lasReminder.g
            long diffInMs = currentDate.getTime() - lasReminder.getTimeInMillis();
            long hoursDiff = diffInMs / (3600 * 1000);
            if (hoursDiff >= 1) {
                return true;
            }

        }
        return false;
    }

    private Boolean canDoEscalation(DocumentModel doc) {

        String xPATH = "assign:lastReminder";
        GregorianCalendar lasReminder = (GregorianCalendar) doc.getPropertyValue(xPATH);
        if (lasReminder == null)
            return false;
        else {
            xPATH="assign:dueDate";
            GregorianCalendar currentDate= new GregorianCalendar();
            GregorianCalendar dueDate = (GregorianCalendar) doc.getPropertyValue(xPATH);
            if(dueDate.before(currentDate))
            {
                return true;
            }
        }
        return false;
    }
    private Boolean HasOpenTask(DocumentModel assignment, String actor, String tag) {
        DocumentModelList list = new DocumentModelListImpl();
        //String recommendedParent = (String) assignment.getPropertyValue("assign:recommendedParent");
        String query = "";
        //if(recommendedParent.isEmpty() || recommendedParent.equals("null"))
        if (tag.equals("department"))
            query = "Select * from Document where ecm:mixinType IN ('RoutingTask') AND ecm:isVersion = 0 AND ecm:currentLifeCycleState = 'opened' AND nt:targetDocumentsIds = '" + assignment.getId() + "' AND (nt:actors = '" + actor + "' OR nt:actors = '" + session.getPrincipal().getName() + "')";
        else
            query = "Select * from Document where ecm:mixinType IN ('RoutingTask') AND nt:type IN ('WF_IncomingCorrespondence_Assigment-20210705133153717-simple-task') AND ecm:isVersion = 0 AND ecm:currentLifeCycleState = 'opened' AND nt:targetDocumentsIds = '" + assignment.getId() + "' AND nt:actors = '" + actor + "'";
        DocumentModelList docs = session.query(query);
        if (docs == null || docs.isEmpty())
            return false;
        else
            return true;
    }

    private Boolean HasCreatorReviewOpenTask(DocumentModel correspondence, String actor) {
        DocumentModelList list = new DocumentModelListImpl();
        //String recommendedParent = (String) assignment.getPropertyValue("assign:recommendedParent");
        String query = "";
        //if(recommendedParent.isEmpty() || recommendedParent.equals("null"))

       query = "Select * from Document where ecm:mixinType IN ('RoutingTask') AND nt:type IN ('WF_OutgoingCorrespondence_Review-20210617160931550-approve-task') AND ecm:isVersion = 0 AND ecm:currentLifeCycleState = 'opened' AND nt:targetDocumentsIds = '" + correspondence.getId() + "' AND nt:actors = '" + actor + "'";
        DocumentModelList docs = session.query(query);
        if (docs == null || docs.isEmpty())
            return false;
        else
            return true;
    }

    private DocumentModel GetSystemConfiguration(String tenantId) {
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

    private DocumentModel GetSystemConfiguration(String tenantId, CoreSession session) {
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

    private Boolean ConfigIsActive(SystemConfigurationAction action) {
        try {
            if (systemConfigurationDoc.getProperty(action.getState()) != null) {
                return (Boolean) systemConfigurationDoc.getPropertyValue(action.getState());
            }
            return Boolean.FALSE;
        } catch (Exception e) {
            return false;
        }

    }

    private String getCommunicationDepartment() {
        try {
            if (systemConfigurationDoc.getProperty("subscription/communicationDepartment") != null) {
                return (String) systemConfigurationDoc.getPropertyValue("subscription/communicationDepartment");
            }
            return null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private Boolean ConfigIsActiveTenant(SystemConfigurationAction action, String tenant) {
        boolean active =false;
        active =  CoreInstance.doPrivileged(session, s -> {
                    try {
                        DocumentModel tenantSystemConfigurationDoc = GetSystemConfiguration(tenant, s);
                        Property isActive = tenantSystemConfigurationDoc.getProperty(action.getState());
                        if (isActive != null) {
                            Boolean propertyValue = (Boolean) tenantSystemConfigurationDoc.getPropertyValue(action.getState());
                            if(propertyValue != null)
                            {
                                return propertyValue;
                            }
                            else
                                return Boolean.FALSE;
                        }
                        return Boolean.FALSE;
                    } catch (Exception e) {
                        return Boolean.FALSE;
                    }
                }
        );
        return active;

    }

    public Boolean checkIfUserAllowedToSendExternal(String tenant)
    {
        String ctsUserGroupPrefix = "tenant_" + tenant + "_cts_role_sys_User";
        String departmentGroupPrefix = "tenant_" + tenant + "_cts_role_";
        String communicationManagerPrefix = "tenant_" + tenant + "_cts_role_Communication_Manager";
        String vrDepartmentPrefix = "tenant_" + tenant + "_cts_role_virtual_department";
        String incomingHandlerGroupPrefix = "tenant_" + tenant + "_cts_incoming_handler_";
        NuxeoPrincipal currentUser = session.getPrincipal();
        List<String> userGroups = currentUser.getGroups();
        List<String> userDepartment = userGroups.stream().filter(str -> !(str.toLowerCase().equals(ctsUserGroupPrefix.toLowerCase())))
                .filter(str -> !(str.toLowerCase().equals(communicationManagerPrefix.toLowerCase())))
                .filter(str -> str.toLowerCase().startsWith(departmentGroupPrefix.toLowerCase())).map(str -> {
                    String role = str.replace(departmentGroupPrefix, "");
                    role = role.substring(0, role.indexOf("_"));
                    return role;
                }).distinct().collect(Collectors.toList());

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
                        return true;

                    }
                }
            } catch (QueryParseException qe) {
                return false;
            }

        }
        return  false;
    }
    public Boolean checkIfDepartmentAllowedToSendExternal(String departmentCode, String tenantId, CoreSession session) {
        try {
//                    boolean tx = TransactionHelper.startTransaction();
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT * FROM Department WHERE comp:companyCode");
            sb.append(" = ");
            sb.append(NXQL.escapeString(tenantId));
            sb.append(" AND ");
            sb.append("dc:title");
            sb.append(" = ");
            sb.append(NXQL.escapeString(departmentCode));
            String q = sb.toString();

            DocumentModelList list = session.query(q);
            //                  TransactionHelper.commitOrRollbackTransaction();
            if (!CollectionUtils.isEmpty(list)) {
                DocumentModel department = list.get(0);
                Boolean isAllowed = (Boolean) department.getPropertyValue("dept:isAllowRecExternal");
                if (isAllowed) {
                    return Boolean.TRUE;

                }
                else
                {return Boolean.FALSE;}
            }
            return Boolean.FALSE;
        } catch (
                QueryParseException qe) {
            return Boolean.FALSE;
        }
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
    private DocumentModel GetWorkFlowDocument(Task task) {
        String wfId = task.getProcessId();
        DocumentModel wfDocument =session.getDocument(new IdRef(wfId));
        return  wfDocument;
    }

}

enum SecrecyLevel {
    NORMAL("Normal"), SECRET("Secret"), TOP_SECRET("Top Secret"), RESTRICTED("Restricted"), NON_TRANSFERABLE("Non Transferable");
    private final String secrecy;

    SecrecyLevel(String secrecy) {
        this.secrecy = secrecy;
    }

    public String getSecrecy() {
        return secrecy;
    }

    public static SecrecyLevel fromString(String secrecy) {
        for (SecrecyLevel secrecyLevel : SecrecyLevel.values()) {
            if (secrecyLevel.secrecy.equalsIgnoreCase(secrecy)) {
                return secrecyLevel;
            }
        }
        throw new IllegalArgumentException("Unknown SecrecyLevel: " + secrecy);
    }
}

enum IncomingCorrespondenceLifeCycleState {

    DRAFT("draft"),
    READY_TO_REGISTER("readyToRegister"),
    REGISTERED("registered"),
    ASSIGHNED("assigned"),
    ARCHIVED("archived"),

    IN_PROGRESS("inProgress"),

    CLOSED("closed");

    private final String state;

    IncomingCorrespondenceLifeCycleState(String state) {
        this.state = state;
    }

    public String getState() {
        return state;
    }

    public static IncomingCorrespondenceLifeCycleState fromString(String state) {
        for (IncomingCorrespondenceLifeCycleState lifeCycleState : IncomingCorrespondenceLifeCycleState.values()) {
            if (lifeCycleState.state.equalsIgnoreCase(state)) {
                return lifeCycleState;
            }
        }
        throw new IllegalArgumentException("Unknown lifecycle state: " + state);
    }
}

enum OutgoingCorrespondenceLifeCycleState {

    DRAFT("draft"),
    APPROVED("approved"),
    REGISTERED("registered"),
    ASSIGNED("assigned"),
    ARCHIVED("archived"),
    SENT("sent"),
    UNDER_REVISION("under_revision"),
    IN_PROGRESS("inProgress"),

    CLOSED("closed");

    private final String state;

    OutgoingCorrespondenceLifeCycleState(String state) {
        this.state = state;
    }

    public String getState() {
        return state;
    }

    public static OutgoingCorrespondenceLifeCycleState fromString(String state) {
        for (OutgoingCorrespondenceLifeCycleState lifeCycleState : OutgoingCorrespondenceLifeCycleState.values()) {
            if (lifeCycleState.state.equalsIgnoreCase(state)) {
                return lifeCycleState;
            }
        }
        throw new IllegalArgumentException("Unknown lifecycle state: " + state);
    }
}

enum SystemConfigurationAction {

    ASSIGNMENT_ADD_ATTACHMENT("sys_config:additionAssignmentActions/addAttachment"),
    ASSIGNMENT_ABORT("sys_config:additionAssignmentActions/abortAssignment"),
    ASSIGNMENT_OUTGOING("sys_config:additionAssignmentActions/outgoingActive"),
    // ASSIGNMENT_PRIVATE("sys_config:internalIsValid"),
    CORRESPONDENCE_WATERMARK("sys_config:watermarkIsValid"),
    CORRESPONDENCE_INCOMING_DYNAMICFORM("sys_config:dynamicFormIsValid/incomingDynamicForm"),
    CORRESPONDENCE_OUTGOING_DYNAMICFORM("sys_config:dynamicFormIsValid/outgoingDynamicForm"),
    CORRESPONDENCE_INTERNAL_DYNAMICFORM("sys_config:dynamicFormIsValid/internalDynamicForm"),
    CORRESPONDENCE_INTERNAL_ACTIVE("sys_config:internalIsValid"),
    CORRESPONDENCE_SPECIAL_REVIEW("sys_config:reviewCorrByList"),
    CORRESPONDENCE_LISTED_FORM("sys_config:isListedFields"),
    CORRESPONDENCE_REOPEN("sys_config:additionCorrespondenceActions/reOpen"),
    CORRESPONDENCE_RESET_OWNER("sys_config:additionCorrespondenceActions/resetCorrespondence"),
    CORRESPONDENCE_FAVORITE("sys_config:additionCorrespondenceActions/favorite"),
    CORRESPONDENCE_REPLY("sys_config:additionCorrespondenceActions/tasdidCorrespondence"),
    CORRESPONDENCE_RESEND("sys_config:isCorrReSendActive"),
    CORRESPONDENCE_DELEGATE("sys_config:isDelegationActive"),
    CORRESPONDENCE_MULTIRECEIVERS("sys_config:isMultiSendActive"),
    CORRESPONDENCE_SCANNER("sys_config:isScannerActive"),
    CORRESPONDENCE_INITTEMPLATES("sys_config:isCorrInitTemplateActive"),
    CORRESPONDENCE_CC("sys_config:isCCActive"),
    CORRESPONDENCE_MANUALARCHIVE("sys_config:isManualArchiveActive"),
    CORRESPONDECE_WORKSPACE("sys_config:workspaceIsValid"),
    OUTGOING_SEND_BY_HUB("sys_config:subscription/isActive"),
    DEPARTMENTS_CAN_CREATE_OUTGOING("sys_config:canCreateOutgoing"),

    DEPARTMENTS_CAN_ASSIGN_TO_SUB("sys_config:assignSubDep"),
    OUTGOING_USER_SELECT_EXTERNAL("sys_config:setExternalDepartment"),
    CORRESPONDENCE_SET_READ_UNREAD("sys_config:viewerLogActive"),
    CORRESPONDENCE_SET_QRCODE("sys_config:canSetQrcode"),
    CORRESPONDENCE_CLOSE_WITHOUT_REPLY("sys_config:closeCorrWithoutReply"),
    VIP_IS_ACTIVE("sys_config:isVipActive");
    private final String state;

    SystemConfigurationAction(String state) {
        this.state = state;
    }

    public String getState() {
        return state;
    }

    public static SystemConfigurationAction fromString(String state) {
        for (SystemConfigurationAction lifeCycleState : SystemConfigurationAction.values()) {
            if (lifeCycleState.state.equalsIgnoreCase(state)) {
                return lifeCycleState;
            }
        }
        throw new IllegalArgumentException("Unknown lifecycle state: " + state);
    }
}

enum WorkSpaceState {

    NEW("new"),
    CLOSED("closed"),
    ARCHIVED("archived");

    private final String state;

    WorkSpaceState(String state) {
        this.state = state;
    }

    public String getState() {
        return state;
    }

    public static WorkSpaceState fromString(String state) {
        for (WorkSpaceState lifeCycleState : WorkSpaceState.values()) {
            if (lifeCycleState.state.equalsIgnoreCase(state)) {
                return lifeCycleState;
            }
        }
        throw new IllegalArgumentException("Unknown lifecycle state: " + state);
    }


}

