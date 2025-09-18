package sa.comptechco.nuxeo.common.operations.operations;

import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.comment.api.CommentManager;
import org.nuxeo.runtime.api.Framework;

import java.util.Calendar;
import java.util.Date;

/**
 * Operation for creating comments on documents.
 * 
 * This operation creates a new comment associated with a document,
 * automatically setting the author, creation date, and parent document reference.
 */
@Operation(id=CommentOperation.ID, category=Constants.CAT_DOCUMENT, label="Create Comment", description="Create comments on docs.")
public class CommentOperation {

    public static final String ID = "Document.CreateComment";

    @Param(name = "text")
    protected String text = "";

    @Context
    protected CoreSession session;

    @OperationMethod
    public DocumentModel run(DocumentModel input) {
        DocumentModel commentModel = createCommentModel(input);
        return createComment(input, commentModel);
    }

    /**
     * Creates a comment document model with the provided text.
     */
    private DocumentModel createCommentModel(DocumentModel parentDocument) {
        DocumentModel comment = session.createDocumentModel("Comment");
        
        comment.setPropertyValue("comment:author", session.getPrincipal().getName());
        comment.setPropertyValue("comment:text", text);
        comment.setPropertyValue("comment:creationDate", new Date());
        comment.setPropertyValue("comment:parentId", parentDocument.getId());
        
        return comment;
    }
    /**
     * Creates the comment using the comment manager service.
     */
    private DocumentModel createComment(DocumentModel parentDocument, DocumentModel commentModel) {
        CommentManager commentManager = Framework.getService(CommentManager.class);
        return commentManager.createComment(parentDocument, commentModel);
    }
}

        return comment;
    }
}
