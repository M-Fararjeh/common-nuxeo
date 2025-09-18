package sa.comptechco.nuxeo.common.marshallers;

import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.*;
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
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.platform.usermanager.io.NuxeoPrincipalJsonWriter;
import org.nuxeo.ecm.platform.web.common.vh.VirtualHostHelper;
import org.nuxeo.ecm.user.center.profile.UserProfileService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

import javax.inject.Inject;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.nuxeo.common.utils.DateUtils.toZonedDateTime;
import static org.nuxeo.ecm.core.security.UpdateACEStatusWork.FORMATTER;
import static org.nuxeo.ecm.user.center.profile.UserProfileConstants.*;
import org.nuxeo.ecm.multi.tenant.MultiTenantService;
@Setup(mode = Instantiations.SINGLETON, priority = Priorities.OVERRIDE_REFERENCE) // <= an higher priority is used
public class CustomPrincipalJsonWriter extends NuxeoPrincipalJsonWriter {

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


    protected void extend(NuxeoPrincipal principal , JsonGenerator jg) throws IOException {
        super.extend(principal, jg);

        CoreSession session = ctx.getSession(null).getSession();
        if(principal != null && session != null && !StringUtils.isEmpty(principal.getName())) {
            final UserManager um = Framework.getService(UserManager.class);
            final  NuxeoPrincipal principalUser = um.getPrincipal(principal.getName());
            if(principal!=null){
                DocumentModel up = null;
                String excludeUserInfos = ctx.getParameter("exclude-user-info");
                Boolean showGroups=true;
                if(!StringUtils.isEmpty(excludeUserInfos)) {
                    List<String> excludeUserInfosList = Stream.of(excludeUserInfos.trim().split("\\s*,\\s*"))
                            .collect(Collectors.toList());

                    if (excludeUserInfosList.contains("group"))
                        showGroups=false;
                }
                if(showGroups.equals(true))
                {
                    writeExtendedGroups(jg, principal);
                }


                try {
                    // up = userProfileService.getUserProfileDocument(user, session);
                    Map<String,Serializable> profile = getUserMultitenantProfile(principal.getName());
                    if (profile != null) {
                        writeMultitenantUserProfile(jg, profile);
                    }

                } catch (DocumentSecurityException securityException) {
                    securityException.printStackTrace();
                }

                /*try {
                     up = userProfileService.getUserProfileDocument(principal.getName(), session);

                }catch (DocumentSecurityException securityException) {
                   *//* up = Framework.doPrivileged(() -> {
                        try {
                            DocumentModel userProfile = userProfileService.getUserProfileDocument(principal.getName(), session);
                            return userProfile;
                        } catch (Exception e) {
                            e.printStackTrace();
                            return null;
                        }

                    });*//*

                    securityException.printStackTrace();
                }
                if(up != null)
                {
                    writeCompatibilityUserProfile(jg, up);
                }*/
                writeFullName(jg, principal);

                    //session.getPrincipal().getTenantId();
                    /*Framework.doPrivileged(() -> {
                        DocumentModel up = userProfileService.getUserProfileDocument(principal.getName(), session);
                        writeCompatibilityUserProfile(jg, up);
                        writeExtendedGroups(jg, principalUser);
                        writeFullName(jg, principalUser);
                    });*/

                }
            }
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

    private void writeExtendedGroups(JsonGenerator jg, NuxeoPrincipal principal) throws IOException {
        UserManager um = Framework.getService(UserManager.class);
        principal = um.getPrincipal(principal.getName());
        if(principal != null) {
            jg.writeArrayFieldStart("userGroups");
            for (String strGroup : principal.getAllGroups()) {
                NuxeoGroup group = um.getGroup(strGroup);
                String label = group == null ? strGroup : group.getLabel();
                jg.writeStartObject();
                jg.writeStringField("name", strGroup);
                jg.writeStringField("label", label);
                jg.writeStringField("url", "group/" + strGroup);
                jg.writeEndObject();
            }
            jg.writeEndArray();
        }
    }

    protected void writeCompatibilityUserProfile(JsonGenerator jg, DocumentModel up) throws IOException {
        Serializable propertyValue = up == null ? null : up.getPropertyValue(USER_PROFILE_BIRTHDATE_FIELD);
        jg.writeStringField("birthdate",
                propertyValue == null ? null : FORMATTER.format(toZonedDateTime((GregorianCalendar) propertyValue)));
        jg.writeStringField("phonenumber", up == null ? null : (String) up.getPropertyValue(USER_PROFILE_PHONENUMBER_FIELD));
        Blob avatar = up == null ? null : (Blob) up.getPropertyValue(USER_PROFILE_AVATAR_FIELD);
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
