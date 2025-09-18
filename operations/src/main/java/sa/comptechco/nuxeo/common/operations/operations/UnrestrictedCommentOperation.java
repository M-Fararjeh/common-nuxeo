package sa.comptechco.nuxeo.common.operations.operations;

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

import java.util.Date;

/**
 * Operation for creating comments with custom author and date (admin only).
 * 
 * This operation allows administrators to create comments with custom
 * author and creation date, useful for data migration or system operations.
 */
@Operation(id = UnrestrictedCommentOperation.ID, category = Constants.CAT_DOCUMENT, label = "Create Custom Comment", description = "Create comments with custom author and date (admin only).")
public class UnrestrictedCommentOperation {

    public static final String ID = "Document.CreateCustomComment";

    @Param(name = "text")
    protected String text = "";

    @Param(name = "date")
    protected Date commentDate;

    @Param(name = "author")
    protected String author;

    @Context
    protected CoreSession session;

    @OperationMethod
    public DocumentModel run(DocumentModel input) {
        validateAdminAccess();
        validateInputParameters();
        
        DocumentModel commentModel = createCustomCommentModel(input);
        return createComment(input, commentModel);
    }

    /**
     * Validates that the current user is an administrator.
     */
    private void validateAdminAccess() {
        if (!session.getPrincipal().isAdministrator()) {
            throw new NuxeoException("Only administrators can create custom comments", 403);
        }
    }

    /**
     * Validates required input parameters.
     */
    private void validateInputParameters() {
        if (author == null || author.trim().isEmpty()) {
            throw new IllegalArgumentException("Author parameter is required");
        }
        if (commentDate == null) {
            throw new IllegalArgumentException("Comment date parameter is required");
        }
    }

    /**
     * Creates a comment model with custom author and date.
     */
    private DocumentModel createCustomCommentModel(DocumentModel parentDocument) {
        DocumentModel comment = session.createDocumentModel("Comment");
        
        comment.setPropertyValue("comment:author", author);
        comment.setPropertyValue("comment:text", text);
        comment.setPropertyValue("comment:creationDate", commentDate);
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