package org.nuxeo.extended.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.platform.query.api.*;
import org.nuxeo.elasticsearch.provider.ElasticSearchNxqlPageProvider;

import java.util.ArrayList;
import java.util.List;

public class CustomElasticSearchNxqlPageProvider extends ElasticSearchNxqlPageProvider {
    private static final Logger log = LogManager.getLogger(CustomElasticSearchNxqlPageProvider.class);
    public CustomElasticSearchNxqlPageProvider() {
        super();
    }

    @Override
    public PageProviderDefinition getDefinition() {
        return super.getDefinition();
    }

    @Override
    protected void buildQuery(CoreSession coreSession) {
        List<SearchPredicateDto> nxSearchPredicateList = (List<SearchPredicateDto>) getSearchDocumentModel().getContextData("nxSearchPredicateList");
        List<SortInfo> sort = null;
        List<QuickFilter> quickFilters = getQuickFilters();
        String quickFiltersClause = "";

        if (quickFilters != null && !quickFilters.isEmpty()) {
            sort = new ArrayList<>();
            for (QuickFilter quickFilter : quickFilters) {
                String clause = quickFilter.getClause();
                if (clause != null) {
                    if (!quickFiltersClause.isEmpty()) {
                        quickFiltersClause = CustomNXQLQueryBuilder.appendClause(quickFiltersClause, clause);
                    } else {
                        quickFiltersClause = clause;
                    }
                }
                sort.addAll(quickFilter.getSortInfos());
            }

        } else if (sortInfos != null) {
            sort = sortInfos;
        }

        SortInfo[] sortArray = null;
        if (sort != null) {
            sortArray = sort.toArray(new SortInfo[] {});
        }

        String newQuery;

        PageProviderDefinition def = getDefinition();
        WhereClauseDefinition whereClause = def.getWhereClause();
        if (whereClause == null) {

            String originalPattern = def.getPattern();
            String pattern = quickFiltersClause.isEmpty() ? originalPattern
                    : StringUtils.containsIgnoreCase(originalPattern, " WHERE ")
                    ? CustomNXQLQueryBuilder.appendClause(originalPattern, quickFiltersClause)
                    : originalPattern + " WHERE " + quickFiltersClause;

            newQuery = CustomNXQLQueryBuilder.getQuery(pattern, getParameters(), def.getQuotePatternParameters(),
                    def.getEscapePatternParameters(), getSearchDocumentModel(), sortArray);
        } else {

            DocumentModel searchDocumentModel = getSearchDocumentModel();
            if (searchDocumentModel == null) {
                throw new NuxeoException(String.format(
                        "Cannot build query of provider '%s': " + "no search document model is set", getName()));
            }
            newQuery = CustomNXQLQueryBuilder.getQuery(searchDocumentModel, whereClause, quickFiltersClause, getParameters(),
                    nxSearchPredicateList,sortArray);
        }

        if (query != null && newQuery != null && !newQuery.equals(query)) {
            // query has changed => refresh
            refresh();
        }
        query = newQuery;
    }
}
