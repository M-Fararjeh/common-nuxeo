package sa.comptechco.nuxeo.common.operations.operations;

import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.collectors.BlobCollector;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import sa.comptechco.nuxeo.common.operations.utils.Utils;

import java.io.IOException;
import java.io.InputStream;

/**
 *
 */
@Operation(id=CTDocumentTemplateRender.ID, category=Constants.CAT_DOCUMENT, label="CT Document Template Render", description="Describe here what your operation does.")
public class CTDocumentTemplateRender {

    public static final String ID = "Document.CTDocumentTemplateRender";

    @Context
    protected CoreSession session;

    @Context
    protected OperationContext ctx;

    @Param(name = "applyMerge", required = false)
    protected Boolean applyMerge;

    @Param(name = "mergeDocumentId", required = false)
    protected String mergeDocumentId;

    @OperationMethod(collector = BlobCollector.class)
    public Blob run(DocumentModel input) throws IOException {
        Utils.checkInputDocumentType(input);
        boolean isApplyMerge = this.applyMerge != null ? this.applyMerge : false;
        String documentIdToMerge = this.mergeDocumentId != null ? this.mergeDocumentId : "";
        Blob content = (Blob) input.getPropertyValue("file:content");
        try (InputStream inputStream = content.getStream()) {
            Blob out = Utils.processDocumentToPDF(inputStream, session, ctx, isApplyMerge, documentIdToMerge);
            return out;
        }
    }
}
