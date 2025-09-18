package sa.comptechco.nuxeo.common.marshallers;

import com.fasterxml.jackson.core.JsonGenerator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.io.marshallers.json.enrichers.AbstractJsonEnricher;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.OVERRIDE_REFERENCE;

@Setup(mode = SINGLETON, priority = OVERRIDE_REFERENCE)
public class QuickAccessEnricher extends AbstractJsonEnricher<DocumentModel> {

    static final Logger log = LogManager.getLogger(QuickAccessEnricher.class);
    public static final String NAME = "quickAccess";
    public static final String DOC_TYPE = "Quick_Access";

    public QuickAccessEnricher() {
        super(NAME);
    }

    @Override
    public void write(JsonGenerator jg, DocumentModel enrichedDoc) throws IOException {

        if (enrichedDoc.getId() != null && enrichedDoc.getDocumentType() != null) {

            CoreSession session = ctx.getSession(null).getSession();
            IterableQueryResult result = session.queryAndFetch("SELECT * FROM Document WHERE " +
                    "ecm:isProxy = 0 AND ecm:isVersion = 0 AND ecm:isTrashed = 0 AND ecm:primaryType='" + DOC_TYPE + "' AND dc:title Like '%"+session.getPrincipal().getName()+"%'", "NXQL");
            if (result != null && result.size() != 0) {
                Iterator<Map<String, Serializable>> it = result.iterator();
                Map next = it.next();
                DocumentModel quickAccessCollection = session.getDocument(new IdRef(next.get("ecm:uuid").toString()));
                ArrayList<String> documentsIds = (ArrayList<String>) quickAccessCollection.getPropertyValue("collection:documentIds");
                if (documentsIds != null && documentsIds.size() != 0) {
                    boolean isFound = false;
                    for (String doc : documentsIds) {
                        //log.error("enrichedDoc.getId() is:" + enrichedDoc.getId());
                        // log.error("doc is:" + doc);
                        // log.error("doc.equals is:" + doc.equals(enrichedDoc.getId()));
                        //  log.error("quickAccessCollection.getTitle() is:" + quickAccessCollection.getTitle());
                        // log.error("session.getPrincipal().getName() is:" + session.getPrincipal().getName());
                        if (doc.equals(enrichedDoc.getId()) && quickAccessCollection.getTitle().contains(session.getPrincipal().getName())) {
                            // log.error("documentsId found is:" + doc);
                            jg.writeFieldName("isQuickAccess");
                            jg.writeBoolean(true);
                            isFound = true;
                        }
                    }
                    if (!isFound) {
                        jg.writeFieldName("isQuickAccess");
                        jg.writeBoolean(false);
                    }
                } else {
                    jg.writeFieldName("isQuickAccess");
                    jg.writeBoolean(false);
                }

            } else {
                jg.writeFieldName("isQuickAccess");
                jg.writeBoolean(false);

            }

        } else {
            jg.writeFieldName("isQuickAccess");
            jg.writeBoolean(false);
        }

    }

}
