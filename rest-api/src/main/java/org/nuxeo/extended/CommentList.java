package org.nuxeo.extended;

import org.nuxeo.ecm.platform.comment.api.Comment;
import org.nuxeo.ecm.platform.task.Task;

import java.io.Serializable;
import java.util.List;


public interface CommentList extends List<Comment>, Serializable {

    long totalSize();

}
