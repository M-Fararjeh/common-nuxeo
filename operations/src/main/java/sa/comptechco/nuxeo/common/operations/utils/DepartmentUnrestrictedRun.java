package sa.comptechco.nuxeo.common.operations.utils;

import org.apache.commons.collections.CollectionUtils;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.runtime.transaction.TransactionHelper;

public class DepartmentUnrestrictedRun extends UnrestrictedSessionRunner {

    protected DocumentModel department;


    protected String departmentName;


    public DepartmentUnrestrictedRun(String repositoryName, String departmentName) {
        super(repositoryName);
        this.departmentName = departmentName;
    }


    @Override
    public void run() {
        department = getDepartment(departmentName);
        if (department == null)
            department = getOSDepartment(departmentName);
    }

    public DocumentModel getDepartment() {
        return department;
    }


    private DocumentModel getDepartment(String deptName) {
        try {
            boolean tx = TransactionHelper.startTransaction();


            StringBuilder sb = new StringBuilder();
            sb.append("SELECT * FROM Department WHERE ");
            sb.append("dept:englishName");
            sb.append(" = ");
            sb.append(NXQL.escapeString(deptName));
            String q = sb.toString();

            DocumentModelList list = session.query(q);
            TransactionHelper.commitOrRollbackTransaction();
            if (!CollectionUtils.isEmpty(list)) {
                return list.get(0);
            } else {
                return null;
            }
        } catch (QueryParseException qe) {
            return null;
        }
    }

    public DocumentModel getOSDepartment(String deptName) {
        boolean tx = TransactionHelper.startTransaction();


        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM OSDepartment WHERE ");
        sb.append("osdept:englishName");
        sb.append(" = ");
        sb.append(NXQL.escapeString(deptName));
        String q = sb.toString();

        DocumentModelList list = session.query(q);
        TransactionHelper.commitOrRollbackTransaction();
        if (!CollectionUtils.isEmpty(list)) {
            return list.get(0);
        } else {
            return null;
        }
    }
}
