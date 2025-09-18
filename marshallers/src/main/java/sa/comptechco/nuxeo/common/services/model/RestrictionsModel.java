package sa.comptechco.nuxeo.common.services.model;

import java.util.List;

public class RestrictionsModel {

    private List<PathRestriction> pathRestrictions;
    private List<TypeRestriction> typeRestrictions;

    public List<PathRestriction> getPathRestrictions() {
        return pathRestrictions;
    }

    public void setPathRestrictions(List<PathRestriction> pathRestrictions) {
        this.pathRestrictions = pathRestrictions;
    }

    public List<TypeRestriction> getTypeRestrictions() {
        return typeRestrictions;
    }

    public void setTypeRestrictions(List<TypeRestriction> typeRestrictions) {
        this.typeRestrictions = typeRestrictions;
    }
}
