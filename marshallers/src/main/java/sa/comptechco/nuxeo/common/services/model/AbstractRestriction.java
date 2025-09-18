package sa.comptechco.nuxeo.common.services.model;

import java.util.List;

public abstract class AbstractRestriction {

    protected ModeEnum mode;
    protected List<ActionEnum> actions;
    protected List<String> groups;

    public ModeEnum getMode() {
        return mode;
    }

    public void setMode(ModeEnum mode) {
        this.mode = mode;
    }

    public List<ActionEnum> getActions() {
        return actions;
    }

    public void setActions(List<ActionEnum> actions) {
        this.actions = actions;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }
}
