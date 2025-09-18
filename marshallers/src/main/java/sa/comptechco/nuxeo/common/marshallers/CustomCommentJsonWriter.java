package sa.comptechco.nuxeo.common.marshallers;

import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.core.util.PageProviderHelper;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.automation.jaxrs.io.documents.PaginableDocumentModelListImpl;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.io.download.DownloadService;
import org.nuxeo.ecm.core.io.marshallers.json.OutputStreamWithJsonWriter;
import org.nuxeo.ecm.core.io.registry.Writer;
import org.nuxeo.ecm.core.io.registry.reflect.Instantiations;
import org.nuxeo.ecm.core.io.registry.reflect.Priorities;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.multi.tenant.MultiTenantService;
import org.nuxeo.ecm.platform.comment.api.Comment;
import org.nuxeo.ecm.platform.comment.impl.CommentJsonWriter;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.query.api.QuickFilter;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.user.center.profile.UserProfileService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.services.config.ConfigurationService;
import org.nuxeo.runtime.transaction.TransactionHelper;

import javax.inject.Inject;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.*;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.nuxeo.common.utils.DateUtils.toZonedDateTime;
import static org.nuxeo.ecm.core.io.marshallers.json.document.DocumentModelJsonWriter.ENTITY_TYPE;
import static org.nuxeo.ecm.core.security.UpdateACEStatusWork.FORMATTER;
import static org.nuxeo.ecm.user.center.profile.UserProfileConstants.*;


@Setup(mode = Instantiations.SINGLETON, priority = Priorities.OVERRIDE_REFERENCE) // <= an higher priority is used
public class CustomCommentJsonWriter extends CommentJsonWriter {

    @Inject
    protected SchemaManager schemaManager;

    @Inject
    protected DownloadService downloadService;

    @Inject
    protected UserProfileService userProfileService;

    @Inject
    protected ConfigurationService configurationService;

    @Inject
    private DirectoryService directoryService;
    private UserManager um;

    private PageProviderService pageProviderService;

    protected void extend(Comment document, JsonGenerator jg) throws IOException {
        um = Framework.getService(UserManager.class);
        super.extend(document, jg);
        CoreSession session = ctx.getSession(null).getSession();
        NuxeoPrincipal principal = um.getPrincipal(document.getAuthor());


        //DocumentModel up = userProfileService.getUserProfileDocument(document.getAuthor(), session);
        jg.writeFieldName("Commenter");

       // jg.writeStartObject();
        Boolean isPrincipal= CoreInstance.doPrivileged(session, s -> {
            NuxeoPrincipal principalUser = um.getPrincipal(document.getAuthor());
            if(principalUser!=null) {
                try {
                    jg.writeStartObject();
                    writePrincipal(jg, principalUser);

                } catch (IOException e) {
                    try {
                        jg.writeEndObject();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                    throw new RuntimeException(e);
                }
                return true;
            }
            else {
                return false;
            }


        });
        if (isPrincipal) {
            try {
                // up = userProfileService.getUserProfileDocument(user, session);
                Map<String, Serializable> profile = getUserMultitenantProfile(document.getAuthor());
                if (profile != null) {
                    writeMultitenantUserProfile(jg, profile);
                }

            } catch (DocumentSecurityException securityException) {
                securityException.printStackTrace();
            }

            jg.writeEndObject();
        }
        else
        {
            jg.writeEndObject();
        }
        //if (configurationService.isBooleanPropertyTrue(COMPATIBILITY_CONFIGURATION_PARAM)) {
        //    writeCompatibilityUserProfile(jg, up);
        //} else {
       /* if (principal != null) {
            writePrincipal(jg, principal);
            writeCompatibilityUserProfile(jg, up);
        }*/
        //}
       // jg.writeEndObject();

        // writeAttachments(document.getId(),jg);


    }

    private void writeAttachments(String documentId, JsonGenerator jg) throws IOException {

        pageProviderService = Framework.getService(PageProviderService.class);
        List<QuickFilter> quickFilters = new ArrayList<QuickFilter>();
        PageProviderDefinition pageProviderDefinition = pageProviderService.getPageProviderDefinition("PP_Document_Attachments");
        List<QuickFilter> ppQuickFilters = pageProviderDefinition.getQuickFilters();

        for (QuickFilter quickFilter : ppQuickFilters) {
            if (quickFilter.getName().equals("Comment")) {
                quickFilters.add(quickFilter);
                break;
            }
        }
        Properties namedParameters = new Properties();
        namedParameters.put("ct_attachment_docRefId", documentId);
        Map<String, Serializable> props = new HashMap<>();
        CoreSession session = ctx.getSession(null).getSession();
        props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY, (Serializable) session);
        PaginableDocumentModelListImpl res;


        DocumentModel searchDocumentModel = PageProviderHelper.getSearchDocumentModel(session,
                "PP_Document_Attachments", namedParameters);

        res = new PaginableDocumentModelListImpl(
                (PageProvider<DocumentModel>) pageProviderService.getPageProvider("PP_Document_Attachments", searchDocumentModel,
                        null, null, null, props, quickFilters),
                null);

        if (res.hasError()) {
            // throw new NuxeoException(res.getErrorMessage(), SC_BAD_REQUEST);
        } else {

            jg.writeArrayFieldStart("attachments");
            for (DocumentModel documentModel : res) {
                jg.writeStartObject();
                jg.writeStringField("id", documentModel.getId());
                jg.writeStringField("name", documentModel.getTitle());
                jg.writeEndObject();
            }
            jg.writeEndArray();
        }

    }

