package org.nuxeo.extended.utils;

import org.nuxeo.ecm.automation.core.util.PaginableDocumentModelList;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.platform.comment.api.Comment;
import org.nuxeo.ecm.platform.comment.api.CommentAdapterFactory;
import org.nuxeo.ecm.platform.comment.api.CommentImpl;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.extended.PaginableCommentList;
import org.nuxeo.extended.PaginableCommentListImpl;
import org.nuxeo.extended.PaginableTaskList;
import org.nuxeo.extended.PaginableCommentListImpl;

import java.util.*;

import static org.nuxeo.ecm.platform.comment.workflow.utils.CommentsConstants.*;
import static org.nuxeo.ecm.platform.comment.workflow.utils.CommentsConstants.COMMENT_MODIFICATION_DATE;

public class PaginableCommentMapper {

    public static PaginableCommentList ToCommentListPage(PaginableDocumentModelList paginable, CoreSession session)
    {
        //PaginableDocumentModelList paginable = (PaginableDocumentModelList) list;
        final List<Comment> result = new ArrayList<>();


        // User does not necessary have READ on the workflow instance
        new UnrestrictedSessionRunner(session) {
            @Override
            public void run() {
                for (DocumentModel documentModel : paginable) {
                    //final Comment comment = documentModel.getAdapter(Comment.class);

                    // documentModelToComment(documentModel, comment);
                    CommentAdapterFactory commentAdapterFactory = new CommentAdapterFactory();
                    Comment comment = commentAdapterFactory.getAdapter(documentModel, Comment.class);
                    result.add(comment);
                }
            }
        }.runUnrestricted();


        PaginableCommentList commentList= new PaginableCommentListImpl();
        commentList.addAll(result);
        ((PaginableCommentListImpl)commentList).setCurrentPageIndex(paginable.getCurrentPageIndex());
        ((PaginableCommentListImpl)commentList).setCurrentPageSize(paginable.getCurrentPageSize());
        ((PaginableCommentListImpl)commentList).setHasAggregateSupport(paginable.hasAggregateSupport());
        if(paginable.hasAggregateSupport())
        {
            ((PaginableCommentListImpl)commentList).setGetAggregates(paginable.getAggregates());
        }
        else
        {
            ((PaginableCommentListImpl)commentList).setGetAggregates(new HashMap<String, Aggregate<? extends Bucket>>());
        }

        ((PaginableCommentListImpl)commentList).setResultsCount(paginable.getResultsCount());
        ((PaginableCommentListImpl)commentList).setPageSize(paginable.getPageSize());
        ((PaginableCommentListImpl)commentList).setCurrentPageOffset(paginable.getCurrentPageOffset());
        ((PaginableCommentListImpl)commentList).setGetActiveQuickFilters(paginable.getActiveQuickFilters());
        ((PaginableCommentListImpl)commentList).setGetErrorMessage(paginable.getErrorMessage());
        ((PaginableCommentListImpl)commentList).setNumberOfPages(paginable.getNumberOfPages());
        ((PaginableCommentListImpl)commentList).setResultsCountLimit(paginable.getResultsCountLimit());
        ((PaginableCommentListImpl)commentList).setSortable(paginable.isSortable());
        ((PaginableCommentListImpl)commentList).setLastPageAvailable(paginable.isLastPageAvailable());
        ((PaginableCommentListImpl)commentList).setNextPageAvailable(paginable.isNextPageAvailable());
        ((PaginableCommentListImpl)commentList).setMaxPageSize(paginable.getMaxPageSize());

        return commentList;
    }

    public static void documentModelToComment(DocumentModel documentModel, Comment comment) {
        comment.setId(documentModel.getId());
        comment.setAuthor((String) documentModel.getPropertyValue(COMMENT_AUTHOR));
        comment.setText((String) documentModel.getPropertyValue(COMMENT_TEXT));
        Collection<String> ancestorIds = (Collection<String>) documentModel.getPropertyValue(COMMENT_ANCESTOR_IDS);
        ancestorIds.forEach(comment::addAncestorId);
        String parentId = (String) documentModel.getPropertyValue(COMMENT_PARENT_ID);
        comment.setParentId(parentId);

        Calendar creationDate = (Calendar) documentModel.getPropertyValue(COMMENT_CREATION_DATE);
        if (creationDate != null) {
            comment.setCreationDate(creationDate.toInstant());
        }
        Calendar modificationDate = (Calendar) documentModel.getPropertyValue(COMMENT_MODIFICATION_DATE);
        if (modificationDate != null) {
            comment.setModificationDate(modificationDate.toInstant());
        }
    }
}
