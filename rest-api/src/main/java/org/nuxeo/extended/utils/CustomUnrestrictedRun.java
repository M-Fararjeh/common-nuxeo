package org.nuxeo.extended.utils;

import org.meeting.AutomationException;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;

public class CustomUnrestrictedRun extends UnrestrictedSessionRunner {

    protected DocumentModel documentModel;

    protected Blob blob;

    protected Boolean isContent;

    protected String id;

    protected String xPath;


    public CustomUnrestrictedRun(String repositoryName, String id, Boolean isContent) {
        super(repositoryName);
        this.id = id;
        this.isContent = isContent;
    }

    public CustomUnrestrictedRun(String repositoryName, String id, Boolean isContent,String xPath) {
        super(repositoryName);
        this.id = id;
        this.isContent = isContent;
        this.xPath= xPath;
    }

    @Override
    public void run() {
               IdRef docRef = new IdRef(id);
                if (docRef != null) {
                    DocumentModel document = session.getDocument(docRef);
                    if(document.hasSchema("Correspondence") || document.hasSchema("Memo")) {
                        documentModel = document;
                    }
                    else {
                        throw new AutomationException(500,"bad request","Invalid document type");
                    }
                }
                if(isContent)
                {
                    if(documentModel!= null ) {
                        blob = (Blob) documentModel.getPropertyValue(xPath);
                    }
                }
    }

    public DocumentModel getDocument() {
        return documentModel;
    }

    public Blob getBlob() {
        return blob;
    }
}
