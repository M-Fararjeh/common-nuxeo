package org.nuxeo.ecm.restapi.server.jaxrs.extended;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.OperationDocumentation;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.util.DocumentHelper;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.automation.jaxrs.io.documents.PaginableDocumentModelListImpl;
import org.nuxeo.ecm.automation.server.jaxrs.ResponseHelper;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.impl.SimpleDocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.io.download.DownloadService;
import org.nuxeo.ecm.core.io.marshallers.json.OutputStreamWithJsonWriter;
import org.nuxeo.ecm.core.io.marshallers.json.document.DocumentPropertyJsonWriter;
import org.nuxeo.ecm.core.io.registry.MarshallerRegistry;
import org.nuxeo.ecm.core.io.registry.Writer;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.platform.usermanager.io.NuxeoPrincipalJsonWriter;
import org.nuxeo.ecm.platform.web.common.vh.VirtualHostHelper;
import org.nuxeo.ecm.restapi.server.jaxrs.usermanager.GroupRootObject;
import org.nuxeo.ecm.user.center.profile.UserProfileService;
import org.nuxeo.ecm.webengine.JsonFactoryManager;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.nuxeo.common.utils.DateUtils.toZonedDateTime;
import static org.nuxeo.ecm.core.security.UpdateACEStatusWork.FORMATTER;
import static org.nuxeo.ecm.platform.comment.workflow.utils.CommentsConstants.COMMENT_PARENT_ID;
import static org.nuxeo.ecm.platform.comment.workflow.utils.CommentsConstants.COMMENT_SCHEMA;
import static org.nuxeo.ecm.platform.usermanager.UserManagerImpl.USER_HAS_PARTIAL_CONTENT;
import static org.nuxeo.ecm.user.center.profile.UserProfileConstants.*;
import static org.nuxeo.ecm.user.center.profile.UserProfileConstants.USER_PROFILE_AVATAR_FIELD;
import org.nuxeo.ecm.multi.tenant.MultiTenantService;

@WebObject(type = "group-members")
public class GroupUsersMembers extends GroupRootObject {


    private static final String ENTITY_TYPE = "user";
    @Context
    protected CoreSession session;

    private static final Log log = LogFactory.getLog(GroupUsersMembers.class);
    @Context
    protected HttpServletRequest request;

    @Context
    protected HttpServletResponse response;

    /* @Context
     protected RenderingContext renderingContext;
 */
    protected SchemaManager schemaManager;


    protected MarshallerRegistry registry;


    protected DirectoryService directoryService;


