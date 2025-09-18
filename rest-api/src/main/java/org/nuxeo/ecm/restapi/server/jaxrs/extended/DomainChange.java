package org.nuxeo.ecm.restapi.server.jaxrs.extended;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.meeting.AutomationException;
import org.nuxeo.common.utils.ExceptionUtils;
import org.nuxeo.ecm.automation.*;
import org.nuxeo.ecm.automation.core.impl.ChainTypeImpl;
import org.nuxeo.ecm.automation.core.util.BlobList;
import org.nuxeo.ecm.automation.core.util.Paginable;
import org.nuxeo.ecm.automation.core.util.RecordSet;
import org.nuxeo.ecm.automation.jaxrs.DefaultJsonAdapter;
import org.nuxeo.ecm.automation.jaxrs.JsonAdapter;
import org.nuxeo.ecm.automation.jaxrs.io.operations.ExecutionRequest;
import org.nuxeo.ecm.automation.server.AutomationServer;
import org.nuxeo.ecm.automation.server.jaxrs.ResponseHelper;
import org.nuxeo.ecm.automation.server.jaxrs.RestOperationException;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.webengine.jaxrs.session.SessionFactory;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.exceptions.WebResourceNotFoundException;
import org.nuxeo.ecm.webengine.model.impl.DefaultObject;
import org.nuxeo.runtime.api.Framework;

import javax.mail.MessagingException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Cookie;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.core.NewCookie;

@WebObject(type = "domain-change")
public class DomainChange extends DefaultObject {


    private static final String CHANGE_DOMAIN_OPERATION = "AC_UA_ChangeDomainUser";
    @Context
    protected AutomationService service;

    @Context
    protected HttpServletRequest request;

    @Context
    protected HttpServletResponse response;

    @Context
    protected CoreSession session;

    protected OperationContext createContext(ExecutionRequest xreq) {
        return xreq.createContext(request, response, session);
    }

    @POST
    //@Path("{operationId}")
    public Object doPost(ExecutionRequest xreq) {
        OperationType operation = null;
        try {
            operation = service.getOperation(CHANGE_DOMAIN_OPERATION);
        } catch (OperationNotFoundException cause) {
            return new WebResourceNotFoundException("Failed to invoke operation: " + CHANGE_DOMAIN_OPERATION, cause);
        }
        try {
            AutomationServer srv = Framework.getService(AutomationServer.class);
            if (!srv.accept(getId(operation), isChain(operation), request)) {
                return ResponseHelper.notFound();
            }
            Object result = execute(xreq, operation);
            int customHttpStatus = xreq.getRestOperationContext().getHttpStatus();
            return getResponse(result, request, customHttpStatus,"JSESSIONID");
        } catch (OperationException | NuxeoException | MessagingException | IOException cause) {
            String exceptionMessage = "Failed to invoke operation: " + operation.getId();
            if (cause instanceof OperationNotFoundException) {
                throw new WebResourceNotFoundException(exceptionMessage, cause);

            } else if (cause instanceof NuxeoException) {
                NuxeoException nuxeoException = (NuxeoException) cause;
                //nuxeoException.addInfo(exceptionMessage);
                throw nuxeoException;
            } else {
                Throwable rootCause = ExceptionUtils.getRootCause(cause);
                if (rootCause instanceof AutomationException) {
                    AutomationException automationException = (AutomationException) rootCause;

                    try {
                        return Response.status(Response.Status.fromStatusCode(automationException.getHttpStatusCode()))
                                .entity(writeJSON(automationException)).type(MediaType.APPLICATION_JSON).build();
                    } catch (IOException e) {
                        exceptionMessage = "unable to write response ";
                        throw new NuxeoException(exceptionMessage, e);
                    }
                } else if (rootCause instanceof RestOperationException) {
                    int customHttpStatus = ((RestOperationException) rootCause).getStatus();
                    throw new NuxeoException(exceptionMessage, cause, customHttpStatus);
                }
                throw new NuxeoException(exceptionMessage, cause);
            }
        }
    }


    public String getId(OperationType operation) {
        return operation.getId();
    }


    public boolean isChain(OperationType operation) {
        return operation instanceof ChainTypeImpl;
    }


    public Object execute(ExecutionRequest xreq, OperationType operation) throws OperationException {
        return service.run(createContext(xreq), operation.getId(), xreq.getParams());

    }
    private String writeJSON(AutomationException exception) throws IOException{
        ObjectMapper mapper = new ObjectMapper();
        String jsonInString = mapper.writeValueAsString(exception);
        return  jsonInString;
    }


    public static Object getResponse(Object result, HttpServletRequest request, int httpStatus,String RemovedCookie)
            throws IOException, MessagingException {
        NewCookie cookie;
        if(request.isSecure())
         cookie= new NewCookie(RemovedCookie,null,"/nuxeo",request.getServerName(),"",0,true);
        else  {
            cookie= new NewCookie(RemovedCookie,null,"/nuxeo",request.getServerName(),"",0,false);
        }
        //cookie.setMaxAge(0);
        //cookie.setPath("/");



        if (result == null || "true".equals(request.getHeader("X-NXVoidOperation"))) {
            return ResponseHelper.emptyContent();

        }
        if (result instanceof Blob) {
            return result; // BlobWriter will do all the processing and call the DownloadService
        } else if (result instanceof BlobList) {
            return ResponseHelper.blobs((BlobList) result);
        } else if (result instanceof DocumentRef) {
            CoreSession session = SessionFactory.getSession(request);
            return Response.status(httpStatus).entity(session.getDocument((DocumentRef) result)).cookie(cookie).build();
        } else if (result instanceof DocumentRefList) {
            CoreSession session = SessionFactory.getSession(request);
            return Response.status(httpStatus).entity(((DocumentRefList) result).stream().map(session::getDocument)
                    .collect(Collectors.toCollection(DocumentModelListImpl::new))).cookie(cookie).build();
        } else if (result instanceof List && !((List<?>) result).isEmpty()
                && ((List<?>) result).get(0) instanceof NuxeoPrincipal) {
            return Response.status(httpStatus)
                    .entity(new GenericEntity<>(result,
                            TypeUtils.parameterize(List.class, NuxeoPrincipal.class)))
                    .cookie(cookie).build();
        } else if (result instanceof DocumentModel || result instanceof DocumentModelList
                || result instanceof JsonAdapter || result instanceof RecordSet || result instanceof Paginable<?>) {
            return Response.status(httpStatus).entity(result).cookie(cookie).build();
        } else { // try to adapt to JSON
            return Response.status(httpStatus).entity(new DefaultJsonAdapter(result)).cookie(cookie).build();
        }
    }
}
