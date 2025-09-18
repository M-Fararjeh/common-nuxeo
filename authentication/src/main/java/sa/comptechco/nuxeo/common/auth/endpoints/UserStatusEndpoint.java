package sa.comptechco.nuxeo.common.auth.endpoints;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.platform.ui.web.auth.CachableUserIdentificationInfo;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.ModuleRoot;
import org.nuxeo.runtime.api.Framework;

import java.util.HashMap;
import java.util.Map;

@Path("/user")
@WebObject(type = "User")
public class UserStatusEndpoint extends ModuleRoot {

    private static final String PRINT_STACK_TRACE_PROPERTY = "comptech.nuxeo.app.auth.endpoints.user.exceptions.print-stack-trace";
    private static final String LOGIN_IDENTITY_ATTRIBUTE = "org.nuxeo.ecm.login.identity";
    private static final String NOT_AUTHENTICATED_ERROR = "User not authenticated";
    private static final String INTERNAL_ERROR = "Internal server error";

    private static final Logger log = LogManager.getLogger(UserStatusEndpoint.class);
    private static final Gson gson = new Gson();

    @Context
    private HttpServletRequest request;

    @Context
    private HttpServletResponse response;

    @GET
    @Path("whoami")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserStatus() {
        try {
            NuxeoPrincipal principal = getAuthenticatedPrincipal();
            if (principal == null) {
                return createErrorResponse(Response.Status.FORBIDDEN, NOT_AUTHENTICATED_ERROR);
            }

            return createSuccessResponse(principal.getName());

        } catch (Exception e) {
            log.error("Error getting user status: {}", e.getMessage());

            if (shouldPrintStackTrace()) {
                log.error("Stack trace:", e);
            }

            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, INTERNAL_ERROR);
        }
    }

    private NuxeoPrincipal getAuthenticatedPrincipal() {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        CachableUserIdentificationInfo identificationInfo =
                (CachableUserIdentificationInfo) session.getAttribute(LOGIN_IDENTITY_ATTRIBUTE);
        if (identificationInfo == null) {
            return null;
        }

        NuxeoPrincipal principal = (NuxeoPrincipal) identificationInfo.getPrincipal();
        if (principal == null || principal.isAnonymous()) {
            return null;
        }

        return principal;
    }

    private Response createErrorResponse(Response.Status status, String errorMessage) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", errorMessage);
        return Response.status(status)
                .entity(gson.toJson(errorResponse))
                .build();
    }

    private Response createSuccessResponse(String username) {
        Map<String, String> userResponse = new HashMap<>();
        userResponse.put("username", username);
        userResponse.put("status", "authenticated");
        return Response.ok(gson.toJson(userResponse)).build();
    }

    private boolean shouldPrintStackTrace() {
        return Boolean.parseBoolean(
                Framework.getProperty(PRINT_STACK_TRACE_PROPERTY, "false")
        );
    }
}