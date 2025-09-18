package org.nuxeo.extended.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.SortInfo;
import org.nuxeo.ecm.platform.query.api.*;
import org.nuxeo.ecm.platform.query.core.*;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomPageProviderServiceImpl extends PageProviderServiceImpl {
    private static final Logger log = LogManager.getLogger(CustomPageProviderServiceImpl.class);
    private final PageProviderService service = Framework.getService(PageProviderService.class);
    @Override
    public PageProvider<?> getPageProvider(String name, DocumentModel searchDocument, List<SortInfo> sortInfos,
                                           Long pageSize, Long currentPage, Long currentOffset, Map<String, Serializable> properties,
                                           List<String> highlights, List<QuickFilter> quickFilters, Object... parameters) {
        PageProviderDefinition desc =  service.getPageProviderDefinition(name);
        if (desc == null) {
            throw new NuxeoException(String.format("Could not resolve page provider with name '%s'", name));
        }
        return getPageProvider(name, desc, searchDocument, sortInfos, pageSize, currentPage, currentOffset, properties,
                highlights, quickFilters, parameters);
    }
    @Override
    public PageProvider<?> getPageProvider(String name, PageProviderDefinition desc, DocumentModel searchDocument,
                                           List<SortInfo> sortInfos, Long pageSize, Long currentPage, Long currentOffset,
                                           Map<String, Serializable> properties, List<String> highlights, List<QuickFilter> quickFilters,
                                           Object... parameters) {
        if (desc == null) {
            return null;
        }
        Class<CustomElasticSearchNxqlPageProvider> klass = CustomElasticSearchNxqlPageProvider.class;
        PageProvider<?> pageProvider = newPageProviderInstance(name, klass);
        // XXX: set local properties without resolving, and merge with given
        // properties.
        Map<String, Serializable> allProps = new HashMap<>();
        Map<String, String> localProps = desc.getProperties();
        if (localProps != null) {
            allProps.putAll(localProps);
        }
        if (properties != null) {
            allProps.putAll(properties);
        }
        pageProvider.setDefinition(desc);
        pageProvider.setProperties(allProps);
        pageProvider.setSortable(desc.isSortable());
        pageProvider.setParameters(parameters);
        pageProvider.setPageSizeOptions(desc.getPageSizeOptions());
        if (searchDocument != null) {
            pageProvider.setSearchDocumentModel(searchDocument);
        }

        Long maxPageSize = desc.getMaxPageSize();
        if (maxPageSize != null) {
            pageProvider.setMaxPageSize(maxPageSize.longValue());
        }

        if (sortInfos == null) {
            pageProvider.setSortInfos(desc.getSortInfos());
        } else {
            pageProvider.setSortInfos(sortInfos);
        }

        if (quickFilters != null) {
            pageProvider.setQuickFilters(quickFilters);
        }

        if (highlights != null) {
            pageProvider.setHighlights(highlights);
        }

        if (pageSize == null || pageSize.longValue() < 0) {
            pageProvider.setPageSize(desc.getPageSize());
        } else {
            pageProvider.setPageSize(pageSize.longValue());
        }
        if (currentPage != null && currentPage.longValue() > 0) {
            pageProvider.setCurrentPage(currentPage.longValue());
        }
        if (currentOffset != null && currentOffset.longValue() >= 0) {
            pageProvider.setCurrentPageOffset(currentOffset.longValue());
        }

        return pageProvider;
    }
}