package org.nuxeo.extended;

import org.nuxeo.ecm.platform.comment.api.Comment;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.query.api.QuickFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PaginableCommentListImpl extends ArrayList<Comment> implements PaginableCommentList {


    Long pageSize ;

    private Long MaxPageSize;

    private Long ResultsCount;

    private Long NumberOfPages;

    private boolean isNextPageAvailable;

    private boolean isLastPageAvailable;

    private boolean isPreviousPageAvailable;

    private Long CurrentPageSize;

    private Long CurrentPageOffset;

    private Long CurrentPageIndex;

    boolean isSortable;

    boolean hasError;

    String getErrorMessage;

    Map<String, Aggregate<? extends Bucket>> getAggregates;

    boolean hasAggregateSupport;

    List<QuickFilter> getActiveQuickFilters;

    List<QuickFilter> getAvailableQuickFilters;

    private Long ResultsCountLimit;



    @Override
    public long getPageSize() {
        return pageSize;
    }


    public void setPageSize(Long pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public long getMaxPageSize() {
        return MaxPageSize;
    }

    public void setMaxPageSize(Long maxPageSize) {
        MaxPageSize = maxPageSize;
    }
    @Override
    public long getResultsCount() {
        return ResultsCount;
    }

    public void setResultsCount(Long resultsCount) {
        ResultsCount = resultsCount;
    }
    @Override
    public long getNumberOfPages() {
        return NumberOfPages;
    }

    public void setNumberOfPages(Long numberOfPages) {
        NumberOfPages = numberOfPages;
    }
    @Override
    public boolean isNextPageAvailable() {
        return isNextPageAvailable;
    }

    public void setNextPageAvailable(boolean nextPageAvailable) {
        isNextPageAvailable = nextPageAvailable;
    }
    @Override
    public boolean isLastPageAvailable() {
        return isLastPageAvailable;
    }

    public void setLastPageAvailable(boolean lastPageAvailable) {
        isLastPageAvailable = lastPageAvailable;
    }
    @Override
    public boolean isPreviousPageAvailable() {
        return isPreviousPageAvailable;
    }

    public void setPreviousPageAvailable(boolean previousPageAvailable) {
        isPreviousPageAvailable = previousPageAvailable;
    }
    @Override
    public long getCurrentPageSize() {
        return CurrentPageSize;
    }

    public void setCurrentPageSize(Long currentPageSize) {
        CurrentPageSize = currentPageSize;
    }
    @Override
    public long getCurrentPageOffset() {
        return CurrentPageOffset;
    }

    public void setCurrentPageOffset(Long currentPageOffset) {
        CurrentPageOffset = currentPageOffset;
    }
    @Override
    public long getCurrentPageIndex() {
        return CurrentPageIndex;
    }

    public void setCurrentPageIndex(Long currentPageIndex) {
        CurrentPageIndex = currentPageIndex;
    }
    @Override
    public boolean isSortable() {
        return isSortable;
    }

    @Override
    public boolean hasError() {
        return false;
    }

    @Override
    public String getErrorMessage() {
        return null;
    }

    @Override
    public Map<String, Aggregate<? extends Bucket>> getAggregates() {
        return getAggregates;
    }

    @Override
    public boolean hasAggregateSupport() {
        return hasAggregateSupport;
    }

    @Override
    public List<QuickFilter> getActiveQuickFilters() {
        return null;
    }

    @Override
    public List<QuickFilter> getAvailableQuickFilters() {
        return null;
    }

    public void setSortable(boolean sortable) {
        isSortable = sortable;
    }

    public boolean isHasError() {
        return hasError;
    }

    public void setHasError(boolean hasError) {
        this.hasError = hasError;
    }

    public String getGetErrorMessage() {
        return getErrorMessage;
    }

    public void setGetErrorMessage(String getErrorMessage) {
        this.getErrorMessage = getErrorMessage;
    }



    public void setGetAggregates(Map<String, Aggregate<? extends Bucket>> getAggregates) {
        this.getAggregates = getAggregates;
    }

    public boolean isHasAggregateSupport() {
        return hasAggregateSupport;
    }

    public void setHasAggregateSupport(boolean hasAggregateSupport) {
        this.hasAggregateSupport = hasAggregateSupport;
    }

    public List<QuickFilter> getGetActiveQuickFilters() {
        return getActiveQuickFilters;
    }

    public void setGetActiveQuickFilters(List<QuickFilter> getActiveQuickFilters) {
        this.getActiveQuickFilters = getActiveQuickFilters;
    }

    public List<QuickFilter> getGetAvailableQuickFilters() {
        return getAvailableQuickFilters;
    }

    public void setGetAvailableQuickFilters(List<QuickFilter> getAvailableQuickFilters) {
        this.getAvailableQuickFilters = getAvailableQuickFilters;
    }
    @Override
    public long getResultsCountLimit() {
        return ResultsCountLimit;
    }

    public void setResultsCountLimit(Long resultsCountLimit) {
        ResultsCountLimit = resultsCountLimit;
    }

    @Override
    public String getDocumentLinkBuilder() {
        return null;
    }


    @Override
    public long totalSize() {
        return getResultsCount();
    }
}
