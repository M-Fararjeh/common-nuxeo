package sa.comptechco.nuxeo.common.operations.operations;

import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.comment.api.CommentManager;

import org.nuxeo.runtime.api.Framework;

import java.util.Calendar;
import java.util.Date;

/**
 *
 */
@Operation(id=CommentOperation.ID, category=Constants.CAT_DOCUMENT, label="Create Comment", description="Create comments on docs.")
public class CommentOperation {

    public static final String ID = "Document.CreateComment";

    @Param(name = "text")
    protected  String text ="";


    @Context
    protected CoreSession session;


    @OperationMethod
    public DocumentModel run(DocumentModel input) {
        CommentManager commentManager = Framework.getService(CommentManager.class);
        DocumentModel commentModel = session.createDocumentModel("Comment");
        commentModel.setPropertyValue("comment:author", session.getPrincipal().getName());
        commentModel.setPropertyValue("comment:text", text);
        commentModel.setPropertyValue("comment:creationDate", new Date());
        commentModel.setPropertyValue("comment:parentId", input.getId());

        DocumentModel comment = commentManager.createComment(input,commentModel);

        return comment;
    }
}
