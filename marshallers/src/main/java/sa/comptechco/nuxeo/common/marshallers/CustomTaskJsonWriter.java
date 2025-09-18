package sa.comptechco.nuxeo.common.marshallers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.io.download.DownloadService;
import org.nuxeo.ecm.core.io.marshallers.json.OutputStreamWithJsonWriter;
import org.nuxeo.ecm.core.io.registry.MarshallerRegistry;
import org.nuxeo.ecm.core.io.registry.Writer;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.core.io.registry.reflect.Instantiations;
import org.nuxeo.ecm.core.io.registry.reflect.Priorities;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.utils.DateParser;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.platform.actions.ActionContext;
import org.nuxeo.ecm.platform.actions.ELActionContext;
import org.nuxeo.ecm.platform.actions.ejb.ActionManager;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.routing.core.impl.GraphNode;
import org.nuxeo.ecm.platform.routing.core.impl.GraphRoute;
import org.nuxeo.ecm.platform.routing.core.io.DocumentRouteWriter;
import org.nuxeo.ecm.platform.routing.core.io.NodeAccessRunner;
import org.nuxeo.ecm.platform.routing.core.io.TaskWriter;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskComment;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.user.center.profile.UserProfileService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.api.login.NuxeoLoginContext;
import org.nuxeo.runtime.transaction.TransactionHelper;

import javax.inject.Inject;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.nuxeo.common.utils.DateUtils.toZonedDateTime;
import static org.nuxeo.ecm.core.security.UpdateACEStatusWork.FORMATTER;
import static org.nuxeo.ecm.user.center.profile.UserProfileConstants.*;
import org.nuxeo.ecm.multi.tenant.MultiTenantService;
@Setup(mode = Instantiations.SINGLETON, priority = Priorities.OVERRIDE_REFERENCE) // <= an higher priority is used
public class CustomTaskJsonWriter extends TaskWriter {

    private static final Logger log = LogManager.getLogger(TaskWriter.class);
    @Inject
    protected SchemaManager schemaManager;

    @Inject
    protected DownloadService downloadService;

    @Inject
    protected UserProfileService userProfileService;

    @Inject
    private DirectoryService directoryService;


    @Inject
    protected MarshallerRegistry registry;

    @Inject
    protected RenderingContext ctx;

    private  UserManager um;

    String CONTEXT_PATH = "/nuxeo";
    String CONTEXT_PATH_PROP = "org.nuxeo.ecm.contextPath";

