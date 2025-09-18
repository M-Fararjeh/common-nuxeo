package sa.comptechco.nuxeo.common.operations.operations;

import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.comment.api.CommentManager;
import org.nuxeo.runtime.api.Framework;

import java.util.Date;

/**
 *
 */
@Operation(id= UnrestrictedCommentOperation.ID, category=Constants.CAT_DOCUMENT, label="Create Comment", description="Create comments on docs.")
public class UnrestrictedCommentOperation {

    public static final String ID = "Document.CreateCustomComment";

    @Param(name = "text")
    protected  String text ="";


    @Param(name = "date")
    protected  Date commentDate ;

    @Param(name = "author")
    protected  String author;

    @Context
    protected CoreSession session;


    @OperationMethod
    public DocumentModel run(DocumentModel input) {
        if(session.getPrincipal().isAdministrator()) {
            CommentManager commentManager = Framework.getService(CommentManager.class);
            DocumentModel commentModel = session.createDocumentModel("Comment");
            commentModel.setPropertyValue("comment:author", author);
            commentModel.setPropertyValue("comment:text", text);
            commentModel.setPropertyValue("comment:creationDate", commentDate);
            commentModel.setPropertyValue("comment:parentId", input.getId());

            DocumentModel comment = commentManager.createComment(input, commentModel);
            return comment;
        }
        else
        {
            throw new RuntimeException("not allowed to add comment");
        }

    }
}
