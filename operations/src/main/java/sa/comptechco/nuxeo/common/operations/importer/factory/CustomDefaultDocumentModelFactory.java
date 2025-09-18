package sa.comptechco.nuxeo.common.operations.importer.factory;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.platform.importer.factories.DefaultDocumentModelFactory;
import org.nuxeo.ecm.platform.importer.source.SourceNode;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;

public class CustomDefaultDocumentModelFactory extends DefaultDocumentModelFactory {
    private final String imagesLeafType = "Picture";
    private final String videosLeafType = "Video";

    public static final String KEEP_IMAGES_AND_VIDEOS_TYPES_ENABLE = "org.nuxeo.importer.preserve_images_videos_types";
    public static final String PROP_FACETS_TO_BE_ADDED = "org.nuxeo.importer.image_video.facets.add";
    public static Boolean KEEP_IMAGES_AND_VIDEOS_TYPES=false;
    public static String[] FACETS_TO_BE_ADDED;

    private Boolean isKeepImageVideoTypesPropertyUpdated = false;
    private Boolean isFacetsToAddUpdated = false;
    public CustomDefaultDocumentModelFactory() {
        super();
    }

    public CustomDefaultDocumentModelFactory(String folderishType, String leafType) {
        super(folderishType, leafType);
    }
    public DocumentModel createLeafNode(CoreSession session, DocumentModel parent, SourceNode node) throws IOException {
        return this.customCreateLeafNode(session, parent, node);
    }

    protected DocumentModel customCreateLeafNode(CoreSession session, DocumentModel parent, SourceNode node) throws IOException {
        Blob blob = null;
        Map<String, Serializable> props = null;
        String leafTypeToUse = this.leafType;
        if (!isKeepImageVideoTypesPropertyUpdated) {
            try {
                KEEP_IMAGES_AND_VIDEOS_TYPES = Boolean.valueOf(Framework.getProperty(KEEP_IMAGES_AND_VIDEOS_TYPES_ENABLE, "false"));
                isKeepImageVideoTypesPropertyUpdated = true;
            } catch (Exception e) {

            }
        }
        if (KEEP_IMAGES_AND_VIDEOS_TYPES) {
            leafTypeToUse = getLeafTypeFromExtension(node.getName()) ;
        }
        BlobHolder bh = node.getBlobHolder();
        String fileName;
        if (bh != null) {
            blob = bh.getBlob();
            props = bh.getProperties();
            fileName = this.getDocTypeToUse(bh);
            if (fileName != null) {
                leafTypeToUse = fileName;
            }
        }

        fileName = node.getName();
        String name = this.getValidNameFromFileName(fileName);
        DocumentModel doc = session.createDocumentModel(parent.getPathAsString(), name, leafTypeToUse);
        Iterator var11 = this.getFacetsToUse(bh).iterator();

        while(var11.hasNext()) {
            String facet = (String)var11.next();
            doc.addFacet(facet);
        }

        if (leafTypeToUse.compareTo(leafType) != 0) {
            if(!isFacetsToAddUpdated) {
                try {
                    FACETS_TO_BE_ADDED = Framework.getProperty(PROP_FACETS_TO_BE_ADDED, "").trim().split("\\s*,\\s*");
                    isFacetsToAddUpdated = true;
                } catch (Exception e) {

                }
            }
            if (FACETS_TO_BE_ADDED != null && FACETS_TO_BE_ADDED.length > 0) {
                for (String facet: FACETS_TO_BE_ADDED ) {
                    // add GeneralDocumentFacet
                    if (facet != null && facet.length() > 0) {
                        doc.addFacet(facet);
                    }
                }
            }
        }

        doc.setProperty("dublincore", "title", node.getName());
        if (blob != null && blob.getLength() > 0L) {
            blob.setFilename(fileName);
            doc.setProperty("file", "content", blob);
        }

        doc = session.createDocument(doc);
        if (props != null) {
            doc = this.setDocumentProperties(session, props, doc);
        }

        return doc;
    }


    protected String getDocTypeToUse(BlobHolder inBH) {
        String type = null;
        if (inBH != null) {
            Map<String, Serializable> props = inBH.getProperties();
            if (props != null) {
                type = (String)props.get("ecm:primaryType");
                if (type != null && type.isEmpty()) {
                    type = null;
                }
            }
        }

        return type;
    }

    private String getLeafTypeFromExtension(String filename) {
        if (filename == null) {
            return leafType;
        }
        String name = filename.toLowerCase();
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif")) {
            return imagesLeafType;
        } else if (name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".mov") || name.endsWith(".wmv") || name.endsWith(".flv")) {
            return videosLeafType;
        }
        return leafType;
    }
}
