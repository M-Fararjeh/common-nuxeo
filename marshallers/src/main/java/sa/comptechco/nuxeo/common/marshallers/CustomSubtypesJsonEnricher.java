package sa.comptechco.nuxeo.common.marshallers;

import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.commons.lang3.BooleanUtils;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
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
public class CustomSubtypesJsonEnricher extends AbstractJsonEnricher<DocumentModel> {

    public static final String NAME = "subtypes";

    public CustomSubtypesJsonEnricher() {
        super(NAME);
    }

    @Override
    public void write(JsonGenerator jg, DocumentModel enriched) throws IOException {
        SchemaManager schemaManager = Framework.getService(SchemaManager.class);
        Collection<String> subtypes = computeSubtypes(enriched);
        jg.writeFieldName(NAME);
        jg.writeStartArray();
        for (String subtype : subtypes) {
            jg.writeStartObject();
            jg.writeStringField("type", subtype);
            jg.writeArrayFieldStart("facets");
            for (String facet : schemaManager.getDocumentType(subtype).getFacets()) {
                jg.writeString(facet);
            }
            jg.writeEndArray();
            jg.writeEndObject();
        }
        jg.writeEndArray();
    }

    protected Collection<String> computeSubtypes(DocumentModel enriched) {
        CustomRestrictionsService restrictionsService = Framework.getService(CustomRestrictionsService.class);
        CoreSession session = ctx.getSession(null).getSession();

        // if create_child disabled for this user then no subtypes will be returned
        if (!restrictionsService.checkPathOrTypeAllowed(enriched.getPath().toString(), enriched.getType(),
                session.getPrincipal().getAllGroups(), ActionEnum.create_child)) {
          return new HashSet<>();
        }

        Collection<String> defaultSubtypes = enriched.getDocumentType().getAllowedSubtypes();
        if (enriched.hasFacet(UITypesConfigurationConstants.UI_TYPES_CONFIGURATION_FACET)) {
            defaultSubtypes = computeLocalConfigurationSubtypes(enriched, defaultSubtypes);
        }

        //now we need to check for every subtype if it is allowed to create for this user
        Collection<String> allowedSubtypes = new HashSet<>();
        for (String subtype: defaultSubtypes) {
            if (restrictionsService.checkPathOrTypeAllowed(null, subtype,
                    session.getPrincipal().getAllGroups(), ActionEnum.create)) {
                allowedSubtypes.add(subtype);
            }
        }

        return allowedSubtypes;
    }

    protected Collection<String> computeLocalConfigurationSubtypes(DocumentModel enriched,
                                                                   Collection<String> defaultSubtypes) {
        Boolean denyAllTypes = (Boolean) enriched.getPropertyValue(
                UITypesConfigurationConstants.UI_TYPES_CONFIGURATION_DENY_ALL_TYPES_PROPERTY);
        if (BooleanUtils.isNotTrue(denyAllTypes)) {
            String[] allowedTypesProperty = (String[]) enriched.getPropertyValue(
                    UITypesConfigurationConstants.UI_TYPES_CONFIGURATION_ALLOWED_TYPES_PROPERTY);
            String[] deniedTypesProperty = (String[]) enriched.getPropertyValue(
                    UITypesConfigurationConstants.UI_TYPES_CONFIGURATION_DENIED_TYPES_PROPERTY);
            List<String> allowedTypes = allowedTypesProperty == null ? Collections.emptyList()
                    : Arrays.asList(allowedTypesProperty);
            List<String> deniedTypes = deniedTypesProperty == null ? Collections.emptyList()
                    : Arrays.asList(deniedTypesProperty);
            return defaultSubtypes.stream()
                    .filter(s -> !deniedTypes.contains(s))
                    .filter(s -> allowedTypes.contains(s) || allowedTypes.isEmpty())
                    .collect(toSet());
        }
        return Collections.emptySet();
    }
}
