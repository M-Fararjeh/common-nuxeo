package org.nuxeo.ecm.restapi.server.jaxrs.extended;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.core.util.PaginableDocumentModelList;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.automation.jaxrs.io.documents.PaginableDocumentModelListImpl;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.platform.comment.api.exceptions.CommentSecurityException;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.QuickFilter;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.ecm.restapi.server.jaxrs.QueryObject;
import org.nuxeo.ecm.restapi.server.jaxrs.adapters.SearchAdapter;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.extended.PaginableCommentList;
import org.nuxeo.extended.utils.PaginableCommentMapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.nuxeo.ecm.platform.comment.workflow.utils.CommentsConstants.COMMENT_PARENT_ID;
import static org.nuxeo.ecm.platform.comment.workflow.utils.CommentsConstants.COMMENT_SCHEMA;


@WebObject(type = "custom-comment")
public class CustomCommentObject extends QueryObject {

    @Context
    protected CoreSession session;


    @Context
    protected HttpServletRequest request;

    @Context
    protected HttpServletResponse response;


    @GET
    @Path("pp/{providerName}")
    public PaginableCommentList getComments(@Context UriInfo uriInfo,
                                         @PathParam("providerName") String providerName) {

        final List<PaginableCommentList> list = new ArrayList<>(1);
        new UnrestrictedSessionRunner(session) {
            @Override
            public void run() {
                DocumentModelList documentModelList = getQuery(session,uriInfo, providerName);
                PaginableCommentList result = PaginableCommentMapper.ToCommentListPage((PaginableDocumentModelList) documentModelList, session);
                list.add(result);
            }
        }.runUnrestricted();


        return list.get(0);
    }


    protected DocumentRef getAncestorRef(CoreSession session, DocumentModel documentModel) {
        return CoreInstance.doPrivileged(session, s -> {
            if (!documentModel.hasSchema(COMMENT_SCHEMA)) {
                return documentModel.getRef();
            }
            DocumentModel ancestorComment = getThreadForComment(s, documentModel);
            return new IdRef((String) ancestorComment.getPropertyValue(COMMENT_PARENT_ID));
        });
    }

    protected DocumentModel getThreadForComment(CoreSession session, DocumentModel comment)
            throws CommentSecurityException {

        NuxeoPrincipal principal = session.getPrincipal();
        return CoreInstance.doPrivileged(session, s -> {
            DocumentModel thread = comment;
            DocumentModel parent = s.getDocument(new IdRef((String) thread.getPropertyValue(COMMENT_PARENT_ID)));
            if (parent.hasSchema(COMMENT_SCHEMA)) {
                thread = getThreadForComment(parent);
            }
            DocumentRef ancestorRef = s.getDocument(new IdRef((String) thread.getPropertyValue(COMMENT_PARENT_ID)))
                    .getRef();
            if (!s.hasPermission(principal, ancestorRef, SecurityConstants.READ)) {
                throw new CommentSecurityException("The user " + principal.getName()
                        + " does not have access to the comments of document " + ancestorRef.reference());
            }
            return thread;
        });
    }

    public DocumentModel getThreadForComment(DocumentModel comment) throws CommentSecurityException {
        return getThreadForComment(comment.getCoreSession(), comment);
    }

