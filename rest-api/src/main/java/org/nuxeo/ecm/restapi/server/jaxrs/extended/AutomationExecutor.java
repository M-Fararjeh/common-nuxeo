package org.nuxeo.ecm.restapi.server.jaxrs.extended;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.meeting.AutomationException;
import org.nuxeo.common.utils.ExceptionUtils;
import org.nuxeo.ecm.automation.*;
import org.nuxeo.ecm.automation.core.impl.ChainTypeImpl;
import org.nuxeo.ecm.automation.jaxrs.io.operations.ExecutionRequest;
import org.nuxeo.ecm.automation.server.AutomationServer;
import org.nuxeo.ecm.automation.server.jaxrs.ResponseHelper;
import org.nuxeo.ecm.automation.server.jaxrs.RestOperationException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoException;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;


@WebObject(type = "custom-automation")
public class AutomationExecutor extends DefaultObject {


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
    @Path("{operationId}")
    public Object doPost(@PathParam("operationId") String operationId, ExecutionRequest xreq) {
        OperationType operation = null;
        try {
            operation = service.getOperation(operationId);
        } catch (OperationNotFoundException cause) {
            return new WebResourceNotFoundException("Failed to invoke operation: " + operationId, cause);
        }
        try {
            AutomationServer srv = Framework.getService(AutomationServer.class);
            if (!srv.accept(getId(operation), isChain(operation), request)) {
                return ResponseHelper.notFound();
            }
            Object result = execute(xreq, operation);
            int customHttpStatus = xreq.getRestOperationContext().getHttpStatus();
            return ResponseHelper.getResponse(result, request, customHttpStatus);
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

}
