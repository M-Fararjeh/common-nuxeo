package sa.comptechco.nuxeo.common.auth.servlets;


import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
public class AuthLoginServlet extends HttpServlet {
    private static final String redirectUrl = Framework.getProperty("comptech.nuxeo.app.auth.redirect.url");
    private static final String redirectMessage = Framework.getProperty("comptech.nuxeo.app.auth.redirect.message","Please wait, redirecting...");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handlRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handlRequest(request, response);

    }

    private void handlRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String redirectUrlParam = request.getParameter("redirectUrl");
        String finalRedirectUrl = "";
        if(redirectUrlParam == null || redirectUrlParam.isBlank() || redirectUrlParam.isEmpty()){
            finalRedirectUrl = redirectUrl;
        }
        else{
            finalRedirectUrl = redirectUrlParam;
        }
        response.sendRedirect(finalRedirectUrl);
    }
}
