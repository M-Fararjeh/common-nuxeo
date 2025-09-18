package sa.comptechco.nuxeo.common.filters;

import org.nuxeo.ecm.core.api.impl.UserPrincipal;
import org.nuxeo.ecm.platform.ui.web.auth.CachableUserIdentificationInfo;
import org.nuxeo.ecm.platform.ui.web.auth.interfaces.NuxeoAuthenticationSessionManager;
import org.nuxeo.ecm.platform.usermanager.NuxeoPrincipalImpl;
import org.nuxeo.ecm.webengine.login.WebEngineSessionManager;
import org.nuxeo.runtime.api.Framework;
import sa.comptechco.nuxeo.common.filters.sessions.CustomLoginHttpSessionBinder;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.Iterator;
import java.util.regex.Pattern;


public class CustomRequestFilter implements Filter{
	Boolean ENABLE_FETCH_OTHER_USERS;
	Boolean ENABLE_MULTIPLE_LOGIN;
	String ENABLE_FETCH_OTHER_USERS_PROP = "comptechco.security.user.fetch.enable";
	String ENABLE_MULTIPLE_LOGIN_PROP = "comptechco.security.user.multi-login.enable";
	String CONTEXT_PATH = "/nuxeo";
	String CONTEXT_PATH_PROP = "org.nuxeo.ecm.contextPath";
	String CUSTOM_SESSION_BIND_ATTR = "com.comptechco.custom.session.bind";

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
			throws IOException, ServletException {


		try {
			ENABLE_FETCH_OTHER_USERS = Boolean.valueOf(Framework.getProperty(ENABLE_FETCH_OTHER_USERS_PROP, "true"));
		} catch (Exception e) {
			ENABLE_FETCH_OTHER_USERS = true;
		}
		try {
			ENABLE_MULTIPLE_LOGIN = Boolean.valueOf(Framework.getProperty(ENABLE_MULTIPLE_LOGIN_PROP, "true"));
		} catch (Exception e) {
			ENABLE_MULTIPLE_LOGIN = true;
		}
		try {
			CONTEXT_PATH = Framework.getProperty(CONTEXT_PATH_PROP, "/nuxeo");
		} catch (Exception e) {
			CONTEXT_PATH = "/nuxeo";
		}
//		System.out.println("================================================Hello from CTRequestFilter===========================================");
		HttpServletRequest httpRequest = null;
		if (servletRequest instanceof HttpServletRequest){
//			System.out.println("================================================Casting to HttpServletRequest===========================================");
			httpRequest = (HttpServletRequest) servletRequest;
		}
		HttpServletResponse httpResponse = null;
		if (servletResponse instanceof HttpServletResponse){
//			System.out.println("================================================Casting to HttpServletRequest===========================================");
			httpResponse = (HttpServletResponse) servletResponse;
		}
		if (httpRequest == null) {
//			System.out.println("================================================httpRequest is null - RETURN ===========================================");
			filterChain.doFilter(servletRequest, servletResponse);
			return;
		}
//		System.out.println("================================================Check user identity===========================================");

		// check invalid session
		Object sessionInvalid = httpRequest.getSession().getAttribute("com.comptechco.custom.session.invalid");
		if (sessionInvalid != null && (Boolean) sessionInvalid) {
			assert httpResponse != null;
			httpResponse.sendError(401, "Unauthorized");
			return;
		}
		if (httpRequest.getSession().getAttribute("org.nuxeo.ecm.login.identity") instanceof CachableUserIdentificationInfo ) {
//			System.out.println("================================================Casting [org.nuxeo.ecm.login.identity] to CachableUserIdentificationInfo===========================================");

			CachableUserIdentificationInfo principal = (CachableUserIdentificationInfo) httpRequest.getSession().getAttribute("org.nuxeo.ecm.login.identity");

			handleSessionDuplication(principal.getPrincipal().getName(), httpRequest, ENABLE_MULTIPLE_LOGIN);

			//////////////////////////////

			if (principal.getPrincipal() instanceof NuxeoPrincipalImpl) {
//				System.out.println("================================================Casting CachableUserIdentificationInfo to NuxeoPrincipalImpl===========================================");

				NuxeoPrincipalImpl nuxeoPrincipal = (NuxeoPrincipalImpl) principal.getPrincipal();
				if (nuxeoPrincipal.isAdministrator) {
//					System.out.println("================================================nuxeoPrincipal.isAdministrator & USER IS ADMIN - RETURN===========================================");

					filterChain.doFilter(servletRequest, servletResponse);
					return;
				} else if (httpRequest.getRequestURI().endsWith("/api/v1/user/" + nuxeoPrincipal.getName())) {
					filterChain.doFilter(servletRequest, servletResponse);
					return;
				} else if (httpResponse != null && httpRequest.getRequestURI() != null && httpRequest.getRequestURI().length() > 0
						&& httpRequest.getRequestURI().contains("/") && httpRequest.getRequestURI().substring(0, httpRequest.getRequestURI().lastIndexOf('/')).endsWith("/api/v1/user")) {
					if (ENABLE_FETCH_OTHER_USERS) {
						filterChain.doFilter(servletRequest, servletResponse);
						return;
					} else {
						httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Not Allowed Request");
					}
				}
			} else if (principal.getPrincipal().getName().toLowerCase().startsWith("admin")) {
//				System.out.println("================================================USER IS ADMIN - RETURN===========================================");

				filterChain.doFilter(servletRequest, servletResponse);
				return;
			} else if (httpRequest.getRequestURI().endsWith("/api/v1/user/" + principal.getPrincipal().getName())) {
				filterChain.doFilter(servletRequest, servletResponse);
				return;
			} else if (httpResponse != null && httpRequest.getRequestURI() != null && httpRequest.getRequestURI().length() > 0
					&& httpRequest.getRequestURI().contains("/") && httpRequest.getRequestURI().substring(0, httpRequest.getRequestURI().lastIndexOf('/')).endsWith("/api/v1/user")) {
				if (ENABLE_FETCH_OTHER_USERS) {
					filterChain.doFilter(servletRequest, servletResponse);
					return;
				} else {
					httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Not Allowed Request");
				}
			}
		} else if (httpRequest.getSession().getAttribute("org.nuxeo.ecm.login.identity") instanceof UserPrincipal) {
//			System.out.println("================================================Casting [org.nuxeo.ecm.login.identity] to UserPrincipal===========================================");

			UserPrincipal principal = (UserPrincipal) httpRequest.getSession().getAttribute("org.nuxeo.ecm.login.identity");

			handleSessionDuplication(principal.getName(), httpRequest, ENABLE_MULTIPLE_LOGIN);

			if (principal.isAdministrator()) {
//				System.out.println("================================================principal.isAdministrator() & USER IS ADMIN - RETURN===========================================");

				filterChain.doFilter(servletRequest, servletResponse);
				return;
			} else if (httpRequest.getRequestURI().endsWith("/api/v1/user/" + principal.getName())) {
				filterChain.doFilter(servletRequest, servletResponse);
				return;
			} else if (httpResponse != null && httpRequest.getRequestURI() != null && httpRequest.getRequestURI().length() > 0
					&& httpRequest.getRequestURI().contains("/") && httpRequest.getRequestURI().substring(0, httpRequest.getRequestURI().lastIndexOf('/')).endsWith("/api/v1/user")) {
				if (ENABLE_FETCH_OTHER_USERS) {
					filterChain.doFilter(servletRequest, servletResponse);
					return;
				} else {
					httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Not Allowed Request");
				}
			}
		} else if (httpRequest.getSession().getAttribute("org.nuxeo.ecm.login.identity") instanceof Principal) {
//			System.out.println("================================================Casting [org.nuxeo.ecm.login.identity] to Principal===========================================");

			Principal principal = (Principal) httpRequest.getSession().getAttribute("org.nuxeo.ecm.login.identity");

			handleSessionDuplication(principal.getName(), httpRequest, ENABLE_MULTIPLE_LOGIN);

			if (principal.getName().toLowerCase().startsWith("admin")) {
//				System.out.println("================================================ [77] USER IS ADMIN - RETURN===========================================");

				filterChain.doFilter(servletRequest, servletResponse);
				return;
			} else if (httpRequest.getRequestURI().endsWith("/api/v1/user/" + principal.getName())) {
				filterChain.doFilter(servletRequest, servletResponse);
				return;
			} else if (httpResponse != null && httpRequest.getRequestURI() != null && httpRequest.getRequestURI().length() > 0
					&& httpRequest.getRequestURI().contains("/") && httpRequest.getRequestURI().substring(0, httpRequest.getRequestURI().lastIndexOf('/')).endsWith("/api/v1/user")) {
				if (ENABLE_FETCH_OTHER_USERS) {
					filterChain.doFilter(servletRequest, servletResponse);
					return;
				} else {
					httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Not Allowed Request");
				}
			}
		}
		// Here own logic

//		this.printHeaders(httpRequest);
		Pattern urlPattern = Pattern.compile("https?://[^/]+"+CONTEXT_PATH+"(/.*)?$");
//		System.out.println();
//		System.out.println();
//		System.out.println("================================================ PRINTING HEADERS - START===========================================");
		Iterator<String> headersItr = httpRequest.getHeaderNames().asIterator();
		while(headersItr.hasNext()) {
			String headerName = headersItr.next();
//			System.out.println("===> " + headerName + " : " + httpRequest.getHeader(headerName));

		}

//		System.out.println("================================================ PRINTING HEADERS - END===========================================");
//		System.out.println();
//		System.out.println();

//		System.out.println("================================================ Check Referer Header: [Referer:  " + httpRequest.getHeader("Referer") + "] ===========================================");

		if (httpRequest.getHeader("Referer") != null && urlPattern.matcher(httpRequest.getHeader("Referer")).matches()) {
//			System.out.println("================================================ Check Referer: [Referer:  " + httpRequest.getHeader("Referer") + "] - MATCH NUXEO ===========================================");

			if (httpRequest.getRequestURI().endsWith("/api/v1/search/execute")) {
//				System.out.println("================================================ [92] (/api/v1/search/execute) - Do Nothing ===========================================");
				filterChain.doFilter(servletRequest, servletResponse);

			} else if (httpRequest.getRequestURI().endsWith("/api/v1/search/pp/default_content_collection/execute")) {
				httpRequest.getRequestDispatcher(httpRequest.getRequestURI().replaceFirst(httpRequest.getContextPath(), "").replaceFirst("default_content_collection", "tdf_nuxeo_ui_content_collection")).forward(servletRequest, servletResponse);
			} else if (httpRequest.getRequestURI().endsWith("/api/v1/search/pp/domain_documents/execute")) {
				httpRequest.getRequestDispatcher(httpRequest.getRequestURI().replaceFirst(httpRequest.getContextPath(), "").replaceFirst("domain_documents", "tdf_nuxeo_ui_domain_documents")).forward(servletRequest, servletResponse);
			} else if (httpRequest.getRequestURI().endsWith("/api/v1/automation/Search.SuggestersLauncher")) {
				// NuxeoUISuggestOperation
				httpRequest.getRequestDispatcher(httpRequest.getRequestURI().replaceFirst(httpRequest.getContextPath(), "").replaceFirst("Search.SuggestersLauncher", "Search.NuxeoUISuggestOperation")).forward(servletRequest, servletResponse);

			} else if (httpRequest.getRequestURI().endsWith("/api/v1/search/pp/nxql_search/execute")) {
				filterChain.doFilter(servletRequest, servletResponse);

			} else if (httpRequest.getRequestURI().endsWith("/api/v1/search/pp/tree_children/execute")) {
				httpRequest.getRequestDispatcher(httpRequest.getRequestURI().replaceFirst(httpRequest.getContextPath(), "").replaceFirst("tree_children", "tdf_nuxeo_ui_tree_children")).forward(servletRequest, servletResponse);
			} else if (httpRequest.getRequestURI().endsWith("/api/v1/search/pp/default_search/execute")) {
				httpRequest.getRequestDispatcher(httpRequest.getRequestURI().replaceFirst(httpRequest.getContextPath(), "").replaceFirst("default_search", "tdf_nuxeo_ui_default_search")).forward(servletRequest, servletResponse);
			} else if (httpRequest.getRequestURI().endsWith("/api/v1/search/pp/assets_search/execute")) {
				httpRequest.getRequestDispatcher(httpRequest.getRequestURI().replaceFirst(httpRequest.getContextPath(), "").replaceFirst("assets_search", "tdf_nuxeo_ui_assets_search")).forward(servletRequest, servletResponse);
			} else if (httpRequest.getRequestURI().endsWith("/api/v1/search/pp/advanced_document_content/execute")) {
				httpRequest.getRequestDispatcher(httpRequest.getRequestURI().replaceFirst(httpRequest.getContextPath(), "").replaceFirst("advanced_document_content", "tdf_nuxeo_ui_advanced_document_content")).forward(servletRequest, servletResponse);

			} else if (httpRequest.getRequestURI().endsWith("/api/v1/task")) {
//				System.out.println(httpRequest.getRequestURI());
				filterChain.doFilter(servletRequest, servletResponse);

			} else if (httpRequest.getRequestURI().endsWith("/api/v1/automation/Favorite.Fetch")) {
//				System.out.println(httpRequest.getRequestURI());
				filterChain.doFilter(servletRequest, servletResponse);

			} else {
//				System.out.println("================================================ [125] RequestURI Do Not Match any pattern - Do Nothing ===========================================");

				filterChain.doFilter(servletRequest, servletResponse);
			}

		} else {
//			System.out.println("================================================ [132] Referer [ " + httpRequest.getHeader("Referer") + " ] Do Not Match Pattern [ \"https?://[^/]+/nuxeo(/.*)?$\" ] - Do Nothing ===========================================");

			filterChain.doFilter(servletRequest, servletResponse);
		}
		
	}

	private void handleSessionDuplication(String username, HttpServletRequest httpRequest, boolean multiLoginEnabled) {
		if (!multiLoginEnabled && httpRequest.getRequestURI().endsWith("/api/v1/user/" + username)) {
			// handle session invalidation
			Object currentUserSession =  httpRequest.getSession().getAttribute(CUSTOM_SESSION_BIND_ATTR);
			if (currentUserSession == null) {
				CustomLoginHttpSessionBinder newUserSession = new CustomLoginHttpSessionBinder();
				newUserSession.setAccountId(username);
				httpRequest.getSession().setAttribute(CUSTOM_SESSION_BIND_ATTR, newUserSession);
			}
		}
	}

}
