package sa.comptechco.nuxeo.common.operations.provider;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.QuickFilter;
import org.nuxeo.ecm.platform.query.api.WhereClauseDefinition;
import org.nuxeo.ecm.platform.query.nxql.NXQLQueryBuilder;
import org.nuxeo.elasticsearch.provider.ElasticSearchNxqlPageProvider;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.api.login.NuxeoLoginContext;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class HubCorrespondencesPageProvider extends ElasticSearchNxqlPageProvider {
   /* @Override
    public List<DocumentModel> getCurrentPage() {
      *//*  CoreSession coreSession = getCoreSession();
        String tenantId = coreSession.getPrincipal().getTenantId();
        String communicationManagerRole = "tenant_"+tenantId+"_cts_role_Communication_Manager";
        if(coreSession.getPrincipal().getTenantId()!=null && coreSession.getPrincipal().getGroups().contains(communicationManagerRole))
        {
            //  run as admin
                return super.getCurrentPage();
        }
        else {
            DocumentModelList documentModels = new DocumentModelListImpl();
            return documentModels;
        }
    }*/
    @Override
    protected CoreSession getCoreSession() {
        Map<String, Serializable> props = getProperties();
        CoreSession coreSession = (CoreSession) props.get(CORE_SESSION_PROPERTY);
        if (coreSession == null) {
            throw new NuxeoException("cannot find core session");
        }

        String tenantId = coreSession.getPrincipal().getTenantId();
        String communicationManagerRole = "tenant_"+tenantId+"_cts_role_Communication_Manager";
        if(coreSession.getPrincipal().getTenantId()!=null && coreSession.getPrincipal().getGroups().contains(communicationManagerRole))
        {
            try (NuxeoLoginContext loginContext = Framework.loginSystem(coreSession.getPrincipal().getOriginatingUser())) {
                coreSession = CoreInstance.getCoreSession(coreSession.getRepositoryName());

            }
        }
        return coreSession;
    }
}
