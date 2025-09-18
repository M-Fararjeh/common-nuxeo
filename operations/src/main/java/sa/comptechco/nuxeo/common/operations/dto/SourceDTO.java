package  sa.comptechco.nuxeo.common.operations.dto;


import java.util.Map;

public class SourceDTO {
    private String sourceId;

    private String sourceType;

    private String sourceName;

    private Map<String, Object> data;

    private String application;

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    @Override
    public String toString() {
        return "SourceDTO{" +
            "sourceId='" + sourceId + '\'' +
            ", sourceType='" + sourceType + '\'' +
            ", sourceName='" + sourceName + '\'' +
            ", data=" + data +
            ", application='" + application + '\'' +
            '}';
    }
}


