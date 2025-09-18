package org.nuxeo.extended.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.api.model.Property;

import java.util.HashMap;
import java.util.Map;

public class CustomRun extends UnrestrictedSessionRunner {

    private String result;

    protected String id;

    protected String schema;

    public CustomRun(String repositoryName, String id, String schema) {
        super(repositoryName);
        this.id = id;
        this.schema = schema;
    }


    @Override
    public void run() {
        DocumentModel doc = session.getDocument(new IdRef(id));
        Map<String, Object> map = doc.getProperties(schema);
        Map<String, Object> correctedMap = new HashMap<>();
        Map<String,Object> extendedMap =  new HashMap<>();
        if(doc.hasSchema("assignment"))
        {
            String correspondenceId = (String) doc.getProperty("cts_common:correspondence").getValue();
            DocumentModel correspondence = session.getDocument(new IdRef(correspondenceId));
            if(correspondence!= null) {
                extendedMap = correspondence.getProperties("Correspondence");

                extendedMap.put("CorrespondenceId", doc.getId());
                extendedMap.put("CorrespondenceType", doc.getType());
                extendedMap.put("CorrespondenceTitle", doc.getTitle());
                extendedMap.put("CorrespondenceState", doc.getCurrentLifeCycleState());
                extendedMap.put("CorrespondencePath", doc.getPath().toString());
                extendedMap.put("CorrespondenceCreator", doc.getPropertyValue("dc:creator"));
            }
        }
        if (map == null) {
            map = new HashMap<>();
        }
        for (Map.Entry<String, Object> entry: map.entrySet()) {
            correctedMap.put(entry.getKey().replace(":",""),entry.getValue());
        }
        for (Map.Entry<String, Object> entry: extendedMap.entrySet()) {
            correctedMap.put(entry.getKey().replace(":",""),entry.getValue());
        }
        correctedMap.put("id", doc.getId());
        correctedMap.put("type", doc.getType());
        correctedMap.put("title", doc.getTitle());
        correctedMap.put("state", doc.getCurrentLifeCycleState());
        correctedMap.put("path", doc.getPath().toString());
        correctedMap.put("creator", doc.getPropertyValue("dc:creator"));
        String mimetype = null;
        if (doc.hasSchema("file"))
        {
            Blob content = (Blob) doc.getPropertyValue("file:content");
            if(content!= null)
            {
                mimetype = content.getMimeType();
                correctedMap.put("mimeType", mimetype);
            }

        }
        ObjectMapper objectMapper = new ObjectMapper();
        result = "";
        try {
            String json = objectMapper.writeValueAsString(correctedMap);
            result=json;
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

    }

    public String getResult() {
        return result;
    }
}
