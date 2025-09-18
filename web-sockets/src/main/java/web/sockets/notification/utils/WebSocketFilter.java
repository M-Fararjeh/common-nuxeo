package web.sockets.notification.utils;

import io.dropwizard.metrics5.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Supplier;
import org.nuxeo.ecm.platform.api.login.UserIdentificationInfo;
import org.nuxeo.ecm.platform.ui.web.auth.CachableUserIdentificationInfo;
import org.nuxeo.ecm.platform.ui.web.auth.NuxeoAuthenticationFilter;
import org.nuxeo.ecm.platform.ui.web.auth.NuxeoSecuredRequestWrapper;
import org.nuxeo.ecm.platform.ui.web.auth.interfaces.LoginResponseHandler;
import org.nuxeo.ecm.platform.ui.web.auth.interfaces.NuxeoAuthenticationPlugin;
import org.nuxeo.ecm.platform.web.common.session.NuxeoHttpSessionMonitor;
import org.nuxeo.ecm.platform.web.common.vh.VirtualHostHelper;
import org.nuxeo.runtime.api.login.LoginComponent;
import org.nuxeo.runtime.api.login.NuxeoLoginContext;
import web.sockets.notification.server.WebSocketServletListener;

import javax.security.auth.login.LoginContext;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.Objects;

public class WebSocketFilter extends NuxeoAuthenticationFilter {
    private static final Logger log = LogManager.getLogger(WebSocketFilter.class);

