package sa.comptechco.nuxeo.common.services.model;

public enum ModeEnum {
    allow("allow"), deny("deny");

    private String value;

    ModeEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
