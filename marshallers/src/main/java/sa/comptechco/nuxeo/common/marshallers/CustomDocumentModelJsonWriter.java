package sa.comptechco.nuxeo.common.marshallers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracing;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.jetbrains.annotations.Nullable;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.util.JSONPropertyWriter;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.io.marshallers.json.OutputStreamWithJsonWriter;
import org.nuxeo.ecm.core.io.marshallers.json.document.DocumentModelJsonWriter;
import org.nuxeo.ecm.core.io.marshallers.json.enrichers.Enriched;
import org.nuxeo.ecm.core.io.registry.Writer;
import org.nuxeo.ecm.core.io.registry.context.MaxDepthReachedException;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.core.io.registry.context.WrappedContext;
import org.nuxeo.ecm.core.io.registry.reflect.Instantiations;
import org.nuxeo.ecm.core.io.registry.reflect.Priorities;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.utils.DateParser;
import org.nuxeo.ecm.multi.tenant.MultiTenantService;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.nuxeo.common.utils.DateUtils.formatISODateTime;
import static org.nuxeo.common.utils.DateUtils.nowIfNull;
import static org.nuxeo.ecm.core.io.marshallers.json.enrichers.AbstractJsonEnricher.ENTITY_ENRICHER_NAME;
import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.ENTITY_FIELD_NAME;
import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.WILDCARD_VALUE;

@Setup(mode = Instantiations.SINGLETON, priority = Priorities.OVERRIDE_REFERENCE) // <= an higher priority is used
public class CustomDocumentModelJsonWriter extends DocumentModelJsonWriter {
    public static final String ENTITY_TYPE = "document";
    String CONTEXT_PATH = "/nuxeo";
    String CONTEXT_PATH_PROP = "org.nuxeo.ecm.contextPath";

