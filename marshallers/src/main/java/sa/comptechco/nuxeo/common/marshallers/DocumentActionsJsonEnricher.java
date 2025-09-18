package sa.comptechco.nuxeo.common.marshallers;

import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.OperationType;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.io.marshallers.json.enrichers.AbstractJsonEnricher;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.platform.types.localconfiguration.UITypesConfigurationConstants;
import org.nuxeo.runtime.api.Framework;
import sa.comptechco.nuxeo.common.services.api.CustomRestrictionsService;
import sa.comptechco.nuxeo.common.services.model.ActionEnum;

import java.io.IOException;
import java.util.*;

import static java.util.stream.Collectors.toSet;
import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.OVERRIDE_REFERENCE;

@Setup(mode = SINGLETON, priority = OVERRIDE_REFERENCE)
public class DocumentActionsJsonEnricher extends AbstractJsonEnricher<DocumentModel> {

    public static final String NAME = "actions";
    private static final String OPERATION_DOCUMENT_ACTIONS = "Document.DocumentActionsOperation";

    public DocumentActionsJsonEnricher() {
        super(NAME);
    }

    @Override
    public void write(JsonGenerator jg, DocumentModel enriched) throws IOException {

        jg.writeArrayFieldStart("actions");
        List<String> actions = getDocumentActions(enriched);

        for (String action : actions) {
                jg.writeString(action);
            }
            jg.writeEndArray();


    }

    private List<String> getDocumentActions(DocumentModel enriched) {
        AutomationService service = Framework.getService(AutomationService.class);
        try {

            OperationType operation = service.getOperation(OPERATION_DOCUMENT_ACTIONS);

            Map<String, Object> params = new HashMap<>();
            OperationContext operationContext = new OperationContext();
            operationContext.setCoreSession(ctx.getSession(null).getSession());
            operationContext.setInput(enriched);
            List<String> actions = (List<String>) service.run(operationContext, operation.getId(), params);
            return actions;

        } catch (OperationException e) {
            e.printStackTrace();
            throw new NuxeoException("failed to retrieve actions");

        }
    }

}
