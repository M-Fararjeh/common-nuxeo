package sa.comptechco.nuxeo.common.services.api;

import sa.comptechco.nuxeo.common.services.model.ActionEnum;
import sa.comptechco.nuxeo.common.services.model.RestrictionsModel;

import java.util.List;

public interface CustomRestrictionsService {

    RestrictionsModel getRestrictionsModel();
    Boolean checkPathOrTypeAllowed(String path, String docType, List<String> userGroups, ActionEnum action);
}