    @Override
    protected void writeEntityBody(DocumentModel doc, JsonGenerator jg) throws IOException {
        if( null != ctx.getParameter("pageProvider") && ctx.getParameter("pageProvider").toString().startsWith("PP_Department") && doc.getType().equals("Department"))
        {

            jg.writeStringField("uid", doc.getId());
            jg.writeStringField("title", doc.getTitle());
            jg.writeObjectFieldStart("properties");
            jg.writeStringField("dept:arabicName", (String) doc.getPropertyValue("dept:arabicName"));
            jg.writeStringField("dept:englishName", (String) doc.getPropertyValue("dept:englishName"));
            jg.writeStringField("dc:title", (String) doc.getPropertyValue("dc:title"));
            jg.writeStringField("dept:parentDepartmentCode", (String) doc.getPropertyValue("dept:parentDepartmentCode"));
            jg.writeEndObject();

        }
        //PP_HubCorrespondences_Elastic
        //PP_HubTargetResponse
        else if ( null != ctx.getParameter("pageProvider") &&(
                ctx.getParameter("pageProvider").toString().startsWith("PP_HubCorrespondences_Search_Elastic")|| ctx.getParameter("pageProvider").toString().startsWith("PP_HubCorrespondences"))&&
                (doc.getType().equals("IncomingCorrespondence")||doc.getType().equals("OutgoingCorrespondence"))) {
            jg.writeStringField("uid", doc.getId());
            //jg.writeStringField("title", doc.getTitle());
            jg.writeObjectFieldStart("properties");
            if(doc.getType().equals("IncomingCorrespondence"))
            {
                jg.writeStringField("corr:externalCorrespondenceNumber", (String) doc.getPropertyValue("corr:externalCorrespondenceNumber"));
                jg.writeStringField("corr:fromAgency", (String) doc.getPropertyValue("corr:fromAgency"));
                jg.writeStringField("hub:receiverDepartmentNameAr", (String) doc.getPropertyValue("hub:receiverDepartmentNameAr"));
                jg.writeStringField("hub:receiverDepartmentNameEn", (String) doc.getPropertyValue("hub:receiverDepartmentNameEn"));
                jg.writeStringField("hub:senderAgencyNameAr", (String) doc.getPropertyValue("hub:senderAgencyNameAr"));
                jg.writeStringField("hub:senderAgencyNameEn", (String) doc.getPropertyValue("hub:senderAgencyNameEn"));

                Calendar cal = (Calendar) doc.getPropertyValue("dc:created");
                if (cal != null) {
                    jg.writeStringField("dc:created", DateParser.formatW3CDateTime(cal.getTime()));
                }//jg.writeStringField("dc:created", (String) doc.getPropertyValue("dc:created"));
            }
            else{
                String outgoingAgency = (String) doc.getPropertyValue("corr:toAgency");

                if(StringUtils.isEmpty(outgoingAgency)) {
                    JSONPropertyWriter writer = JSONPropertyWriter.create().writeNull(false).writeEmpty(false).prefix("out_corr");
                    writer.writeProperty(jg,doc.getProperty("out_corr:multiRecivers"));
                }

                else{
                    jg.writeStringField("corr:toAgency", (String) doc.getPropertyValue("corr:toAgency"));
                }
                //jg.writeStringField("hubCorr:createdAt", (String) doc.getPropertyValue("hubCorr:createdAt"));
                if(doc.getProperty("hubCorr:createdAt")!= null) {
                    Calendar cal = (Calendar) doc.getPropertyValue("hubCorr:createdAt");
                    if (cal != null) {
                        jg.writeStringField("hubCorr:createdAt", DateParser.formatW3CDateTime(cal.getTime()));
                    }
                }
                jg.writeStringField("hubCorr:status", (String) doc.getPropertyValue("hubCorr:status"));
            }
            jg.writeStringField("corr:from", (String) doc.getPropertyValue("corr:from"));
            jg.writeStringField("corr:referenceNumber", (String) doc.getPropertyValue("corr:referenceNumber"));

            jg.writeStringField("corr:to", (String) doc.getPropertyValue("corr:to"));
            jg.writeStringField("corr:priority", (String) doc.getPropertyValue("corr:priority"));

            jg.writeEndObject();
        }
        else if ( null != ctx.getParameter("pageProvider") &&(
                ctx.getParameter("pageProvider").toString().startsWith("PP_HubTargetResponse")||
                        ctx.getParameter("pageProvider").toString().startsWith("PP_HubTargets"))&&
                doc.getType().equals("TargetResponse")) {
            jg.writeStringField("uid", doc.getId());
            jg.writeStringField("title", doc.getTitle());
            jg.writeObjectFieldStart("properties");
            jg.writeStringField("targetRes:tarCode", (String) doc.getPropertyValue("targetRes:tarCode"));
            jg.writeStringField("targetRes:status", (String) doc.getPropertyValue("targetRes:status"));
            jg.writeStringField("cts_common:importance", (String) doc.getPropertyValue("cts_common:importance"));
            jg.writeStringField("cts_common:referenceNumber", (String) doc.getPropertyValue("cts_common:referenceNumber"));
            jg.writeStringField("cts_common:from", (String) doc.getPropertyValue("cts_common:from"));
 //           jg.writeStringField("targetRes:receivingDate", (String) doc.getPropertyValue("targetRes:receivingDate"));
            if(doc.getProperty("targetRes:receivingDate")!= null) {
                Calendar cal = (Calendar) doc.getPropertyValue("targetRes:receivingDate");
                if (cal != null) {
                    jg.writeStringField("targetRes:receivingDate", DateParser.formatW3CDateTime(cal.getTime()));
                }
            }

            if(doc.getPropertyValue("targetRes:hold")!=null) {
                jg.writeBooleanField("targetRes:hold", (Boolean) doc.getPropertyValue("targetRes:hold") );
            }
                else {
                jg.writeBooleanField("targetRes:hold", false);
            }
            jg.writeEndObject();
        }
         else {
            super.writeEntityBody(doc, jg);
        }
    }

