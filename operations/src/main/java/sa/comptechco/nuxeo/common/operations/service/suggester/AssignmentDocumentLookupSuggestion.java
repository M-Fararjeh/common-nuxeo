package sa.comptechco.nuxeo.common.operations.service.suggester;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentLocation;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.impl.DocumentLocationImpl;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.suggestbox.service.Suggestion;
import org.nuxeo.ecm.platform.types.adapter.TypeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AssignmentDocumentLookupSuggestion extends  Suggestion {

    private static final long serialVersionUID = 1L;

    private static final String PREFIX = "nxdoc";

    private static final String VIEW_ID = "view_documents";
   // private static final String GENERAL_DOCUMENT_TYPE = "GeneralDocument";
   // private static final String DOCUMENT_TYPE_CODE_PROPERTY = "gdoc:documentTypeCode";
   // private static final String DMS_FOLDER = "DmsFolder";
  //  private static final String DMS_FOLDER_DOCUMENT_TYPE_CODE_PROPERTY = "dmsfolder:documentTypeCode" ;
    private static final String FOLDER = "Folder";

    protected final DocumentLocation documentLocation;
    private final String typeLabel;
    private final Boolean isFolder;
    private final String referenceNumber;
    private final String assignmentType;
    private final String assignmentState;

    //private final String superType;
    //private final String documentTypeCode;


    private final String mimetype;

    public AssignmentDocumentLookupSuggestion(String id, DocumentLocation documentLocation, String type, String typeLabel, Boolean isFolder, String label, String iconURL, String mimetype, String referenceNumber, String assignmentType,String assignmentState) {
        super(id, type, label, iconURL);
       this.documentLocation = documentLocation;
        this.typeLabel= typeLabel;
        this.isFolder= isFolder;
        this.mimetype = mimetype;
        this.referenceNumber = referenceNumber;
        this.assignmentType = assignmentType;
        this.assignmentState = assignmentState;

        // this.superType=superType;
        //this.documentTypeCode=documentTypeCode;

    }

    public static Suggestion fromDocumentModel(DocumentModel doc) {
        TypeInfo typeInfo = doc.getAdapter(TypeInfo.class);
        String description = doc.getProperty("dc:description").getValue(String.class);
        String refNumber = doc.getProperty("cts_common:referenceNumber").getValue(String.class);
        String subject= doc.getProperty("cts_common:title").getValue(String.class);
        //String type= doc.getType(String.class);
        String assignmentType= doc.getProperty("assign:typee").getValue(String.class);
        String state= doc.getProperty("assignState:state").getValue(String.class);
        String icon = null;
        if (doc.hasSchema("common")) {
            icon = (String) doc.getProperty("common", "icon");
        }
        if (StringUtils.isEmpty(icon)) {
            icon = typeInfo.getIcon();
        }
        String thumbnailURL = String.format("api/v1/id/%s/@rendition/thumbnail", doc.getId());
        String mimetype = null;
        if (doc.hasSchema("file"))
        {
            Blob content = (Blob) doc.getPropertyValue("file:content");
            if(content!= null)
            {
                mimetype = content.getMimeType();
            }

        }
        @SuppressWarnings("unchecked")
        Map<String, List<String>> highlights = (Map<String, List<String>>) doc.getContextData(
                PageProvider.HIGHLIGHT_CTX_DATA);

//        String superType="";
//        String documentTypeCode="";
//        if(doc.getDocumentType().getName().equals(GENERAL_DOCUMENT_TYPE)||doc.getDocumentType().getSuperType().getName().equals(GENERAL_DOCUMENT_TYPE))
//        {
//            superType = GENERAL_DOCUMENT_TYPE;
//            if(doc.getProperty(DOCUMENT_TYPE_CODE_PROPERTY)!= null)
//            {
//                documentTypeCode = String.valueOf(doc.getProperty(DOCUMENT_TYPE_CODE_PROPERTY).getValue());
//            }
//        }
//        else
//        {
//            if(doc.getDocumentType().getName().equals(DMS_FOLDER)||doc.getDocumentType().getSuperType().getName().equals(DMS_FOLDER))
//            {
//                superType = DMS_FOLDER;
//                if(doc.getProperty(DMS_FOLDER_DOCUMENT_TYPE_CODE_PROPERTY)!= null)
//                {
//                    documentTypeCode = String.valueOf(doc.getProperty(DMS_FOLDER_DOCUMENT_TYPE_CODE_PROPERTY).getValue());
//                }
//            }
//
//        }


        return new AssignmentDocumentLookupSuggestion(doc.getId(),new DocumentLocationImpl(doc), typeInfo.getId(),typeInfo.getLabel(),doc.isFolder(), subject, icon,mimetype,refNumber,assignmentType,state).withDescription(
                description).withThumbnailURL(thumbnailURL).withHighlights(highlights);
    }

//    public DocumentLocation getDocumentLocation() {
//        return documentLocation;
//    }


    public String getObjectUrl() {
        if (documentLocation != null) {
            List<String> items = new ArrayList<>();
            items.add(PREFIX);
            items.add(documentLocation.getServerName());
            IdRef docRef = documentLocation.getIdRef();
            if (docRef == null) {
                return null;
            }
            items.add(docRef.toString());
            items.add(VIEW_ID);

            String uri = StringUtils.join(items, "/");
            return uri;
        }
        return null;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public Boolean isFolder() {
        return isFolder;
    }

    public String getMimetype() {
        return mimetype;
    }


//
//    public String getSuperType() {
//        return superType;
//    }
//
//    public String getDocumentTypeCode() {
//        return documentTypeCode;
//    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public Object getAssignmentState() { return  assignmentState;
    }

    public Object getAssignmentType() { return assignmentType;
    }
}


