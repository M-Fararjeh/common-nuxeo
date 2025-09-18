package org.nuxeo.ecm.restapi.server.jaxrs.extended;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.util.DocumentHelper;
import org.nuxeo.ecm.automation.core.util.PageProviderHelper;
import org.nuxeo.ecm.automation.core.util.PaginableDocumentModelList;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.automation.jaxrs.io.documents.PaginableDocumentModelListImpl;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.api.impl.SimpleDocumentModel;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.platform.el.ELService;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.restapi.server.jaxrs.QueryObject;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.DefaultObject;
import org.nuxeo.extended.PaginableTaskList;
import org.nuxeo.extended.utils.PaginableTaskMapper;
import org.nuxeo.runtime.api.Framework;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@WebObject(type = "committee-task")
public class CommitteeTaskObject extends QueryObject {

    private static final String CALENDAR_TASKS_PROVIDER = "PP_Task_Calendar";

    private static final String COMMITTEE_MEETINGS_PROVIDER = "PP_Meeting";

    private static final Log log = LogFactory.getLog(CommitteeTaskObject.class);

    @Context
    protected CoreSession session;


    @Context
    protected HttpServletRequest request;

    @Context
    protected HttpServletResponse response;




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
    @GET
    @Path("committee/{committeeId}")
    public PaginableTaskList getCommitteeTasks(@Context UriInfo uriInfo,
                                      @PathParam("committeeId") String committeeId) {


        //first get committee meetings
        PageProviderService pageProviderService = Framework.getService(PageProviderService.class);
        Properties namedParameters = new Properties();
        namedParameters.put("CommonData_committeeId", committeeId);

        DocumentModel searchDocumentModel = getSearchDocumentModel(session, pageProviderService,
                COMMITTEE_MEETINGS_PROVIDER, namedParameters);
        Map<String, Serializable> props = new HashMap<>();
        props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY, (Serializable) session);

        PaginableDocumentModelListImpl res = new PaginableDocumentModelListImpl(
                (PageProvider<DocumentModel>) pageProviderService.getPageProvider(COMMITTEE_MEETINGS_PROVIDER, searchDocumentModel,
                        null, null, null, props, null, null),
                null);

        //DocumentModelList meetingsModelList= getQuery(uriInfo, COMMITTEE_MEETINGS_PROVIDER);
        //CommonData_committeeId
        final List<String> meetingIDs =res.stream().map(document -> "\"".concat(document.getId()).concat("\"")).collect(Collectors.toList());
        meetingIDs.add("\"".concat(committeeId).concat("\""));

        DocumentModelList documentModelList = null;// new DocumentModelListImpl();
        if(!CollectionUtils.isEmpty(meetingIDs))
        {
            String ids="[".concat(StringUtils.join(
                    meetingIDs,",")).concat("]");
            uriInfo.getQueryParameters().add("task_targetDocumentsIds", ids);
            documentModelList = getQuery(uriInfo, CALENDAR_TASKS_PROVIDER);
        }
        else
        {
            String ids="[\"".concat(committeeId).concat("\"]");
            uriInfo.getQueryParameters().add("task_targetDocumentsIds", ids);
            documentModelList = getQuery(uriInfo, CALENDAR_TASKS_PROVIDER);
        }

    return PaginableTaskMapper.ToTaskListPage((PaginableDocumentModelList) documentModelList,session);
    }
}
