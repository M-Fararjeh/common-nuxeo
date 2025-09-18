package sa.comptechco.nuxeo.common.operations.operations;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.io.marshallers.json.OutputStreamWithJsonWriter;
import org.nuxeo.ecm.core.io.registry.MarshallerRegistry;
import org.nuxeo.ecm.core.io.registry.Writer;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.runtime.api.Framework;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Map;

/**
 * Operation to fetch document properties by schema names.
 * 
 * This operation extracts properties from specified schemas and returns them as JSON,
 * with optional prefix removal for cleaner property names.
 */
@Operation(id = "Document.FetchPropertiesBySchema", category = Constants.CAT_DOCUMENT, label = "FetchPropertiesBySchema", description = "Fetch properties of document by schema name")
public class GetDocumentPropertiesBySchema {

    private static final Log logger = LogFactory.getLog(GetDocumentPropertiesBySchema.class);
    public static final String ID = "Document.FetchPropertiesBySchema";

    @Param(name = "schemasNames")
    protected StringList schemasNames;

    @Param(name = "ignorePrefix", required = false)
    protected Boolean ignorePrefix;

    @Context
    protected CoreSession session;

    @OperationMethod
    public StringBlob run(DocumentModel input) throws IOException {
        try {
            String jsonResult = extractPropertiesAsJson(input);
            return new StringBlob(jsonResult, "application/json");
        } catch (Exception e) {
            logger.error("Failed to fetch properties by schema for document: " + input.getId(), e);
            throw new NuxeoException("Failed to fetch document properties", e);
        }
    }

    /**
     * Extracts properties from specified schemas and converts to JSON.
     */
    private String extractPropertiesAsJson(DocumentModel input) throws IOException {
        StringWriter stringWriter = new StringWriter();
        JsonFactory jsonFactory = new JsonFactory();
        
        try (JsonGenerator jsonGenerator = jsonFactory.createGenerator(stringWriter)) {
            RenderingContext renderingContext = createRenderingContext(input);
            Writer<Property> propertyWriter = getPropertyWriter(renderingContext);
            
            writePropertiesToJson(input, jsonGenerator, propertyWriter, renderingContext);
            
            return stringWriter.toString();
        } finally {
            stringWriter.close();
        }
    }

    /**
     * Creates rendering context for property marshalling.
     */
    private RenderingContext createRenderingContext(DocumentModel input) {
        RenderingContext context = RenderingContext.CtxBuilder.get();
        context.setExistingSession(input.getCoreSession());
        return context;
    }

    /**
     * Gets the property writer for JSON marshalling.
     */
    private Writer<Property> getPropertyWriter(RenderingContext context) {
        MarshallerRegistry registry = Framework.getService(MarshallerRegistry.class);
        return registry.getWriter(context, Property.class, MediaType.APPLICATION_JSON_TYPE);
    }

    /**
     * Writes all properties from specified schemas to JSON.
     */
    private void writePropertiesToJson(DocumentModel input, JsonGenerator jsonGenerator, 
                                     Writer<Property> propertyWriter, RenderingContext context) throws IOException {
        jsonGenerator.writeStartObject();
        
        try (OutputStream outputStream = new OutputStreamWithJsonWriter(jsonGenerator)) {
            for (String schemaName : schemasNames) {
                writeSchemaProperties(input, schemaName, jsonGenerator, propertyWriter, outputStream);
            }
        }
        
        jsonGenerator.writeEndObject();
    }

    /**
     * Writes properties from a single schema to JSON.
     */
    private void writeSchemaProperties(DocumentModel input, String schemaName, JsonGenerator jsonGenerator,
                                     Writer<Property> propertyWriter, OutputStream outputStream) throws IOException {
        Map<String, Object> properties = input.getProperties(schemaName);
        
        if (properties == null || properties.isEmpty()) {
            return;
        }

        for (String propertyName : properties.keySet()) {
            writeProperty(input, propertyName, jsonGenerator, propertyWriter, outputStream);
        }
    }

    /**
     * Writes a single property to JSON with optional prefix removal.
     */
    private void writeProperty(DocumentModel input, String propertyName, JsonGenerator jsonGenerator,
                             Writer<Property> propertyWriter, OutputStream outputStream) throws IOException {
        Property property = input.getProperty(propertyName);
        String fieldName = shouldIgnorePrefix() ? removePropertyPrefix(propertyName) : propertyName;
        
        jsonGenerator.writeFieldName(fieldName);
        propertyWriter.write(property, Property.class, Property.class, MediaType.APPLICATION_JSON_TYPE, outputStream);
    }

    /**
     * Checks if property prefixes should be ignored.
     */
    private boolean shouldIgnorePrefix() {
        return ignorePrefix != null && ignorePrefix;
    }

    /**
     * Removes schema prefix from property name (e.g., "dc:title" becomes "title").
     */
    private String removePropertyPrefix(String propertyName) {
        int colonIndex = propertyName.lastIndexOf(":");
        if (colonIndex > 0 && colonIndex < propertyName.length() - 1) {
            return propertyName.substring(colonIndex + 1);
        }
        return propertyName;
    }
}