    @Override
    public void write(DocumentModel entity, JsonGenerator jg) throws IOException {
        if( null != ctx.getParameter("pageProvider") && ctx.getParameter("pageProvider").toString().startsWith("PP_Department") && entity.getType().equals("Department"))
        {
            Span span = Tracing.getTracer().getCurrentSpan();
            span.addAnnotation("json#write " + ENTITY_TYPE);
            jg.writeStartObject();
            List<Object> entityList = new ArrayList<>();
            entityList.add(ENTITY_TYPE);
            ctx.addParameterListValues(RenderingContext.RESPONSE_HEADER_ENTITY_TYPE_KEY, entityList);
            jg.writeStringField(ENTITY_FIELD_NAME, ENTITY_TYPE);
            writeEntityBody(entity, jg);
            try {
                WrappedContext wrappedCtx = ctx.wrap();//.controlDepth();
                Set<String> enrichers = ctx.getEnrichers(ENTITY_TYPE);
                if (enrichers.size() > 0) {
                    boolean hasEnrichers = false;
                    Enriched<DocumentModel> enriched = null;
                    for (String enricherName : enrichers) {
                        span.addAnnotation("json#write " + ENTITY_TYPE + ".enricher." + enricherName);
                        try (Closeable resource = wrappedCtx.with(ENTITY_ENRICHER_NAME, enricherName).open()) {
                            @SuppressWarnings("rawtypes")
                            Collection<Writer<Enriched>> writers = registry.getAllWriters(ctx, Enriched.class,
                                    TypeUtils.parameterize(Enriched.class, DocumentModel.class), APPLICATION_JSON_TYPE);
                            for (@SuppressWarnings("rawtypes")
                            Writer<Enriched> writer : writers) {
                                if (!hasEnrichers) {
                                    hasEnrichers = true;
                                    jg.writeObjectFieldStart("contextParameters");
                                    enriched = new Enriched<>(entity);
                                }
                                OutputStreamWithJsonWriter out = new OutputStreamWithJsonWriter(jg);
                                writer.write(enriched, Enriched.class, TypeUtils.parameterize(Enriched.class, DocumentModel.class), APPLICATION_JSON_TYPE, out);
                            }
                        }
                    }
                    if (hasEnrichers) {
                        jg.writeEndObject();
                    }
                }
            } catch (Exception e) {
                // do nothing, do not call enrichers
            }
            extend(entity, jg);
            jg.writeEndObject();
            span.addAnnotation("json#write " + ENTITY_TYPE + ".done");
        }
        else {
            super.write(entity, jg);
        }

    }