    @GET
    @Path("all-members/{group}")
    @Produces(MediaType.APPLICATION_JSON)
    public Object getGroupMembers(@PathParam("group") String group,@QueryParam("q") String q) throws IOException {

        schemaManager = Framework.getService(SchemaManager.class);
        registry = Framework.getService(MarshallerRegistry.class);

        directoryService = Framework.getService(DirectoryService.class);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        UserProfileService userProfileService = Framework.getService(UserProfileService.class);
        try (JsonGenerator jg = getFactory().createGenerator(out)) {


            // renderingContext = Framework.getService(RenderingContext.class);
            NuxeoGroup parentGroup = um.getGroup(group);

            if (parentGroup == null) {
                return ResponseHelper.notFound();
            }


            jg.writeStartObject();
            List<String> users = CoreInstance.doPrivileged(session, s -> {

                if(!group.endsWith("cts_role_sys_User")){
                    return um.getUsersInGroupAndSubGroups(group);
                }
                else{
                    return um.getUsersInGroup(group);
                }

            });


            //jg.useDefaultPrettyPrinter();
            if (!StringUtils.isEmpty(q) && !q.equals("*")) {

                String finalQ = q.replace("*","");
                users = users.stream().filter(s -> s.startsWith(finalQ)).collect(Collectors.toList());
            }

            jg.writeArrayFieldStart("users");
            for (String user : users) {
                DocumentModel up = null;
                jg.writeStartObject();
                CoreInstance.doPrivileged(session, s -> {
                    NuxeoPrincipal principal = um.getPrincipal(user);
                    try {
                        writePrincipal(jg, principal);
                        writeFullName(jg, principal);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                });




                try {
                    // up = userProfileService.getUserProfileDocument(user, session);
                    Map<String,Serializable> profile = getUserMultitenantProfile(user);
                    if (profile != null) {
                        writeMultitenantUserProfile(jg, profile);

                    }

                } catch (DocumentSecurityException securityException) {
                    securityException.printStackTrace();
                }

                jg.writeEndObject();
            }
            jg.writeEndArray();
            jg.writeEndObject();
        }

        return out.toString("UTF-8");

    }
    private void writeFullName(JsonGenerator jg, NuxeoPrincipal principal) throws IOException {
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
        String firstName="",lastName="";
        for (Property property : properties) {
            String localName = property.getField().getName().getLocalName();

            if (localName.equals("firstName")&& (!StringUtils.isEmpty((String) property.getValue()))) {
                firstName = (String) property.getValue();
            }
            if (localName.equals("lastName")&& (!StringUtils.isEmpty((String) property.getValue()))) {
                lastName = (String) property.getValue();
            }

        }
        jg.writeStringField("fullName",firstName.concat(" ").concat(lastName));

    }
    private void writeMultitenantUserProfile(JsonGenerator jg, Map<String,Serializable> up) throws IOException {
        String baseURL = VirtualHostHelper.getBaseURL(request);
        RenderingContext renderingContext = RenderingContext.CtxBuilder.base(baseURL).get();
        jg.writeStringField("fullNameAr", (String) up.get("fullNameAr"));
        jg.writeStringField("jobGrade", (String) up.get("jobGradeEn"));
        jg.writeStringField("jobGradeAr", (String) up.get("jobGradeAr"));
       // jg.writeStringField("phonenumber", (String) up.get("phonenumber"));

        if (up.get("avatar") != null) {
            jg.writeStringField("avatar", renderingContext.getBaseUrl() + up.get("avatar"));
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

    private static JsonFactory getFactory() {
        return Framework.getService(JsonFactoryManager.class).getJsonFactory();
    }

    protected void writeCompatibilityUserProfile(JsonGenerator jg, DocumentModel up) throws IOException {
        RenderingContext renderingContext = RenderingContext.CtxBuilder.get();
        Serializable propertyValue = up.getPropertyValue(USER_PROFILE_BIRTHDATE_FIELD);
        jg.writeStringField("birthdate",
                propertyValue == null ? null : FORMATTER.format(toZonedDateTime((GregorianCalendar) propertyValue)));
        jg.writeStringField("phonenumber", (String) up.getPropertyValue(USER_PROFILE_PHONENUMBER_FIELD));
        Blob avatar = (Blob) up.getPropertyValue(USER_PROFILE_AVATAR_FIELD);
        if (avatar != null) {
            DownloadService downloadService = Framework.getService(DownloadService.class);
            String url = downloadService.getDownloadUrl(up, USER_PROFILE_AVATAR_FIELD, avatar.getFilename());
            jg.writeStringField("avatar", renderingContext.getBaseUrl() + url);
        } else {
            jg.writeNullField("avatar");
        }
    }

    private void writePrincipal(JsonGenerator jg, NuxeoPrincipal principal) throws IOException {
        RenderingContext renderingContext = RenderingContext.CtxBuilder.get();
        if (principal != null) {
            DocumentModel doc = principal.getModel();
            if (doc == null) {
                return;
            }
            UserManager um = Framework.getService(UserManager.class);
            jg.writeStringField("id", principal.getName());
            DocumentModel model = principal.getModel();
            if (model != null && Boolean.TRUE.equals(model.getContextData(USER_HAS_PARTIAL_CONTENT))) {
                jg.writeBooleanField("isPartial", true);
            }
            jg.writeBooleanField("isAdministrator", principal.isAdministrator());
            jg.writeBooleanField("isAnonymous", principal.isAnonymous());
            String userSchema = um.getUserSchemaName();
            Collection<Property> properties = doc.getPropertyObjects(userSchema);
            if (properties.isEmpty()) {
                return;
            }
            Writer<Property> propertyWriter = registry.getWriter(renderingContext, Property.class, APPLICATION_JSON_TYPE);
            jg.writeObjectFieldStart("properties");
            for (Property property : properties) {
                String localName = property.getField().getName().getLocalName();
                if (!localName.equals(getPasswordField())) {
                    jg.writeFieldName(localName);
                    OutputStream out = new OutputStreamWithJsonWriter(jg);
                    propertyWriter.write(property, Property.class, Property.class, APPLICATION_JSON_TYPE, out);
                }
            }
            jg.writeEndObject();
        }
    }


    private String getPasswordField() {
        String userDirectoryName = um.getUserDirectoryName();
        return directoryService.getDirectory(userDirectoryName).getPasswordField();
    }


    protected void writeUserProfile(JsonGenerator jg, DocumentModel up) throws IOException {
        RenderingContext renderingContext = RenderingContext.CtxBuilder.get();
        Writer<Property> propertyWriter = registry.getWriter(renderingContext, Property.class, APPLICATION_JSON_TYPE);
        Schema schema = schemaManager.getSchema(USER_PROFILE_SCHEMA);
        // provides the user profile document to the property marshaller
        try (Closeable resource = renderingContext.wrap().with(ENTITY_TYPE, up).open()) {
            for (Field field : schema.getFields()) {
                jg.writeFieldName(field.getName().getLocalName());
                Property property = up.getProperty(field.getName().getPrefixedName());
                OutputStream out = new OutputStreamWithJsonWriter(jg);
                propertyWriter.write(property, Property.class, Property.class, APPLICATION_JSON_TYPE, out);
            }
        }

    }

    private Map<String,Serializable> getUserMultitenantProfile(String user)
    {

        return CoreInstance.doPrivileged(session, s -> {
                    try {
                        Map<String,Serializable> profile = new HashMap<String,Serializable>();

                        boolean tx = TransactionHelper.startTransaction();


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

                            //get current tenant
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



                            TransactionHelper.commitOrRollbackTransaction();
                            return profile;
                        } else {
                            TransactionHelper.commitOrRollbackTransaction();
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

    protected DocumentModel getSearchDocumentModel(CoreSession session, PageProviderService pps, String providerName,
                                                   Properties namedParameters) {
        // generate search document model if type specified on the definition
        DocumentModel searchDocumentModel = null;
        if (!StringUtils.isBlank(providerName)) {
            PageProviderDefinition pageProviderDefinition = pps.getPageProviderDefinition(providerName);
            if (pageProviderDefinition != null) {
                String searchDocType = pageProviderDefinition.getSearchDocumentType();
                if (searchDocType != null) {
                    searchDocumentModel = session.createDocumentModel(searchDocType);
                } else if (pageProviderDefinition.getWhereClause() != null) {
                    // avoid later error on null search doc, in case where clause is only referring to named parameters
                    // (and no namedParameters are given)
                    searchDocumentModel = new SimpleDocumentModel();
                }
            } else {
                log.error("No page provider definition found for " + providerName);
            }
        }

        if (namedParameters != null && !namedParameters.isEmpty()) {
            // fall back on simple document if no type defined on page provider
            if (searchDocumentModel == null) {
                searchDocumentModel = new SimpleDocumentModel();
            }
            for (Map.Entry<String, String> entry : namedParameters.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                try {
                    DocumentHelper.setProperty(session, searchDocumentModel, key, value, true);
                } catch (PropertyNotFoundException | IOException e) {
                    // assume this is a "pure" named parameter, not part of the search doc schema
                    continue;
                }
            }
            searchDocumentModel.putContextData(PageProviderService.NAMED_PARAMETERS, namedParameters);
        }
        return searchDocumentModel;
    }
}
