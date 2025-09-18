package org.nuxeo.extended.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.plugins.annotations.Component;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.util.PageProviderHelper;
import org.nuxeo.ecm.automation.jaxrs.io.documents.PaginableDocumentModelListImpl;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.SortInfo;
import org.nuxeo.ecm.platform.query.api.*;
import org.nuxeo.ecm.restapi.server.jaxrs.search.QueryExecutor;
import org.nuxeo.ecm.webengine.model.WebContext;
import org.nuxeo.ecm.webengine.model.exceptions.IllegalParameterException;
import org.nuxeo.runtime.api.Framework;

import javax.ws.rs.core.MultivaluedMap;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
public class CustomQueryExecutor extends QueryExecutor {
    protected PageProviderService pageProviderService;
    private static final Logger log = LogManager.getLogger(CustomQueryExecutor.class);

    public void initExecutor() {
        if (this.ctx==null ){
            this.ctx=Framework.getService(WebContext.class);}
        this.pageProviderService = new CustomPageProviderServiceImpl();
        this.skipAggregates = Boolean.parseBoolean(this.ctx.getHttpHeaders().getRequestHeaders().getFirst("skipAggregates"));
    }

    @Override
    protected DocumentModelList queryByPageProvider(String pageProviderName, Long pageSize, Long currentPageIndex,
                                                    Long currentPageOffset, List<SortInfo> sortInfo, List<String> highlights, List<QuickFilter> quickFilters,
                                                    Map<String, Serializable> props, DocumentModel searchDocumentModel, Object... parameters) {
        PageProvider<?> pageProvider = pageProviderService.getPageProvider(pageProviderName, searchDocumentModel,
                sortInfo, pageSize, currentPageIndex, currentPageOffset, props, highlights, quickFilters, parameters);
        if (props.get("aggregationNames") != null)
            updateAggregationInterval(pageProvider, (ArrayList) props.get("aggregationNames"));
        PaginableDocumentModelListImpl res = new PaginableDocumentModelListImpl(
                (PageProvider<DocumentModel>) pageProvider, null);
        if (res.hasError()) {
            throw new NuxeoException(res.getErrorMessage(), SC_BAD_REQUEST);
        }
        return res;
    }

    protected DocumentModelList queryByPageProvider(String pageProviderName,
                                                    MultivaluedMap<String, String> queryParams,
                                                    SearchPredicateDto searchPredicate) {
        if (Operator.isValidOperator(searchPredicate.getOperator())) {
            searchPredicate.setOperator(" " + searchPredicate.getOperator() + " ");
            return queryByPageProvider(pageProviderName, queryParams, Collections.singletonList(searchPredicate), null);
        }
        throw new NuxeoException("Operator Is Not Valid", SC_BAD_REQUEST);
    }

    protected DocumentModelList queryByPageProvider(String pageProviderName,
                                                    MultivaluedMap<String, String> queryParams,
                                                    List<SearchPredicateDto> searchPredicatesList, JsonNode aggregationNamesMaps) {


        if (pageProviderName == null) {
            throw new IllegalParameterException("invalid page provider name");
        }
        ObjectMapper objectMapper = new ObjectMapper();
        List<Map<String, Serializable>> listOfMaps = objectMapper.convertValue(
                aggregationNamesMaps,
                objectMapper.getTypeFactory().constructCollectionType(List.class,
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Serializable.class))
        );
        Long pageSize = getPageSize(queryParams);
        Long currentPageIndex = getCurrentPageIndex(queryParams);
        Long currentPageOffset = getCurrentPageOffset(queryParams);
        Map<String, String> namedParameters = getNamedParameters(queryParams);
        Object[] parameters = getParameters(queryParams);
        List<SortInfo> sortInfo = getSortInfo(queryParams);
        List<QuickFilter> quickFilters = getQuickFilters(pageProviderName, queryParams);
        List<String> highlights = getHighlights(queryParams);
        Map<String, Serializable> props = getProperties();
        if (listOfMaps != null)
            props.put("aggregationNames", (Serializable) listOfMaps);
        DocumentModel searchDocumentModel = PageProviderHelper.getSearchDocumentModel(ctx.getCoreSession(),
                pageProviderName, namedParameters);

        searchDocumentModel.putContextData("nxSearchPredicateList", (Serializable) searchPredicatesList);

        return queryByPageProvider(pageProviderName, pageSize, currentPageIndex, currentPageOffset, sortInfo,
                highlights, quickFilters, props, searchDocumentModel, parameters);
    }

    @Override
    protected List<QuickFilter> getQuickFilters(String providerName, MultivaluedMap<String, String> queryParams) {
        PageProviderDefinition pageProviderDefinition = Framework.getService(PageProviderService.class).getPageProviderDefinition(providerName);
        String quickFilters = queryParams.getFirst(QUICK_FILTERS);
        List<QuickFilter> quickFilterList = new ArrayList<>();
        if (!StringUtils.isBlank(quickFilters)) {
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
        return quickFilterList;
    }

    protected void updateAggregationInterval(PageProvider<?> pageProvider, ArrayList aggregationNamesMap) {
        if (aggregationNamesMap.isEmpty())
            return;
        List<AggregateDefinition> aggregates = pageProvider.getDefinition().getAggregates();
        List<Map<String, Object>> filtersList = (List<Map<String, Object>>) aggregationNamesMap;

        for (int i = 0; i < aggregates.size(); i++) {
            if (!aggregates.get(i).getType().equals("date_histogram"))
                continue;
            String aggName = aggregates.get(i).getId();

            for (Map<String, Object> aggMap : filtersList) {
                if (aggName.equals(aggMap.get("name").toString())) {
                    pageProvider.getDefinition().getAggregates().get(i).setProperty("interval", aggMap.get("interval").toString());

                }

            }
        }
    }
    @Override
    public String toString() {
        return "CustomSearchService{customSearch=";
    }
}