    @Override
    public void doFilterInternal(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        //log.error("Hello from doFilterInternal");

        this.checkRequestedURL(request);
        if (this.bypassAuth((HttpServletRequest)request)) {
            chain.doFilter(request, response);
        } else {
            String tokenPage = getRequestedPage(request);
            if (tokenPage.equals("swuser")) {
                boolean result = this.switchUser(request, response, chain);
                if (result) {
                    return;
                }
            }

            if (request instanceof NuxeoSecuredRequestWrapper) {
                log.debug("ReEntering Nuxeo Authentication Filter ... exiting directly");
                chain.doFilter(request, response);
            } else if (this.service.canBypassRequest(request)) {
                log.debug("ReEntering Nuxeo Authentication Filter after URL rewrite ... exiting directly");
                chain.doFilter(request, response);
            } else {
                log.debug("Entering Nuxeo Authentication Filter");
                String targetPageURL = null;
                HttpServletRequest httpRequest = (HttpServletRequest)request;
                HttpServletResponse httpResponse = (HttpServletResponse)response;
                Principal principal = httpRequest.getUserPrincipal();
                NuxeoLoginContext propagatedAuthCb = null;
                String forceAnonymousLoginParam = httpRequest.getParameter("forceAnonymousLogin");
                boolean forceAnonymousLogin = Boolean.parseBoolean(forceAnonymousLoginParam);
                String anonymousId = this.getAnonymousId();

                try {
                    String requestedPage;
                    if (principal == null) {
                        log.debug("Principal not found inside Request via getUserPrincipal");
                        log.debug("Try getting authentication from cache");
                        CachableUserIdentificationInfo cachableUserIdent = retrieveIdentityFromCache(httpRequest);
                        if (cachableUserIdent != null) {
                            if (forceAnonymousLogin && cachableUserIdent.getUserInfo().getUserName().equals(anonymousId)) {
                                cachableUserIdent = null;
                                this.service.invalidateSession(request);
                            }

                            if (this.service.needResetLogin(request)) {
                                HttpSession session = httpRequest.getSession(false);
                                if (session != null) {
                                    session.removeAttribute("org.nuxeo.ecm.login.identity");
                                }

                                this.service.invalidateSession(request);
                                cachableUserIdent = null;
                            }
                        }

                        boolean res;
                        if (cachableUserIdent != null) {
                            log.debug("userIdent found in cache, get the Principal from it without reloggin");
                            NuxeoHttpSessionMonitor.instance().updateEntry(httpRequest);
                            principal = cachableUserIdent.getPrincipal();
                            Logger var10000 = log;
                            Supplier[] var10002 = new Supplier[1];
                            Objects.requireNonNull(principal);
                            var10002[0] = principal::getName;
                            var10000.debug("Principal: {}", var10002);
                            propagatedAuthCb = NuxeoLoginContext.create(cachableUserIdent.getPrincipal());
                            propagatedAuthCb.login();
                            requestedPage = getRequestedPage(httpRequest);
                            if ("logout".equals(requestedPage)) {
                                res = this.handleLogout(request, response, cachableUserIdent);
                                cachableUserIdent = null;
                                principal = null;
                                if (res && httpRequest.getParameter("form_submitted_marker") == null) {
                                    return;
                                }
                            } else if ("login".equals(requestedPage)) {
                                if (this.handleLogin(httpRequest, httpResponse)) {
                                    return;
                                }
                            } else {
                                targetPageURL = getSavedRequestedURL(httpRequest, httpResponse);
                            }
                        }

                        if (cachableUserIdent == null) {
                            UserIdentificationInfo userIdent = this.handleRetrieveIdentity(httpRequest, httpResponse);
                            String redirectUrl;
                            if (userIdent != null) {
                                redirectUrl = userIdent.getUserName();
                                if (redirectUrl.equals("system")) {
                                    this.buildUnauthorizedResponse(httpRequest, httpResponse);
                                    return;
                                }

                                if (forceAnonymousLogin && redirectUrl.equals(anonymousId)) {
                                    userIdent = null;
                                }
                            }

                            if (userIdent == null && !this.bypassAuth(httpRequest)) {
                                res = this.handleLoginPrompt(httpRequest, httpResponse);
                                if (res) {
                                    return;
                                }
                            } else {
                                redirectUrl = VirtualHostHelper.getRedirectUrl(httpRequest);
                                HttpSession session = httpRequest.getSession(false);
                                if (session != null) {
                                    session.setAttribute("redirect_url", redirectUrl);
                                }

                                targetPageURL = getSavedRequestedURL(httpRequest, httpResponse);
                            }

                            if (userIdent != null) {
                                cachableUserIdent = new CachableUserIdentificationInfo(userIdent);
                                principal = this.doAuthenticate(cachableUserIdent, httpRequest);
                                NuxeoAuthenticationPlugin plugin;
                                if (principal != null && principal != DIRECTORY_ERROR_PRINCIPAL) {
                                    propagatedAuthCb = NuxeoLoginContext.create(cachableUserIdent.getPrincipal());
                                    propagatedAuthCb.login();
                                    plugin = this.getAuthenticator(cachableUserIdent);
                                    if (plugin instanceof LoginResponseHandler && ((LoginResponseHandler)plugin).onSuccess((HttpServletRequest)request, (HttpServletResponse)response)) {
                                        return;
                                    }
                                } else {
                                    plugin = this.getAuthenticator(cachableUserIdent);
                                    if (plugin instanceof LoginResponseHandler) {
                                        if (((LoginResponseHandler)plugin).onError((HttpServletRequest)request, (HttpServletResponse)response)) {
                                            return;
                                        }
                                    } else {
                                        String err = principal == DIRECTORY_ERROR_PRINCIPAL ? "connection.error" : "authentication.failed";
                                        httpRequest.setAttribute("org.nuxeo.ecm.login.error", err);
                                        boolean res_ = this.handleLoginPrompt(httpRequest, httpResponse);
                                        if (res_) {
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (principal == null) {
                        chain.doFilter(request, response);
                    } else {
                        if (targetPageURL != null && targetPageURL.length() > 0) {
                            String baseURL = this.service.getBaseURL(request);
                            if ("XMLHttpRequest".equalsIgnoreCase(httpRequest.getHeader("X-Requested-With"))) {
                                return;
                            }

                            requestedPage = URI.create(baseURL).resolve(targetPageURL).toString();
                            httpResponse.sendRedirect(requestedPage);
                            return;
                        }
                        Principal p = LoginComponent.getCurrentPrincipal();
                        //httpRequest.setAttribute("NuxeoPrinciple",p);
                        httpRequest.getSession().setAttribute("NuxeoPrinciple",p);
                        chain.doFilter(new NuxeoSecuredRequestWrapper(httpRequest, principal), response);
                    }
                } finally {
                    if (propagatedAuthCb != null) {
                        propagatedAuthCb.close();
                    }

                }

                log.debug("Exit Nuxeo Authentication filter");
            }
        }
    }
}