    protected DocumentModelList getQuery(CoreSession session, UriInfo uriInfo, String langOrProviderName) {
        // Fetching all parameters
        MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();


        DocumentRef docRef = new IdRef(queryParams.getFirst("queryParams"));
        if (session.exists(docRef)) {
            NuxeoPrincipal principal = ctx.getCoreSession().getPrincipal();
            DocumentRef ancestorRef = getAncestorRef(session, session.getDocument(docRef));
            if (session.exists(ancestorRef) && !session.hasPermission(principal, ancestorRef, SecurityConstants.READ)) {
                throw new CommentSecurityException("The user " + principal.getName()
                        + " does not have access to the comments of document " + docRef);
            }
        }
        // Look if provider name is given
        String providerName = null;
        if (!langPathMap.containsValue(langOrProviderName)) {
            providerName = langOrProviderName;
        }
        String query = queryParams.getFirst(QUERY);
        String pageSize = queryParams.getFirst(PAGE_SIZE);
        String currentPageIndex = queryParams.getFirst(CURRENT_PAGE_INDEX);
        String maxResults = queryParams.getFirst(MAX_RESULTS);
        String sortBy = queryParams.getFirst(SORT_BY);
        String sortOrder = queryParams.getFirst(SORT_ORDER);
        List<String> orderedParams = queryParams.get(ORDERED_PARAMS);
        String quickFilters = queryParams.getFirst(QUICK_FILTERS);

        // If no query or provider name has been found
        // Execute big select
        if (query == null && StringUtils.isBlank(providerName)) {
            // provide a defaut query
            query = "SELECT * from Document";
        }

        // Fetching named parameters (other custom query parameters in the
        // path)
        Properties namedParameters = new Properties();
        for (String namedParameterKey : queryParams.keySet()) {
            if (!queryParametersMap.containsValue(namedParameterKey)) {
                String value = queryParams.getFirst(namedParameterKey);
                if (value != null) {
                    if (value.equals(CURRENT_USERID_PATTERN)) {
                        value = ctx.getCoreSession().getPrincipal().getName();
                    } else if (value.equals(CURRENT_REPO_PATTERN)) {
                        value = ctx.getCoreSession().getRepositoryName();
                    }
                }
                namedParameters.put(namedParameterKey, value);
            }
        }

        // Target query page
        Long targetPage = null;
        if (currentPageIndex != null) {
            targetPage = Long.valueOf(currentPageIndex);
        }

        // Target page size
        Long targetPageSize = null;
        if (pageSize != null) {
            targetPageSize = Long.valueOf(pageSize);
        }

        // Ordered Parameters
        Object[] parameters = null;
        if (orderedParams != null && !orderedParams.isEmpty()) {
            parameters = orderedParams.toArray(new String[orderedParams.size()]);
            // expand specific parameters
            for (int idx = 0; idx < parameters.length; idx++) {
                String value = (String) parameters[idx];
                if (value.equals(CURRENT_USERID_PATTERN)) {
                    parameters[idx] = ctx.getCoreSession().getPrincipal().getName();
                } else if (value.equals(CURRENT_REPO_PATTERN)) {
                    parameters[idx] = ctx.getCoreSession().getRepositoryName();
                }
            }
        }

        Map<String, Serializable> props = new HashMap<>();
        props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY, (Serializable) session);

        DocumentModel searchDocumentModel = getSearchDocumentModel(session, pageProviderService,
                providerName, namedParameters);

        // Sort Info Management
        List<SortInfo> sortInfoList = null;
        if (!StringUtils.isBlank(sortBy)) {
            sortInfoList = new ArrayList<>();
            String[] sorts = sortBy.split(",");
            String[] orders = null;
            if (!StringUtils.isBlank(sortOrder)) {
                orders = sortOrder.split(",");
            }
            for (int i = 0; i < sorts.length; i++) {
                String sort = sorts[i];
                boolean sortAscending = (orders != null && orders.length > i && "asc".equals(orders[i].toLowerCase()));
                sortInfoList.add(new SortInfo(sort, sortAscending));
            }
        }

        PaginableDocumentModelListImpl res;
        if (query != null) {
            PageProviderDefinition ppdefinition = pageProviderService.getPageProviderDefinition(
                    SearchAdapter.pageProviderName);
            ppdefinition.setPattern(query);
            if (maxResults != null && !maxResults.isEmpty() && !maxResults.equals("-1")) {
                // set the maxResults to avoid slowing down queries
                ppdefinition.getProperties().put("maxResults", maxResults);
            }
            if (StringUtils.isBlank(providerName)) {
                providerName = SearchAdapter.pageProviderName;
            }

            res = new PaginableDocumentModelListImpl(
                    (PageProvider<DocumentModel>) pageProviderService.getPageProvider(providerName, ppdefinition,
                            searchDocumentModel, sortInfoList, targetPageSize, targetPage, props, parameters),
                    null);
        } else {
            PageProviderDefinition pageProviderDefinition = pageProviderService.getPageProviderDefinition(providerName);
            // Quick filters management
            List<QuickFilter> quickFilterList = new ArrayList<>();
            if (quickFilters != null && !quickFilters.isEmpty()) {
                String[] filters = quickFilters.split(",");
                List<QuickFilter> ppQuickFilters = pageProviderDefinition.getQuickFilters();
                for (String filter : filters) {
                    for (QuickFilter quickFilter : ppQuickFilters) {
                        if (quickFilter.getName().equals(filter)) {
                            quickFilterList.add(quickFilter);
                            break;
                        }
                    }
                }
            }
            res = new PaginableDocumentModelListImpl(
                    (PageProvider<DocumentModel>) pageProviderService.getPageProvider(providerName, searchDocumentModel,
                            sortInfoList, targetPageSize, targetPage, props, quickFilterList, parameters),
                    null);
        }
        if (res.hasError()) {
            throw new NuxeoException(res.getErrorMessage(), SC_BAD_REQUEST);
        }
        return res;
    }


}
