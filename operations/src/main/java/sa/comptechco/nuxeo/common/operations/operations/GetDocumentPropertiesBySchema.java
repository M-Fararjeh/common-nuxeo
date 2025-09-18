package sa.comptechco.nuxeo.common.operations.operations;

import com.fasterxml.jackson.core.JsonFactory;
        import com.fasterxml.jackson.core.JsonGenerator;
        import org.apache.commons.logging.Log;
        import org.apache.commons.logging.LogFactory;
        import org.json.JSONException;
        import org.nuxeo.ecm.automation.OperationException;
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
        import java.security.InvalidKeyException;
        import java.security.NoSuchAlgorithmException;
        import java.util.Map;

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

        StringWriter out = new StringWriter();
        JsonFactory factory = new JsonFactory();
        JsonGenerator jg = factory.createGenerator(out);
        MarshallerRegistry registry = Framework.getService(MarshallerRegistry.class);
        RenderingContext ctx = RenderingContext.CtxBuilder.get();
        ctx.setExistingSession(input.getCoreSession());
        Writer<Property> propertyWriter = registry.getWriter(ctx, Property.class, MediaType.APPLICATION_JSON_TYPE);
        OutputStream outputStream = new OutputStreamWithJsonWriter(jg);
        jg.writeStartObject();

        for(String schemaName : schemasNames) {
            Map<String, Object> properties = input.getProperties(schemaName);
            if (properties != null && !properties.isEmpty())
                for (String propName : properties.keySet()) {
                    Property property = input.getProperty(propName);
                    if (ignorePrefix != null && ignorePrefix)
                        jg.writeFieldName(propName.replace(propName.substring(0, propName.lastIndexOf(":") + 1), ""));
                    else
                        jg.writeFieldName(propName);
                    propertyWriter.write(property, Property.class, Property.class, MediaType.APPLICATION_JSON_TYPE, outputStream);
                }
        }
        jg.writeEndObject();
        out.close();
        outputStream.close();
        jg.close();
        return new StringBlob(out.getBuffer().toString(), "application/json");
    }

}