    @Override
    protected void extend(DocumentModel document, JsonGenerator jg) throws IOException {
   /*     NuxeoPrincipal principal = null;
        CoreSession session = null;
        try {
            session = ctx.getSession(document).getSession();
            principal = session.getPrincipal();
        } catch (Exception e) {
            // TODO: handle exception
        }
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
//        System.out.println("=========================== [72] CustomDocumentModelJsonWriter:: Referer: " + referer + " ===========================================");

        if (urlPattern.matcher(referer).matches() && principal != null && !principal.isAdministrator() && document != null) {
            // TODO: fix empty list item caused by invalid  PaginableDocumentModelList properties after deleting docs
            String docPath = document.getPath() != null ? document.getPath().toString() : "";
            if (departmentsHierarchyPattern.matcher(docPath).matches()
                    || ctsPattern.matcher(docPath).matches()
                    || meetingManagementPattern.matcher(docPath).matches()) {
                document = session.createDocumentModel(document.getType());
                document.setPropertyValue("dc:title", "Hidden Content");
            }
        }*/
        super.extend(document, jg);

        if (document.getId() != null && document.getDocumentType() != null) {
            if(document.isLocked())
            {
                UserManager um = Framework.getService(UserManager.class);

                CoreSession session = ctx.getSession(null).getSession();
                Boolean isPrincipal= CoreInstance.doPrivileged(session, s -> {
                    NuxeoPrincipal principalUser = um.getPrincipal(document.getLockInfo().getOwner());
                    if(principalUser!=null) {
                        try {

                            // if multitenat active return ar name also
                            MultiTenantService multiTenantService= Framework.getService(MultiTenantService.class);
                            if(multiTenantService.isTenantIsolationEnabled(session)) {
                                Map<String,String> profile = getMultitenantProfileArName(principalUser.getName());

                                jg.writeStringField("lockOwnerFullNameAr", profile.get("arName"));
                                jg.writeStringField("lockOwnerFullName", profile.get("enName"));
                            }
                            else {
                                String firstName=principalUser.getFirstName().isEmpty()?"":principalUser.getFirstName();
                                String lastName=principalUser.getFirstName().isEmpty()?"":principalUser.getLastName();
                                jg.writeStringField("lockOwnerFullName", firstName.concat(" ").concat(lastName));

                            }

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

            }

            if (("GeneralDocument".equals(document.getDocumentType().getName())) || ("GeneralDocument".equals(document.getDocumentType().getSuperType().getName()))) {
                OperationContext operationContext = new OperationContext(ctx.getSession(null).getSession());
                DocumentModel department = getDepartment(operationContext, document);
                if (department != null) {
                    jg.writeObjectFieldStart("department");
                    jg.writeStringField("title", department.getTitle());
                    jg.writeStringField("arabicName", toString(department, "osdept:arabicName"));
                    jg.writeStringField("englishName", toString(department, "osdept:englishName"));
                    jg.writeEndObject();
                } else {
                    jg.writeObjectFieldStart("department");
                    jg.writeEndObject();
                }


                DocumentModel category = getCategory(operationContext, document);
                if (category != null) {
                    jg.writeObjectFieldStart("category");
                    jg.writeStringField("englishName", category.getTitle());
                    jg.writeStringField("arabicName", toString(category, "category:arabicTitle"));
                    jg.writeStringField("code", toString(category, "category:code"));
                    jg.writeEndObject();
                } else {
                    jg.writeObjectFieldStart("category");
                    jg.writeEndObject();
                }

                DocumentModel categoryType = getCategoryType(operationContext, document);
                if (categoryType != null) {
                    jg.writeObjectFieldStart("categoryType");
                    jg.writeStringField("englishName", categoryType.getTitle());
                    jg.writeStringField("arabicName", toString(categoryType, "category:arabicTitle"));
                    jg.writeStringField("code", toString(categoryType, "category:code"));
                    jg.writeEndObject();
                } else {
                    jg.writeObjectFieldStart("categoryType");
                    jg.writeEndObject();
                }


                DocumentModel mainTopic = getMainTopic(operationContext, document);

                if (mainTopic != null) {
                    jg.writeObjectFieldStart("mainTopic");
                    jg.writeStringField("englishName", mainTopic.getTitle());
                    jg.writeStringField("arabicName", toString(mainTopic, "category:arabicTitle"));
                    jg.writeStringField("code", toString(mainTopic, "category:code"));
                    jg.writeEndObject();
                } else {
                    jg.writeObjectFieldStart("mainTopic");
                    jg.writeEndObject();
                }

                DocumentModel documentType = getDocumentType(operationContext, document);
                if (documentType != null) {
                    // jg.writeFieldName("");
                    jg.writeObjectFieldStart("documentType");
                    jg.writeStringField("englishTitle", documentType.getTitle());
                    jg.writeStringField("arabicName", toString(documentType, "documenttypee:arabicTitle"));
                    jg.writeStringField("code", toString(documentType, "documenttypee:code"));
                    // getting save type (property does not exist in all projects so here we catching the exception)
                    try{
                        String saveType= (String) documentType.getPropertyValue("documenttypee:saveTypee");
                        jg.writeStringField("saveType",saveType);
                    }
                    catch ( PropertyNotFoundException e)
                    {
                        // e.printStackTrace();
                        // do nothing
                    }
                    jg.writeEndObject();
                } else {
                    jg.writeObjectFieldStart("documentType");
                    jg.writeEndObject();
                }
            }

        }
    }

    private Map<String, String> getMultitenantProfileArName(String user) {
        CoreSession session = ctx.getSession(null).getSession();
        return CoreInstance.doPrivileged(session, s -> {
            try {
                String fullNameAr="";
                String fullName="";
                Map<String,String> profile = new HashMap<String,String>();
                //        boolean tx = TransactionHelper.startTransaction();


                StringBuilder sb = new StringBuilder();
                sb.append("SELECT * FROM UserTenantConfig WHERE ");
                sb.append("tenus:user");
                sb.append(" = ");
                sb.append(NXQL.escapeString(user));
                String q = sb.toString();

                DocumentModelList list = s.query(q);

                if (!CollectionUtils.isEmpty(list)) {
                    String firstNameAr= (String) list.get(0).getPropertyValue("tenus:firstNameAr");
                    String firstLastAr= (String) list.get(0).getPropertyValue("tenus:lastNameAr");
                    fullNameAr= String.format("%s %s", firstNameAr, firstLastAr);

                    String firstName= (String) list.get(0).getPropertyValue("tenus:firstNameEn");
                    String firstLast= (String) list.get(0).getPropertyValue("tenus:lastNameEn");
                    fullName= String.format("%s %s", firstName, firstLast);
                    //          TransactionHelper.commitOrRollbackTransaction();
                    profile.put("arName",fullNameAr);
                    profile.put("enName",fullName);
                    return profile;
                } else {
                    //        TransactionHelper.commitOrRollbackTransaction();
                    return profile;
                }

            } catch (QueryParseException qe) {
                TransactionHelper.commitOrRollbackTransaction();
                return null;
            }
        });

    }


    private String toString(DocumentModel documentModel, String propertyName) {
        if (documentModel == null) {
            return null;
        }
        if (StringUtils.isEmpty(propertyName)) {
            return null;
        }
        String result = String.valueOf(documentModel.getPropertyValue(propertyName));
        return result;
    }

    private DocumentModel getDepartment(OperationContext operationContext, DocumentModel document) {
        ObjectMapper o = new ObjectMapper();

        if (document.getPropertyValue("gdoc:departmentCode") != null) {
            String code = document.getPropertyValue("gdoc:departmentCode").toString();
            if (!StringUtils.isEmpty(code)) {
                AutomationService automationService = Framework.getService(AutomationService.class);
                try {
                    StringList values = new StringList();
                    values.add(code);
                    Map<String, Serializable> params = new HashMap<>();
                    params.put("query", "ecm:mixinType != 'HiddenInNavigation' AND ecm:isVersion = 0 AND ecm:isTrashed = 0 AND ecm:primaryType = 'OSDepartment' AND ecm:currentLifeCycleState != 'obsolete' ");
                    params.put("property", "dc:title");
                    params.put("values", values);

                    DocumentModelList result = (DocumentModelList) automationService.run(operationContext, "Document.FetchByProperty", params);
                    if (CollectionUtils.isEmpty(result)) {
                        return null;
                    } else {
                        return result.get(0);
                    }

                } catch (OperationException e) {
                    e.printStackTrace();
                    return null;
                }
            } else return null;
        }

        return null;
    }


    private DocumentModel getCategory(OperationContext operationContext, DocumentModel document) {
        ObjectMapper o = new ObjectMapper();

        if (document.getPropertyValue("gdoc:categoryCode") != null) {
            String code = document.getPropertyValue("gdoc:categoryCode").toString();
            return getCategoryByCode(operationContext, code);
        }

        return null;
    }

    private DocumentModel getCategoryType(OperationContext operationContext, DocumentModel document) {
        ObjectMapper o = new ObjectMapper();

        if (("GeneralDocument".equals(document.getDocumentType().getName())) || ("GeneralDocument".equals(document.getDocumentType().getSuperType().getName()))) {
            if (document.getPropertyValue("gdoc:categoryTypeCode") != null) {
                String code = document.getPropertyValue("gdoc:categoryTypeCode").toString();
                return getCategoryByCode(operationContext, code);
            }
        } else
            return null;
        return null;
    }


    private DocumentModel getMainTopic(OperationContext operationContext, DocumentModel document) {
        ObjectMapper o = new ObjectMapper();

        if (("GeneralDocument".equals(document.getDocumentType().getName())) || ("GeneralDocument".equals(document.getDocumentType().getSuperType().getName()))) {
            if (document.getPropertyValue("gdoc:mainTopicCode") != null) {
                String code = document.getPropertyValue("gdoc:mainTopicCode").toString();
                return getCategoryByCode(operationContext, code);
            }
        } else
        {
            return null;
        }
        return null;
    }

    @Nullable
    private DocumentModel getCategoryByCode(OperationContext operationContext, String code) {
        if (!StringUtils.isEmpty(code)) {
            AutomationService automationService = Framework.getService(AutomationService.class);
            try {
                StringList values = new StringList();
                values.add(code);
                Map<String, Serializable> params = new HashMap<>();
                params.put("query", "ecm:mixinType != 'HiddenInNavigation' AND ecm:isVersion = 0 AND ecm:isTrashed = 0 AND ecm:primaryType = 'Category' ");
                params.put("property", "category:code");
                params.put("values", values);

                DocumentModelList result = (DocumentModelList) automationService.run(operationContext, "Document.FetchByProperty", params);
                if (CollectionUtils.isEmpty(result)) {
                    return null;
                } else {
                    return result.get(0);
                }

            } catch (OperationException e) {
                e.printStackTrace();
                return null;
            }


        } else {
            return null;
        }
    }


    private DocumentModel getDocumentType(OperationContext operationContext, DocumentModel document) {

        if (document.getPropertyValue("gdoc:documentTypeCode") != null) {
            String code = document.getPropertyValue("gdoc:documentTypeCode").toString();
            if (!StringUtils.isEmpty(code)) {
                AutomationService automationService = Framework.getService(AutomationService.class);
                try {
                    StringList values = new StringList();
                    values.add(code);
                    Map<String, Serializable> params = new HashMap<>();
                    params.put("query", "ecm:mixinType != 'HiddenInNavigation' AND ecm:isVersion = 0 AND ecm:isTrashed = 0 AND ecm:primaryType = 'DocumentTypee'");
                    params.put("property", "documenttypee:code");
                    params.put("values", values);

                    DocumentModelList result = (DocumentModelList) automationService.run(operationContext, "Document.FetchByProperty", params);
                    if (CollectionUtils.isEmpty(result)) {
                        return null;
                    } else {
                        return result.get(0);
                    }

                } catch (OperationException e) {
                    e.printStackTrace();
                    return null;
                }



            }
        } else
        {
            return null;
        }
        return null;
    }



}
