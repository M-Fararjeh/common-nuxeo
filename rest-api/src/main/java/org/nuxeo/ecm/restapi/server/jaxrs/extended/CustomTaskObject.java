package org.nuxeo.ecm.restapi.server.jaxrs.extended;

import org.nuxeo.ecm.automation.core.util.PaginableDocumentModelList;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.restapi.server.jaxrs.QueryObject;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.extended.PaginableTaskList;
import org.nuxeo.extended.PaginableTaskListImpl;
import org.nuxeo.extended.utils.PaginableTaskMapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.List;


@WebObject(type = "custom-task")
public class CustomTaskObject extends QueryObject {

    @Context
    protected CoreSession session;


    @Context
    protected HttpServletRequest request;

    @Context
    protected HttpServletResponse response;


    @GET
    @Path("pp/{providerName}")
    public PaginableTaskList getTasks(@Context UriInfo uriInfo,
                                      @PathParam("providerName") String providerName) {

        DocumentModelList documentModelList= getQuery(uriInfo, providerName);
    return PaginableTaskMapper.ToTaskListPage((PaginableDocumentModelList) documentModelList,session);
    }
}
