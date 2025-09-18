package org.nuxeo.extended;

import org.nuxeo.ecm.automation.core.util.Paginable;
import org.nuxeo.ecm.platform.comment.api.Comment;
import org.nuxeo.ecm.platform.task.Task;

import java.io.Serializable;

public  interface PaginableCommentList extends  CommentList, Paginable<Comment> , Serializable {

    public static final String CODEC_PARAMETER_NAME = "URLCodecName";

    String getDocumentLinkBuilder();


}
