package sa.comptechco.nuxeo.common.services.model;

public class TypeRestriction extends AbstractRestriction {
    protected String docType;

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }
}
