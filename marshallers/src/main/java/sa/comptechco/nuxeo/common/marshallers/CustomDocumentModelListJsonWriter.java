package sa.comptechco.nuxeo.common.marshallers;

import com.fasterxml.jackson.core.JsonGenerator;
import org.nuxeo.ecm.automation.core.util.PaginableDocumentModelList;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.io.marshallers.json.document.DocumentModelListJsonWriter;
import org.nuxeo.ecm.core.io.registry.reflect.Instantiations;
import org.nuxeo.ecm.core.io.registry.reflect.Priorities;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;
import org.nuxeo.runtime.api.Framework;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Pattern;

import static org.nuxeo.ecm.automation.core.util.PaginableDocumentModelList.CODEC_PARAMETER_NAME;

@Setup(mode = Instantiations.SINGLETON, priority = Priorities.OVERRIDE_REFERENCE) // <= an higher priority is used
public class CustomDocumentModelListJsonWriter extends DocumentModelListJsonWriter {
    String CONTEXT_PATH = "/nuxeo";
    String CONTEXT_PATH_PROP = "org.nuxeo.ecm.contextPath";

    @Override
    public void write(List<DocumentModel> documents, JsonGenerator jg) throws IOException {

        NuxeoPrincipal principal = null;
        CoreSession session = null;
        try {
            session = ctx.getSession(null).getSession();
            principal = session.getPrincipal();
        } catch (Exception e) {
            // TODO: handle exception
        }
        
        try {
            CONTEXT_PATH = Framework.getProperty(CONTEXT_PATH_PROP, "/nuxeo");
        } catch (Exception e) {
            CONTEXT_PATH = "/nuxeo";
        }
        Pattern urlPattern = Pattern.compile("https?://[^/]+"+CONTEXT_PATH+"(/.*)?$");

//       /.../.../Departments Hierarchy
//       /.../.../.../CTS
//       /.../.../.../Meeting management
        Pattern departmentsHierarchyPattern = Pattern.compile("/[^/]+/Departments Hierarchy(/.*)?$");
        Pattern ctsPattern = Pattern.compile("/[^/]+/[^/]+/CTS(/.*)?$");
        Pattern meetingManagementPattern = Pattern.compile("/[^/]+/[^/]+/Meeting management(/.*)?$");

        String referer = "";
        if (ctx.getAllParameters().get("referer") != null && ctx.getAllParameters().get("referer").size() > 0) {
            referer = (String) ctx.getAllParameters().get("referer").get(0);
        }
//        System.out.println("=========================== [42] CustomDocumentModelListJsonWriter:: Referer: " + referer + " ===========================================");
        if (urlPattern.matcher(referer).matches() && principal != null && !principal.isAdministrator() && documents.size() > 0) {
            // TODO: fix empty list item caused by invalid  PaginableDocumentModelList properties after deleting docs
            ListIterator<DocumentModel> itr = documents.listIterator();
            while(itr.hasNext()) {
                DocumentModel doc = itr.next();
                String docPath = doc.getPath() != null ? doc.getPath().toString() : "";

                if (departmentsHierarchyPattern.matcher(docPath).matches()
                    || ctsPattern.matcher(docPath).matches()
                    || meetingManagementPattern.matcher(docPath).matches()) {
//                    itr.remove();
                    DocumentModel document = session.createDocumentModel(doc.getType());
                    document.setPropertyValue("dc:title", "Hidden Content");
                    itr.set(document);
                }
            }
        }

        if (documents instanceof PaginableDocumentModelList) {
            PaginableDocumentModelList paginable = (PaginableDocumentModelList) documents;
            String codecName = paginable.getDocumentLinkBuilder();
            try (Closeable resource = ctx.wrap().with(CODEC_PARAMETER_NAME, codecName).open()) {
                super.write(documents, jg);
            }
        } else {
            super.write(documents, jg);
        }


    }

}
