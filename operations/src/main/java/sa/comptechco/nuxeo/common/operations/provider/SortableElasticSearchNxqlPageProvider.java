package sa.comptechco.nuxeo.common.operations.provider;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.SortInfo;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.QuickFilter;
import org.nuxeo.ecm.platform.query.api.WhereClauseDefinition;
import org.nuxeo.ecm.platform.query.nxql.NXQLQueryBuilder;
import org.nuxeo.elasticsearch.provider.ElasticSearchNxqlPageProvider;

import java.util.List;

public class SortableElasticSearchNxqlPageProvider extends ElasticSearchNxqlPageProvider {


    @Override
    protected void buildQuery(final CoreSession coreSession) {
        final List<QuickFilter> quickFilters = (List<QuickFilter>)this.getQuickFilters();
        String quickFiltersClause = "";
        if (quickFilters != null && !quickFilters.isEmpty()) {
            for (final QuickFilter quickFilter : quickFilters) {
                final String clause = quickFilter.getClause();
                if (clause != null) {
                    if (!quickFiltersClause.isEmpty()) {
                        quickFiltersClause = NXQLQueryBuilder.appendClause(quickFiltersClause, clause);
                    }
                    else {
                        quickFiltersClause = clause;
                    }
                }
            }
        }
        final List<SortInfo> sortInfos = (List<SortInfo>)this.getSortInfos();
        SortInfo[] sortArray = null;
        if (sortInfos != null) {
            sortArray = sortInfos.toArray(SortInfo[]::new);
        }
        final PageProviderDefinition def = this.getDefinition();
        final WhereClauseDefinition whereClause = def.getWhereClause();
        String newQuery;
        if (whereClause == null) {

            String originalPattern = def.getPattern();
            String pattern = quickFiltersClause.isEmpty() ? originalPattern
                    : StringUtils.containsIgnoreCase(originalPattern, " WHERE ")
                    ? NXQLQueryBuilder.appendClause(originalPattern, quickFiltersClause)
                    : originalPattern + " WHERE " + quickFiltersClause;

            newQuery = NXQLQueryBuilder.getQuery(pattern, getParameters(), def.getQuotePatternParameters(),
                    def.getEscapePatternParameters(), getSearchDocumentModel(), sortArray);
        }
        else {
            final DocumentModel searchDocumentModel = this.getSearchDocumentModel();
            if (searchDocumentModel == null) {
                throw new NuxeoException(String.format("Cannot build query of provider '%s': no search document model is set", this.getName()));
            }
            newQuery = NXQLQueryBuilder.getQuery(searchDocumentModel, whereClause, quickFiltersClause, this.getParameters(), sortArray);
        }
        if (this.query != null && newQuery != null && !newQuery.equals(this.query)) {
            this.refresh();
        }
        this.query = newQuery;
    }
}
