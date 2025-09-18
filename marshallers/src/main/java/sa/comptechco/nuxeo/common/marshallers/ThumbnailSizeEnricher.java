package sa.comptechco.nuxeo.common.marshallers;

import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.thumbnail.ThumbnailService;
import org.nuxeo.ecm.core.io.marshallers.json.enrichers.AbstractJsonEnricher;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;
import org.nuxeo.runtime.api.Framework;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.OVERRIDE_REFERENCE;

@Setup(mode = SINGLETON, priority = OVERRIDE_REFERENCE)
public class ThumbnailSizeEnricher extends AbstractJsonEnricher<DocumentModel> {

    static final Logger log = LogManager.getLogger(ThumbnailSizeEnricher.class);
    public static final String NAME = "thumbnailSize";

    public ThumbnailSizeEnricher() {
        super(NAME);
    }

    @Override
    public void write(JsonGenerator jg, DocumentModel enrichedDoc) throws IOException {

        try {
            if (enrichedDoc.getId() != null && enrichedDoc.getDocumentType() != null) {
                ThumbnailService thumbnailService = Framework.getService(ThumbnailService.class);
                Blob thumbnailBlob = thumbnailService.getThumbnail(enrichedDoc, enrichedDoc.getCoreSession());
                BufferedImage thumbnail = ImageIO.read(thumbnailBlob.getStream());
                if (thumbnail != null) {
                    jg.writeFieldName("thumbnailWidth");
                    jg.writeNumber(thumbnail.getWidth());
                    jg.writeFieldName("thumbnailHeight");
                    jg.writeNumber(thumbnail.getHeight());
                } else {
//                    jg.writeFieldName("thumbnailWidth");
//                    jg.writeNumber(0);
//                    jg.writeFieldName("thumbnailHeight");
//                    jg.writeNumber(0);
                }
            }
        } catch (Exception e) {
            log.error(e.getLocalizedMessage());
//            e.printStackTrace();
            try {
                ThumbnailService thumbnailService = Framework.getService(ThumbnailService.class);
                Blob thumbnailBlob = thumbnailService.computeThumbnail(enrichedDoc, enrichedDoc.getCoreSession());
                BufferedImage thumbnail = ImageIO.read(thumbnailBlob.getStream());
                if (thumbnail != null) {
                    jg.writeFieldName("thumbnailWidth");
                    jg.writeNumber(thumbnail.getWidth());
                    jg.writeFieldName("thumbnailHeight");
                    jg.writeNumber(thumbnail.getHeight());
                }
            } catch (Exception ee) {
                log.error(ee.getLocalizedMessage());
            }
        }

    }

}