    @Override
    public void writeEntityBody(Task item, JsonGenerator jg) throws IOException {
        GraphRoute workflowInstance = null;
        GraphNode node = null;
        String workflowInstanceId = item.getProcessId();
        final String nodeId = item.getVariable(DocumentRoutingConstants.TASK_NODE_ID_KEY);
        try (RenderingContext.SessionWrapper wrapper = ctx.getSession(item.getDocument())) {
            if (StringUtils.isNotBlank(workflowInstanceId)) {
                NodeAccessRunner nodeAccessRunner = new NodeAccessRunner(wrapper.getSession(), workflowInstanceId,
                        nodeId);
                try {
                    nodeAccessRunner.runUnrestricted();
                } catch (DocumentNotFoundException e) {
                    log.warn("Failed to get workflow instance: {}", workflowInstanceId);
                    log.debug(e, e);
                }
                workflowInstance = nodeAccessRunner.getWorkflowInstance();
                node = nodeAccessRunner.getNode();
            }

            jg.writeStringField("id", item.getDocument().getId());
            jg.writeStringField("name", item.getName());
            jg.writeStringField("workflowInstanceId", workflowInstanceId);
            if (workflowInstance != null) {
                jg.writeStringField("workflowModelName", workflowInstance.getModelName());
                writeWorkflowInitiator(jg, workflowInstance.getInitiator());
                jg.writeStringField("workflowTitle", workflowInstance.getTitle());
                jg.writeStringField("workflowLifeCycleState",
                        workflowInstance.getDocument().getCurrentLifeCycleState());
                jg.writeStringField("graphResource", DocumentRouteWriter.getGraphResourceURL(
                        workflowInstance.getDocumentRoute(wrapper.getSession()), ctx));

            }
            jg.writeStringField("state", item.getDocument().getCurrentLifeCycleState());
            jg.writeStringField("directive", item.getDirective());
            jg.writeStringField("created", DateParser.formatW3CDateTime(item.getCreated()));
            jg.writeStringField("dueDate", DateParser.formatW3CDateTime(item.getDueDate()));
            jg.writeStringField("nodeName", item.getVariable(DocumentRoutingConstants.TASK_NODE_ID_KEY));

            jg.writeArrayFieldStart(TARGET_DOCUMENT_IDS);
            final boolean isFetchTargetDocumentIds = ctx.getFetched(ENTITY_TYPE).contains(FETCH_TARGET_DOCUMENT);
            for (String docId : item.getTargetDocumentsIds()) {
                IdRef idRef = new IdRef(docId);
                if (wrapper.getSession().exists(idRef)) {
                    if (isFetchTargetDocumentIds) {
                        writeEntity(wrapper.getSession().getDocument(idRef), jg);
                    } else {
                        jg.writeStartObject();
                        jg.writeStringField("id", docId);
                        jg.writeEndObject();
                    }
                }
            }
            jg.writeEndArray();

            final boolean isFetchActors = ctx.getFetched(ENTITY_TYPE).contains(FETCH_ACTORS);
            jg.writeArrayFieldStart("actors");
            writeActors(item.getActors(), isFetchActors, jg);
            jg.writeEndArray();

            jg.writeArrayFieldStart("delegatedActors");
            writeActors(item.getDelegatedActors(), isFetchActors, jg);
            jg.writeEndArray();

            jg.writeArrayFieldStart("comments");
            for (TaskComment comment : item.getComments()) {
                jg.writeStartObject();
                jg.writeStringField("author", comment.getAuthor());
                jg.writeStringField("text", comment.getText());
                jg.writeStringField("date", DateParser.formatW3CDateTime(comment.getCreationDate().getTime()));
                jg.writeEndObject();
            }
            jg.writeEndArray();

            jg.writeFieldName("variables");
            jg.writeStartObject();
            // add nodeVariables
            if (node != null) {
                writeTaskVariables(node, jg, registry, ctx, schemaManager);
            }
            // add workflow variables
            if (workflowInstance != null) {
                writeWorkflowVariables(workflowInstance, node, jg, registry, ctx, schemaManager);
            }
            jg.writeEndObject();

            if (node != null) {
                jg.writeFieldName("taskInfo");
                jg.writeStartObject();
                jg.writeBooleanField("allowTaskReassignment", node.allowTaskReassignment());

                final ActionManager actionManager = Framework.getService(ActionManager.class);
                jg.writeArrayFieldStart("taskActions");
                for (GraphNode.Button button : node.getTaskButtons()) {
                    if (StringUtils.isBlank(button.getFilter()) || actionManager.checkFilter(button.getFilter(),
                            createActionContext(wrapper.getSession(), node))) {
                        jg.writeStartObject();
                        jg.writeStringField("name", button.getName());
                        jg.writeStringField("url", ctx.getBaseUrl() + "api/v1/task/" + item.getDocument().getId() + "/"
                                + button.getName());
                        jg.writeStringField("label", button.getLabel());
                        jg.writeBooleanField("validate", button.getValidate());
                        jg.writeEndObject();
                    }
                }
                jg.writeEndArray();

                jg.writeFieldName("layoutResource");
                jg.writeStartObject();
                jg.writeStringField("name", node.getTaskLayout());
                jg.writeStringField("url",
                        ctx.getBaseUrl() + "site/layout-manager/layouts/?layoutName=" + node.getTaskLayout());
                jg.writeEndObject();

                jg.writeArrayFieldStart("schemas");
                for (String schema : node.getDocument().getSchemas()) {
                    // TODO only keep functional schema once adaptation done
                    jg.writeStartObject();
                    jg.writeStringField("name", schema);
                    jg.writeStringField("url", ctx.getBaseUrl() + "api/v1/config/schemas/" + schema);
                    jg.writeEndObject();
                }
                jg.writeEndArray();

                jg.writeEndObject();
            }
        }
    }
    /*public void writeEntityBody(Task document, JsonGenerator jg) throws IOException {
*//*        CoreSession session = ctx.getSession(null).getSession();

        // -------------------------------- Handle Nuxeo UI TDF Content ---------------
        try {
            CONTEXT_PATH = Framework.getProperty(CONTEXT_PATH_PROP, "/nuxeo");
        } catch (Exception e) {
            CONTEXT_PATH = "/nuxeo";
        }
        Pattern urlPattern = Pattern.compile("https?://[^/]+"+CONTEXT_PATH+"(/.*)?$");

//       /.../.../Departments Hierarchy
//       /.../.../.../CTS
//       /.../.../.../Meeting management
        Pattern departmentsHierarchyPattern = Pattern.compile("/[^/]+/Departments Hierarchy(/.*)?$");
        Pattern ctsPattern = Pattern.compile("/[^/]+/[^/]+/CTS(/.*)?$");
        Pattern meetingManagementPattern = Pattern.compile("/[^/]+/[^/]+/Meeting management(/.*)?$");

        String referer = "";
        if (ctx.getAllParameters().get("referer") != null && ctx.getAllParameters().get("referer").size() > 0) {
            referer = (String) ctx.getAllParameters().get("referer").get(0);
        }
//        System.out.println("=========================== [87] CustomTaskJsonWriter:: Referer: " + referer + " ===========================================");

        if (urlPattern.matcher(referer).matches() && !session.getPrincipal().isAdministrator() && document != null) {
            List<String> targetDocumentsIds = document.getTargetDocumentsIds();
            boolean hasBlockedItem = false;
            try(NuxeoLoginContext loginContext = Framework.loginSystem()) {
                CoreSession session2 = CoreInstance.getCoreSession(session.getRepositoryName());
                for (String docId : targetDocumentsIds) {
                    DocumentModel targetDocument = session2.getDocument(new IdRef(docId));
                    String docPath = targetDocument.getPath() != null ? targetDocument.getPath().toString() : "";
                    if (departmentsHierarchyPattern.matcher(docPath).matches()
                            || ctsPattern.matcher(docPath).matches()
                            || meetingManagementPattern.matcher(docPath).matches()) {
                        hasBlockedItem = true;
                        break;
                    }
                }
            }


            if (hasBlockedItem) {
//                DocumentModel tmpDoc = session.createDocumentModel(document.getDocument().getType());
//                tmpDoc.setPropertyValue("dc:title", "Hidden Content");
//                super.writeEntityBody(new TaskImpl(tmpDoc), jg);
                jg.writeStringField("name", "Hidden Content");
                jg.writeStringField("id", "Hidden Content");
                return;
            }
        }*//*
        super.writeEntityBody(document, jg);
        // ----------------------------------------------------------------------------

    }*/

