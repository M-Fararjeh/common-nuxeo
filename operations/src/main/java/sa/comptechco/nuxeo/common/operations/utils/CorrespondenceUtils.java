package sa.comptechco.nuxeo.common.operations.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.mvel2.MVEL;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import org.nuxeo.ecm.multi.tenant.MultiTenantService;

public class CorrespondenceUtils {
    static Logger log = LoggerFactory.getLogger(CorrespondenceUtils.class);

    private static String MESSAGE_AR_PATH = "/default-domain/workspaces/CTS/Translations/messages_ar.json";
    private static String MESSAGE_EN_PATH = "/default-domain/workspaces/CTS/Translations/messages_en.json";

    private static String TENANT_MESSAGE_AR_PATH="/setting-domain/workspaces/DomainsSettings/messages_ar.json";

    private static String TENANT_MESSAGE_EN_PATH="/setting-domain/workspaces/DomainsSettings/messages_en.json";

    public static String getFormatDatePropertyValue(DocumentModel nxDocument, String property,
                                                    String format, String MVELEXP,
                                                    CoreSession session, OperationContext ctx) {
        if (format == null || format.length() == 0) {
            // apply default format
            format = "dd/mm/yyyy hh:mm:ss";
        }
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        GregorianCalendar creationDate = (GregorianCalendar) nxDocument.getPropertyValue(property);
        return sdf.format(creationDate.getTime());
    }

    public static String getStringPropertyValue(DocumentModel nxDocument, String property,
                                                    String format, String MVELEXP,
                                                    CoreSession session, OperationContext ctx) {
        String propertyValue = (String) nxDocument.getPropertyValue(property);
        if (MVELEXP.trim().length() > 0) {
            Serializable compiled = MVEL.compileExpression(MVELEXP);
            Serializable sValue = MVEL.executeExpression(compiled, propertyValue, Serializable.class);
            return sValue.toString();
        }
        return propertyValue;
    }

    public static String getCreationDateFormatted(DocumentModel nxDocument, String local,
                                                  String format, String MVELEXP,
                                                  CoreSession session, OperationContext ctx) {
        if (format == null || format.length() == 0) {
            // apply default format
            format = "dd/mm/yyyy hh:mm:ss";
        }
        if (local != null && (local.toLowerCase().equals("hij") || local.toLowerCase().equals("hijri"))) {
            // return hijri date  corr:hDocumentDate
            return getStringPropertyValue(nxDocument, "corr:hDocumentDate", format, MVELEXP, session, ctx);
        } else {
            // return gregorian date corr:gDocumentDate
            return getFormatDatePropertyValue(nxDocument, "corr:gDocumentDate", format, MVELEXP, session, ctx);
        }
    }

    public static String getDueDateFormatted(DocumentModel nxDocument, String local, String format, String mvelexpr, CoreSession session, OperationContext ctx) {
        if (format == null || format.length() == 0) {
            // apply default format
            format = "dd/mm/yyyy hh:mm:ss";
        }
        if (local != null && (local.toLowerCase().equals("hij") || local.toLowerCase().equals("hijri"))) {
            return getStringPropertyValue(nxDocument, "corr:hDueDate", format, mvelexpr, session, ctx);
        } else {
            return getFormatDatePropertyValue(nxDocument, "corr:gDueDate", format, mvelexpr, session, ctx);
        }
    }

    public static String getLocalizedVocabularyValue(String vocabularyId, String value, String local, CoreSession session, OperationContext ctx) {
        DocumentModel messagesDocument = session.getDocument(new PathRef(local.toUpperCase().compareTo("EN") == 0 ? MESSAGE_EN_PATH : MESSAGE_AR_PATH));

        MultiTenantService multiTenantService = Framework.getService(MultiTenantService.class);

        if (!multiTenantService.isTenantIsolationEnabled(session)) {
            messagesDocument = session.getDocument(new PathRef(local.toUpperCase().compareTo("EN") == 0 ? TENANT_MESSAGE_EN_PATH : TENANT_MESSAGE_AR_PATH));

        }

         if (messagesDocument != null) {
            try {
                String messageJsonStr = (String) messagesDocument.getPropertyValue("note:note");
                if (messageJsonStr == null || messageJsonStr.trim().length() == 0) {
                    return value;
                }
                Gson gson = new Gson();
                JsonObject messagesObject = gson.fromJson(messageJsonStr, JsonObject.class);
                JsonObject vocObject = messagesObject.getAsJsonObject("vocabulary").getAsJsonObject(vocabularyId);
                if (vocObject.has(value)) {
                    return vocObject.get(value).getAsString();
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        return value;
    }
}
