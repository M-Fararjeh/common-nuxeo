package org.nuxeo.extended;

public class KeycloakUserCredentials {

    private String type;
    private String value;
    private Boolean temporary;

    public KeycloakUserCredentials(String type, String value, Boolean temporary) {
        this.type = type;
        this.value = value;
        this.temporary = temporary;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Boolean getTemporary() {
        return temporary;
    }

    public void setTemporary(Boolean temporary) {
        this.temporary = temporary;
    }
}