    protected void extend(Task document, JsonGenerator jg) throws IOException {
         um = Framework.getService(UserManager.class);
        super.extend(document, jg);
        GraphRoute workflowInstance = null;
        GraphNode node = null;
        String workflowInstanceId = document.getProcessId();
        final String nodeId = document.getVariable(DocumentRoutingConstants.TASK_NODE_ID_KEY);
        try (RenderingContext.SessionWrapper wrapper = ctx.getSession(document.getDocument())) {
            if (StringUtils.isNotBlank(workflowInstanceId)) {
                NodeAccessRunner nodeAccessRunner = new NodeAccessRunner(wrapper.getSession(), workflowInstanceId,
                        nodeId);
                try {
                    nodeAccessRunner.runUnrestricted();
                } catch (DocumentNotFoundException e) {
                    System.out.println("error getting node");
                }
                workflowInstance = nodeAccessRunner.getWorkflowInstance();
                node = nodeAccessRunner.getNode();
            }
            if (node != null) {
                jg.writeFieldName("customtaskInfo");
                jg.writeStartObject();
                final ActionManager actionManager = Framework.getService(ActionManager.class);
                jg.writeArrayFieldStart("customTaskActions");
                for (GraphNode.Button button : node.getTaskButtons()) {
                    if (!StringUtils.isBlank(button.getFilter())) {
                        boolean checkfltr = actionManager.checkFilter(button.getFilter(),
                                createActionContext(wrapper.getSession(), node));
                        System.out.println("Check filter for" + button.getFilter() + "is " + checkfltr);
                    }
                    if (StringUtils.isBlank(button.getFilter()) || actionManager.checkFilter(button.getFilter(),
                            createActionContext(wrapper.getSession(), node))) {
                        jg.writeStartObject();
                        jg.writeStringField("name", button.getName());
                        jg.writeStringField("url", ctx.getBaseUrl() + "api/v1/task/" + document.getDocument().getId() + "/"
                                + button.getName());
                        jg.writeStringField("label", button.getLabel());
                        jg.writeBooleanField("validate", button.getValidate());
                        jg.writeEndObject();
                    }
                }
                jg.writeEndArray();
                jg.writeEndObject();
            }
            CoreSession session = ctx.getSession(null).getSession();
            jg.writeFieldName("workflowInitiatorDetails");
            jg.writeStartObject();
            {

            /*NuxeoPrincipal principal = um.getPrincipal(document.getInitiator());
            DocumentModel up = userProfileService.getUserProfileDocument(document.getInitiator(), session);
            writePrincipal(jg, principal);
            writeCompatibilityUserProfile(jg, up);*/

                CoreInstance.doPrivileged(session, s -> {
                    NuxeoPrincipal principal = um.getPrincipal(document.getInitiator());
                    try {
                        writePrincipal(jg, principal);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                });
                try {
                    // up = userProfileService.getUserProfileDocument(user, session);
                    Map<String, Serializable> profile = getUserMultitenantProfile(document.getInitiator());
                    if (profile != null) {
                        writeMultitenantUserProfile(jg, profile);
                    }

                } catch (DocumentSecurityException securityException) {
                    securityException.printStackTrace();
                }
            }
            jg.writeEndObject();
            List<String> actors = document.getActors();
            jg.writeArrayFieldStart("actorsDetails");
            // jg.writeStartObject();
            for (String actor : actors
            ) {


           /* NuxeoPrincipal principal = um.getPrincipal(actor);
            NuxeoGroup group;
            if(null != principal )
            {
               // group =   um.getGroup("actor");


            DocumentModel up = userProfileService.getUserProfileDocument(actor,session);
           // jg.writeFieldName("actor");

            writePrincipal(jg, principal);
            writeCompatibilityUserProfile(jg,up);

            }*/
                Boolean isPrincipal = CoreInstance.doPrivileged(session, s -> {
                    NuxeoPrincipal principal = um.getPrincipal(actor);
                    if (principal != null) {
                        try {
                            jg.writeStartObject();
                            writePrincipal(jg, principal);

                        } catch (IOException e) {
                            try {
                                jg.writeEndObject();
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                            throw new RuntimeException(e);
                        }
                        return true;
                    } else {
                        return false;
                    }


                });


                if (isPrincipal) {
                    try {
                        // up = userProfileService.getUserProfileDocument(user, session);
                        Map<String, Serializable> profile = getUserMultitenantProfile(actor);
                        if (profile != null) {
                            writeMultitenantUserProfile(jg, profile);
                        }

                    } catch (DocumentSecurityException securityException) {
                        securityException.printStackTrace();
                    }

                    jg.writeEndObject();
                }
            }
            jg.writeEndArray();
            Map<String, String> variables = document.getVariables();
            jg.writeFieldName("customTaskVariables");
            jg.writeStartObject();
            // jg.writeStartObject();
            for (Map.Entry<String, String> entry : variables.entrySet()
            ) {
                if (entry.getKey().equals("personInCharge") || entry.getKey().equals("completedDate") || entry.getKey().equals("attachment")) {

                    jg.writeStringField(entry.getKey(), entry.getValue());

                }
            }
            jg.writeEndObject();

            //jg.writeFieldName("advancedTaskVariables");
            //jg.writeStartObject();
            // jg.writeStartObject();
            for (Map.Entry<String, String> entry : variables.entrySet()
            ) {
                try {
                    if (entry.getKey().equals("personInCharge") || entry.getKey().equals("completedDate") || entry.getKey().equals("attachment") || entry.getKey().equals("taskReply")) {

                        if (entry.getKey().equals("personInCharge")) {
                            String personInCharge = entry.getValue();
                            if (entry.getValue() != null) {
                                ObjectMapper om = new ObjectMapper();
                                JsonNode personObject = om.readTree(personInCharge);

                                if (personObject.get("username") != null) {


                                    jg.writeFieldName("personInCharge");
                                    jg.writeStartObject();
                                    {

            /*NuxeoPrincipal principal = um.getPrincipal(document.getInitiator());
            DocumentModel up = userProfileService.getUserProfileDocument(document.getInitiator(), session);
            writePrincipal(jg, principal);
            writeCompatibilityUserProfile(jg, up);*/

                                        CoreInstance.doPrivileged(session, s -> {

                                            NuxeoPrincipal p = um.getPrincipal(personObject.get("username").asText());
                                            try {
                                                writePrincipal(jg, p);
                                            } catch (IOException e) {
                                                throw new RuntimeException(e);
                                            }

                                        });
                                        try {
                                            // up = userProfileService.getUserProfileDocument(user, session);
                                            Map<String, Serializable> profile = getUserMultitenantProfile(personObject.get("username").asText());
                                            if (profile != null) {
                                                writeMultitenantUserProfile(jg, profile);
                                            }

                                        } catch (DocumentSecurityException securityException) {
                                            securityException.printStackTrace();
                                        }
                                    }
                                    //writePrincipal(jg, p);
                                    jg.writeEndObject();
                                }
                            }

                        } else if (entry.getKey().equals("attachment")) {
                            jg.writeArrayFieldStart("attachments");
                            String[] attachments = entry.getValue().split(",");
                            for (String i : attachments) {
                                try {
                                    DocumentModel attachment = session.getDocument(new IdRef(i));
                                    writeEntity(attachment, jg);

                                } catch (Exception e) {
                                    // e.printStackTrace();
                                    System.out.println("error retrieving attachment variable " + i);
                                }
                            }
                            jg.writeEndArray();
                        } else if (entry.getKey().equals("completedDate")) {
                            Calendar cal = Calendar.getInstance();
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd't'HH:mm:ss", Locale.ENGLISH);
                            cal.setTime(sdf.parse(entry.getValue()));


                            if (cal != null) {
                                jg.writeFieldName("taskCompletedDate");
                                jg.writeString(DateParser.formatW3CDateTime(cal.getTime()));
                           /* jg.writeStartObject();
                            jg.writeStringField("completedDate", DateParser.formatW3CDateTime(cal.getTime()));
                            jg.writeEndObject();*/
                            }
                        } else if (entry.getKey().equals("taskReply")) {

                            jg.writeFieldName("taskReply");
                            jg.writeString(entry.getValue());

                        }

                    } else {
                        jg.writeStringField(entry.getKey(), entry.getValue());
                    }
                } catch (Exception e) {
                    // e.printStackTrace();
                    System.out.println("failed to render task variables");
                }


            }
            //jg.writeEndObject();

            // workflow variables
           /* GraphRoute workflowInstance = null;
            String workflowInstanceId = document.getProcessId();
            final String nodeId = document.getVariable(DocumentRoutingConstants.TASK_NODE_ID_KEY);

            try (RenderingContext.SessionWrapper wrapper = ctx.getSession(document.getDocument())) {
                if (StringUtils.isNotBlank(workflowInstanceId)) {
                    NodeAccessRunner nodeAccessRunner = new NodeAccessRunner(wrapper.getSession(), workflowInstanceId,
                            nodeId);
                    nodeAccessRunner.runUnrestricted();
                    workflowInstance = nodeAccessRunner.getWorkflowInstance();
                }*/

                if (workflowInstance != null) {
                    jg.writeFieldName("workflowVariables");
                    jg.writeStartObject();
                    DocumentRouteWriter.writeVariables(workflowInstance, jg, registry, ctx, schemaManager);
                    jg.writeEndObject();
                }
           // }
        }
    }

    private void writePrincipal(JsonGenerator jg, NuxeoPrincipal principal) throws IOException {
        if (principal == null) {
            return;
        }
        DocumentModel doc = principal.getModel();
        if (doc == null) {
            return;
        }
        UserManager um = Framework.getService(UserManager.class);
        String userSchema = um.getUserSchemaName();
        Collection<Property> properties = doc.getPropertyObjects(userSchema);
        if (properties.isEmpty()) {
            return;
        }
        Writer<Property> propertyWriter = registry.getWriter(ctx, Property.class, APPLICATION_JSON_TYPE);
        jg.writeObjectFieldStart("properties");
        String firstName="",lastName="";
        for (Property property : properties) {
            String localName = property.getField().getName().getLocalName();
            if (!localName.equals(getPasswordField())) {
                if (localName.equals("firstName")&& (!StringUtils.isEmpty((String) property.getValue()))) {
                    firstName = (String) property.getValue();
                }
                if (localName.equals("lastName")&& (!StringUtils.isEmpty((String) property.getValue()))) {
                    lastName = (String) property.getValue();
                }
                jg.writeFieldName(localName);
                OutputStream out = new OutputStreamWithJsonWriter(jg);
                propertyWriter.write(property, Property.class, Property.class, APPLICATION_JSON_TYPE, out);
            }
        }
        jg.writeEndObject();
        jg.writeStringField("fullName",firstName.concat(" ").concat(lastName));
    }
    private String getPasswordField() {
        String userDirectoryName = um.getUserDirectoryName();
        return directoryService.getDirectory(userDirectoryName).getPasswordField();
    }


    protected void writeCompatibilityUserProfile(JsonGenerator jg, DocumentModel up) throws IOException {
        Serializable propertyValue = up.getPropertyValue(USER_PROFILE_BIRTHDATE_FIELD);
        jg.writeStringField("birthdate",
                propertyValue == null ? null : FORMATTER.format(toZonedDateTime((GregorianCalendar) propertyValue)));
        jg.writeStringField("phonenumber", (String) up.getPropertyValue(USER_PROFILE_PHONENUMBER_FIELD));
        Blob avatar = (Blob) up.getPropertyValue(USER_PROFILE_AVATAR_FIELD);
        if (avatar != null) {
            String url = downloadService.getDownloadUrl(up, USER_PROFILE_AVATAR_FIELD, avatar.getFilename());
            jg.writeStringField("avatar", ctx.getBaseUrl() + url);
        } else {
            jg.writeNullField("avatar");
        }
    }

    protected void writeUserProfile(JsonGenerator jg, DocumentModel up) throws IOException {
        Writer<Property> propertyWriter = registry.getWriter(ctx, Property.class, APPLICATION_JSON_TYPE);
        Schema schema = schemaManager.getSchema(USER_PROFILE_SCHEMA);
        // provides the user profile document to the property marshaller
        try (Closeable resource = ctx.wrap().with(ENTITY_TYPE, up).open()) {
            for (Field field : schema.getFields()) {
                jg.writeFieldName(field.getName().getLocalName());
                Property property = up.getProperty(field.getName().getPrefixedName());
                OutputStream out = new OutputStreamWithJsonWriter(jg);
                propertyWriter.write(property, Property.class, Property.class, APPLICATION_JSON_TYPE, out);
            }
        }
    }


    protected static ActionContext createActionContext(CoreSession session, GraphNode node) {
        ActionContext actionContext = new ELActionContext();
        actionContext.setDocumentManager(session);
        actionContext.setCurrentPrincipal(session.getPrincipal());
        if (node != null) {
            Map<String, Object> workflowContextualInfo = new HashMap<String, Object>();
            workflowContextualInfo.putAll(node.getWorkflowContextualInfo(session, true));
            actionContext.putAllLocalVariables(workflowContextualInfo);
            try {
                DocumentModelListImpl documents = (DocumentModelListImpl) workflowContextualInfo.get("workflowDocuments");
                System.out.println("getting task document");
                if (!org.apache.commons.collections4.CollectionUtils.isEmpty(documents)) {
                    DocumentModel doc = documents.get(0);
                    System.out.println("getting task document id" + doc.getId() + " -------- "+doc.getTitle());
                    actionContext.setCurrentDocument(doc);
                }
            }catch (Exception e)
            {
                System.out.println("exception when getting task document");
            }

        }
        return actionContext;
    }

    private Map<String,Serializable> getUserMultitenantProfile(String user)
    {
        CoreSession session = ctx.getSession(null).getSession();

        return CoreInstance.doPrivileged(session, s -> {
            try {
                Map<String,Serializable> profile = new HashMap<String,Serializable>();

                //        boolean tx = TransactionHelper.startTransaction();


                StringBuilder sb = new StringBuilder();
                sb.append("SELECT * FROM UserTenantConfig WHERE ");
                sb.append("tenus:user");
                sb.append(" = ");
                sb.append(NXQL.escapeString(user));
                String q = sb.toString();

                DocumentModelList list = s.query(q);

                if (!CollectionUtils.isEmpty(list)) {
                    profile.put("phonenumber", (String) list.get(0).getPropertyValue("tenus:phoneNumber"));
                    profile.put("firstNameAr", (String) list.get(0).getPropertyValue("tenus:firstNameAr"));
                    profile.put("lastNameAr", (String) list.get(0).getPropertyValue("tenus:lastNameAr"));
                    profile.put("fullNameAr", String.format("%s %s", profile.get("firstNameAr"), profile.get("lastNameAr")));
                    Blob avatar = (Blob) list.get(0).getPropertyValue("tenus:profilePicture");
                    if (avatar != null) {
                        DownloadService downloadService = Framework.getService(DownloadService.class);
                        String url = downloadService.getDownloadUrl(list.get(0), "tenus:profilePicture", avatar.getFilename());
                        profile.put("avatar", url);

                    } else {
                        profile.put("avatar", null);
                    }
                    MultiTenantService multiTenantService = Framework.getService(MultiTenantService.class);

                    CoreSession coreSession = CoreInstance.getCoreSession("default");
                    if (multiTenantService.isTenantIsolationEnabled(coreSession)) {
                        List<Map<String, Serializable>> tenantlist = (List<Map<String, Serializable>>) list.get(0).getPropertyValue("tenus:tenantList");
                        if(session.getPrincipal().isAdministrator()||session.getPrincipal().getTenantId()==null)
                        {
                            //profile.put("disabled", Boolean.TRUE);
                        }
                        else {
                            sb = new StringBuilder();
                            sb.append("SELECT * FROM TenantDomain WHERE ");
                            sb.append("tendom:tenantId");
                            sb.append(" = ");
                            sb.append(NXQL.escapeString(session.getPrincipal().getTenantId()));
                            q = sb.toString();

                            list = s.query(q);
                            for (Map<String, Serializable> tenant : tenantlist) {

                                if (tenant.get("tenantId").equals(list.get(0).getId())) {
                                    profile.put("jobGradeEn", (String) tenant.get("jobGradeEn"));
                                    profile.put("jobGradeAr", (String) tenant.get("jobGradeAr"));
                                    if (!tenant.get("state").equals("enable")) {
                                        profile.put("disabled", Boolean.TRUE);
                                    }
                                }
                            }
                        }
                    }
                    //          TransactionHelper.commitOrRollbackTransaction();
                    return profile;
                } else {
                    //        TransactionHelper.commitOrRollbackTransaction();
                    return null;
                }

            } catch (QueryParseException qe) {
                TransactionHelper.commitOrRollbackTransaction();
                return null;
            }
        });



    /*    PageProviderService pageProviderService = Framework.getService(PageProviderService.class);
        org.nuxeo.ecm.automation.core.util.Properties namedParameters = new Properties();
        namedParameters.put("ten_user_user", user);

        DocumentModel searchDocumentModel = getSearchDocumentModel(session, pageProviderService,
                "PP_UserTenantConfig", namedParameters);
        Map<String, Serializable> props = new HashMap<>();
        props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY, (Serializable) session);

        PaginableDocumentModelListImpl res = new PaginableDocumentModelListImpl(
                (PageProvider<DocumentModel>) pageProviderService.getPageProvider("PP_UserTenantConfig", searchDocumentModel,
                        null, null, null, props, null, null),
                null);

        if (res.size() > 0) {

            return res.get(0);

        } else{
            return null;
        }*/

    }
    private void writeMultitenantUserProfile(JsonGenerator jg, Map<String,Serializable> up) throws IOException {



       // jg.writeStringField("phonenumber", (String) up.get("phonenumber"));
        jg.writeStringField("fullNameAr", (String) up.get("fullNameAr"));
        jg.writeStringField("jobGrade", (String) up.get("jobGradeEn"));
        jg.writeStringField("jobGradeAr", (String) up.get("jobGradeAr"));

        if (up.get("avatar") != null) {
            jg.writeStringField("avatar", ctx.getBaseUrl() + up.get("avatar"));
        } else {
            jg.writeNullField("avatar");
        }
        if(up.get("disabled")!=null)
        {
            if(up.get("disabled").equals(true)){
                jg.writeBooleanField("disabled", true);
            }

        }
    }
}
