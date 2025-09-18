package org.nuxeo.ecm.restapi.server.jaxrs.extended;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.ModuleRoot;
import org.nuxeo.extended.utils.CustomRun;
import org.nuxeo.extended.utils.CustomUnrestrictedRun;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@WebObject(type = "customOpen")
public class OpenApi extends ModuleRoot {

    @GET
    @Path("document/{id}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
    public Object getDocumentDetails(@PathParam("id") String id, @QueryParam("schema") String schema) throws JsonProcessingException {
        CustomRun customRun = new CustomRun(null, id, schema);
        customRun.runUnrestricted();
        return customRun.getResult();
    }

    @GET
    @Path("document-content/{id}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
    public DocumentModel getDocumentDetailsWithContent(@PathParam("id") String id){
        CustomUnrestrictedRun customUnrestrictedRun= new CustomUnrestrictedRun(null, id,false);
        customUnrestrictedRun.runUnrestricted();
        return customUnrestrictedRun.getDocument();

    }

    @GET
    @Path("document-content/{id}/{xpath}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
    public Blob getDocumentContent(@PathParam("id") String id,@PathParam("xpath") String xPath)  {

        CustomUnrestrictedRun customUnrestrictedRun= new CustomUnrestrictedRun(null, id,true,xPath);
        customUnrestrictedRun.runUnrestricted();
        return customUnrestrictedRun.getBlob();
    }
}



