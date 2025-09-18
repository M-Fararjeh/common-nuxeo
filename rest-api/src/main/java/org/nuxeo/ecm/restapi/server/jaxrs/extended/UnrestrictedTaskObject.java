package org.nuxeo.ecm.restapi.server.jaxrs.extended;

import com.mchange.v2.beans.BeansUtils;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.core.util.PageProviderHelper;
import org.nuxeo.ecm.automation.core.util.PaginableDocumentModelList;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.automation.jaxrs.io.documents.PaginableDocumentModelListImpl;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.platform.query.api.*;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.restapi.server.jaxrs.QueryObject;
import org.nuxeo.ecm.restapi.server.jaxrs.adapters.SearchAdapter;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.extended.PaginableTaskList;
import org.nuxeo.extended.PaginableTaskListImpl;
import org.nuxeo.extended.utils.PaginableTaskMapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import java.beans.beancontext.BeanContext;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;


@WebObject(type = "unrestricted-task")
public class UnrestrictedTaskObject extends QueryObject {

    @Context
    protected CoreSession session;


    @Context
    protected HttpServletRequest request;

    @Context
    protected HttpServletResponse response;


    @GET
    @Path("pp/{providerName}")
    public PaginableTaskList getTasks(@Context UriInfo uriInfo,
                                      @PathParam("providerName") String providerName) {

        PaginableTaskList taskList= new PaginableTaskListImpl();
        Map<String,Object> pageProperties =  new HashMap<String,Object>();


        new UnrestrictedSessionRunner(session) {
            @Override
            public void run() {
                DocumentModelList list= getQuery(uriInfo, providerName);

                PaginableTaskList tasks = PaginableTaskMapper.ToTaskListPage((PaginableDocumentModelList) list, session);
                getPageProperties(tasks,pageProperties);

                taskList.addAll(tasks);

            }
        }.runUnrestricted();

        setPageProperties(taskList,pageProperties);
       return taskList;


    }

    private void setPageProperties(PaginableTaskList taskList, Map<String, Object> pageProperties) {
        ((PaginableTaskListImpl)taskList).setCurrentPageIndex((Long) pageProperties.get("currentPageIndex"));
        ((PaginableTaskListImpl)taskList).setCurrentPageSize((Long) pageProperties.get("currentPageSize"));
        ((PaginableTaskListImpl)taskList).setHasAggregateSupport((Boolean) pageProperties.get("hasAggregateSupport"));
        ((PaginableTaskListImpl)taskList).setGetAggregates((Map<String, Aggregate<? extends Bucket>>) pageProperties.get("getAggregates"));
        ((PaginableTaskListImpl)taskList).setResultsCount((Long) pageProperties.get("resultsCount"));
        ((PaginableTaskListImpl)taskList).setPageSize((Long) pageProperties.get("pageSize"));
        ((PaginableTaskListImpl)taskList).setCurrentPageOffset((Long) pageProperties.get("currentPageOffset"));
        ((PaginableTaskListImpl)taskList).setGetActiveQuickFilters((List<QuickFilter>) pageProperties.get("getActiveQuickFilters"));
        ((PaginableTaskListImpl)taskList).setGetErrorMessage((String) pageProperties.get("getErrorMessage"));
        ((PaginableTaskListImpl)taskList).setNumberOfPages((Long) pageProperties.get("numberOfPages"));
        ((PaginableTaskListImpl)taskList).setResultsCountLimit((Long) pageProperties.get("resultsCountLimit"));
        ((PaginableTaskListImpl)taskList).setSortable((Boolean) pageProperties.get("isSortable"));
        ((PaginableTaskListImpl)taskList).setLastPageAvailable((Boolean) pageProperties.get("isLastPageAvailable"));
        ((PaginableTaskListImpl)taskList).setNextPageAvailable((Boolean) pageProperties.get("isNextPageAvailable"));
        ((PaginableTaskListImpl)taskList).setMaxPageSize((Long) pageProperties.get("maxPageSize"));

    }

    private void getPageProperties(PaginableTaskList tasks, Map<String, Object> pageProperties) {
        pageProperties.put("currentPageIndex",tasks.getCurrentPageIndex());
        pageProperties.put("currentPageSize",tasks.getCurrentPageSize());
        pageProperties.put("hasAggregateSupport",tasks.hasAggregateSupport());
        pageProperties.put("getAggregates",tasks.getAggregates());
        pageProperties.put("resultsCount",tasks.getResultsCount());
        pageProperties.put("pageSize",tasks.getPageSize());
        pageProperties.put("currentPageOffset",tasks.getCurrentPageOffset());
        pageProperties.put("getActiveQuickFilters",tasks.getActiveQuickFilters());
        pageProperties.put("getErrorMessage",tasks.getErrorMessage());
        pageProperties.put("numberOfPages",tasks.getNumberOfPages());
        pageProperties.put("resultsCountLimit",tasks.getResultsCountLimit());
        pageProperties.put("isSortable",tasks.isSortable());
        pageProperties.put("isLastPageAvailable",tasks.isLastPageAvailable());
        pageProperties.put("isNextPageAvailable",tasks.isNextPageAvailable());
        pageProperties.put("maxPageSize",tasks.getMaxPageSize());


    }


    protected DocumentModelList getQuery(UriInfo uriInfo, String langOrProviderName) {
        // Fetching all parameters
        MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
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
                        value = CoreInstance.getCoreSession("default").getPrincipal().getName();
                    } else if (value.equals(CURRENT_REPO_PATTERN)) {
                        value = CoreInstance.getCoreSession("default").getRepositoryName();
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
            parameters = orderedParams.toArray(new String[0]);
            // expand specific parameters
            for (int idx = 0; idx < parameters.length; idx++) {
                String value = (String) parameters[idx];
                if (value.equals(CURRENT_USERID_PATTERN)) {
                    parameters[idx] = CoreInstance.getCoreSession("default").getPrincipal().getName();
                } else if (value.equals(CURRENT_REPO_PATTERN)) {
                    parameters[idx] = CoreInstance.getCoreSession("default").getRepositoryName();
                }
            }
        }

        Map<String, Serializable> props = new HashMap<>();
        props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY, (Serializable) CoreInstance.getCoreSession("default"));

        DocumentModel searchDocumentModel = PageProviderHelper.getSearchDocumentModel(CoreInstance.getCoreSession("default"),
                pageProviderService, providerName, namedParameters);

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
                boolean sortAscending = (orders != null && orders.length > i && "asc".equalsIgnoreCase(orders[i]));
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