    protected DocumentModelList queryByPageProvider(String pageProviderName, Long pageSize, Long currentPageIndex,
                                                    Long currentPageOffset, List<SortInfo> sortInfo, List<String> highlights, List<QuickFilter> quickFilters,
                                                    Map<String, Serializable> props, DocumentModel searchDocumentModel, Object... parameters) {
        PaginableDocumentModelListImpl res = new PaginableDocumentModelListImpl(
                (PageProvider<DocumentModel>) pageProviderService.getPageProvider(pageProviderName, searchDocumentModel,
                        sortInfo, pageSize, currentPageIndex, currentPageOffset, props, highlights, quickFilters,
                        null),
                null);
        if (res.hasError()) {
            throw new NuxeoException(res.getErrorMessage(), SC_BAD_REQUEST);
        }
        return res;
    }

    private void writePrincipal(JsonGenerator jg, NuxeoPrincipal principal) throws IOException {
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
        String firstName = "", lastName = "";
        for (Property property : properties) {
            String localName = property.getField().getName().getLocalName();
            if (!localName.equals(getPasswordField())) {
                if (localName.equals("firstName") && (!StringUtils.isEmpty((String) property.getValue()))) {
                    firstName = (String) property.getValue();
                }
                if (localName.equals("lastName") && (!StringUtils.isEmpty((String) property.getValue()))) {
                    lastName = (String) property.getValue();
                }
                jg.writeFieldName(localName);
                OutputStream out = new OutputStreamWithJsonWriter(jg);
                propertyWriter.write(property, Property.class, Property.class, APPLICATION_JSON_TYPE, out);
            }
        }
        jg.writeEndObject();
        jg.writeStringField("fullName", firstName.concat(" ").concat(lastName));
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
    private Map<String,Serializable> getUserMultitenantProfile(String user) {
        CoreSession session = ctx.getSession(null).getSession();

        return CoreInstance.doPrivileged(session, s -> {
            try {
                Map<String, Serializable> profile = new HashMap<String, Serializable>();

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
                        if (session.getPrincipal().isAdministrator() || session.getPrincipal().getTenantId() == null) {
                            //profile.put("disabled", Boolean.TRUE);
                        } else {
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
