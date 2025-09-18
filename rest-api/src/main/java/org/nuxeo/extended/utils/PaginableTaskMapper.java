package org.nuxeo.extended.utils;

import org.nuxeo.ecm.automation.core.util.PaginableDocumentModelList;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.extended.PaginableTaskList;
import org.nuxeo.extended.PaginableTaskListImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaginableTaskMapper {

    public static PaginableTaskList ToTaskListPage(PaginableDocumentModelList paginable, CoreSession session)
    {
        //PaginableDocumentModelList paginable = (PaginableDocumentModelList) list;
        final List<Task> result = new ArrayList<>();


        // User does not necessary have READ on the workflow instance
        new UnrestrictedSessionRunner(session) {
            @Override
            public void run() {
                for (DocumentModel documentModel : paginable) {
                    final Task task = documentModel.getAdapter(Task.class);
                    result.add(task);
                }
            }
        }.runUnrestricted();


        PaginableTaskList taskList= new PaginableTaskListImpl();
        taskList.addAll(result);
        ((PaginableTaskListImpl)taskList).setCurrentPageIndex(paginable.getCurrentPageIndex());
        ((PaginableTaskListImpl)taskList).setCurrentPageSize(paginable.getCurrentPageSize());
        ((PaginableTaskListImpl)taskList).setHasAggregateSupport(paginable.hasAggregateSupport());
        if(paginable.hasAggregateSupport())
        {
            ((PaginableTaskListImpl)taskList).setGetAggregates(paginable.getAggregates());
        }
        else
        {
            ((PaginableTaskListImpl)taskList).setGetAggregates(new HashMap<String, Aggregate<? extends Bucket>>());
        }

        ((PaginableTaskListImpl)taskList).setResultsCount(paginable.getResultsCount());
        ((PaginableTaskListImpl)taskList).setPageSize(paginable.getPageSize());
        ((PaginableTaskListImpl)taskList).setCurrentPageOffset(paginable.getCurrentPageOffset());
        ((PaginableTaskListImpl)taskList).setGetActiveQuickFilters(paginable.getActiveQuickFilters());
        ((PaginableTaskListImpl)taskList).setGetErrorMessage(paginable.getErrorMessage());
        ((PaginableTaskListImpl)taskList).setNumberOfPages(paginable.getNumberOfPages());
        ((PaginableTaskListImpl)taskList).setResultsCountLimit(paginable.getResultsCountLimit());
        ((PaginableTaskListImpl)taskList).setSortable(paginable.isSortable());
        ((PaginableTaskListImpl)taskList).setLastPageAvailable(paginable.isLastPageAvailable());
        ((PaginableTaskListImpl)taskList).setNextPageAvailable(paginable.isNextPageAvailable());
        ((PaginableTaskListImpl)taskList).setMaxPageSize(paginable.getMaxPageSize());

        return taskList;
    }
}
