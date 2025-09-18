package sa.comptechco.nuxeo.common.services.model;

public enum ActionEnum {
    all("all"), create("create"), create_child("create_child"), edit("edit"),
    edit_child("edit_child"), delete("delete"), delete_child("delete_child");

    private String value;

    ActionEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
