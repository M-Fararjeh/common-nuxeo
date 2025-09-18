package org.nuxeo.ecm.restapi.server.jaxrs.extended;

import com.fasterxml.jackson.core.type.*;
import com.fasterxml.jackson.databind.*;
import com.google.gson.*;
import org.apache.logging.log4j.*;
import org.json.*;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.platform.usermanager.*;
import org.nuxeo.ecm.webengine.model.*;
import org.nuxeo.ecm.webengine.model.impl.DefaultObject;
import org.nuxeo.extended.*;
import org.nuxeo.runtime.api.Framework;

import javax.servlet.http.*;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;

import org.nuxeo.ecm.platform.ui.web.keycloak.KeycloakAuthenticationPlugin;
@WebObject(type = "keycloak-user")
public class KeycloakUserApi extends DefaultObject {
    private static final Logger log = LogManager.getLogger(KeycloakUserApi.class);
    protected UserManager userManager = Framework.getService(UserManager.class);
    @Context
    protected HttpServletRequest request;
    @Context
    protected HttpServletResponse response;
    @Context
    protected CoreSession session;

    protected static String groupSchemaName = "group";
    private static final int TIMEOUT = 5000;
    private static final Gson gson = new Gson();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String REALM_NAME = Framework.getProperty("nuxeo.comptech.keycloak.realm.name");
    private static final String SERVICE_CLIENT_ID = Framework.getProperty("nuxeo.comptech.keycloak.service-client.id");
    private static final String SERVICE_CLIENT_SECRET = Framework.getProperty("nuxeo.comptech.keycloak.service-client.secret");
    private static final String KEYCLOAK_URL = Framework.getProperty("nuxeo.comptech.keycloak.url");

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Object doGet(@QueryParam("username") String username) throws Exception {
        List<KeycloakUserInfo> users = getKeycloakUserByUsername(username);
        if(users!=null) {
            String usersAsJson = MAPPER.writeValueAsString(users);
            return Response.ok().entity(usersAsJson).build();
        }
        return Response.serverError().build();
    }
    @POST
    public Response doPost(String requestBody) {
        if(requestBody != null && !requestBody.isEmpty()) {
            try {
                List usernamesList = gson.fromJson(requestBody, List.class);
                for (Object username : usernamesList) {
                    List<KeycloakUserInfo> users = getKeycloakUserByUsername(username.toString());
                    if (users != null && !users.isEmpty())
                        createOrUpdateNuxeoUser(users.get(0));
                }
                return Response.status(HttpServletResponse.SC_CREATED).build();
            }
            catch (JSONException e){
                return Response.status(HttpServletResponse.SC_BAD_REQUEST).build();
            }
            catch (Exception e){
                return Response.serverError().build();
            }
        }
        return Response.status(HttpServletResponse.SC_BAD_REQUEST).build();
    }
    private List<KeycloakUserInfo> getKeycloakUserByUsername(String username) throws Exception {
        int totalUsersCount = 100;
        String token = getAccessToken();
        String keycloakServer = getIssuer(token);
        if (username == null || username.isEmpty() || username.isBlank()) {
            username = "";
            totalUsersCount = getTotalUsersCount(keycloakServer,token);
            log.error("totalUsersCount - " + totalUsersCount);
        }
        String usersApiUrl = keycloakServer.replace("/auth/realms/", "/auth/admin/realms/")
                + "/users?username=" + username + "&max=" + totalUsersCount;

        try (InputStream responseStream = sendHttpGetRequest(usersApiUrl,token)) {
            List<KeycloakUserInfo> users = MAPPER.readValue(responseStream, new TypeReference<>() {});
            return users.stream()
                    .filter(user -> !user.getUsername().startsWith("service-account-"))
                    .collect(Collectors.toList());
        }
    }

    private String getAccessToken() throws IOException {
        String accessToken = request.getHeader("Authorization");
        if(accessToken == null){
            accessToken = getAccessTokenUsingServiceAccount();
        }
        if(accessToken == null)
        {
            throw new RuntimeException(new Exception("User is not authorized to do this action"));
        }
        return accessToken;
    }

    private int getTotalUsersCount(String keycloakServer,String token) {
        String usersApiUrl = keycloakServer.replace("/auth/realms/", "/auth/admin/realms/")
                + "/users/count";

        try (InputStream responseStream = sendHttpGetRequest(usersApiUrl,token)) {
            String countStr = new BufferedReader(new InputStreamReader(responseStream))
                    .lines()
                    .collect(Collectors.joining("\n"));

            return Integer.parseInt(countStr);
        } catch (Exception e) {
            log.error("An error occurred during get the total users count from keycloak ");
            log.error(e.getMessage());
            return 1000;
        }
    }

    private String getIssuer(String token) throws JSONException {
        if(token.startsWith("Bearer"))
            token = token.replace("Bearer ","");
        String[] chunks = token.split("\\.");
        Base64.Decoder decoder = Base64.getUrlDecoder();
        String payload = new String(decoder.decode(chunks[1]));
        JSONObject json = new JSONObject(payload);
        return json.getString("iss");
    }
    private void createOrUpdateNuxeoUser(KeycloakUserInfo keycloakUserInfo) {
        if (userManager.getUserModel(keycloakUserInfo.getUsername()) == null) {
            createNuxeoUser(keycloakUserInfo);
        }
        else{
            updateNuxeoUser(keycloakUserInfo);
        }
    }
    private void createNuxeoUser(KeycloakUserInfo keycloakUserInfo) {
        DocumentModel newUserDoc = userManager.getBareUserModel();
        newUserDoc.setPropertyValue(UserConfig.USERNAME_COLUMN, keycloakUserInfo.getUsername());
        newUserDoc.setPropertyValue(UserConfig.PASSWORD_COLUMN,
                UUID.randomUUID().toString());
        newUserDoc.setPropertyValue(UserConfig.FIRSTNAME_COLUMN, keycloakUserInfo.getFirstName());
        newUserDoc.setPropertyValue(UserConfig.LASTNAME_COLUMN, keycloakUserInfo.getLastName());
        newUserDoc.setPropertyValue(UserConfig.EMAIL_COLUMN, keycloakUserInfo.getEmail());
        final DocumentModel userDoc = newUserDoc;
        new UnrestrictedSessionRunner(session) {
            @Override
            public void run() {
                userManager.createUser(userDoc);
                try {
                    updateUserGroups(keycloakUserInfo);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }.runUnrestricted();

    }
    private void updateNuxeoUser(KeycloakUserInfo keycloakUserInfo) {
        DocumentModel userDoc = userManager.getUserModel(keycloakUserInfo.getUsername());
        userDoc.setPropertyValue(UserConfig.FIRSTNAME_COLUMN, keycloakUserInfo.getFirstName());
        userDoc.setPropertyValue(UserConfig.LASTNAME_COLUMN, keycloakUserInfo.getLastName());
        userDoc.setPropertyValue(UserConfig.EMAIL_COLUMN, keycloakUserInfo.getEmail());
        new UnrestrictedSessionRunner(session) {
            @Override
            public void run() {
                userManager.updateUser(userDoc);
                try {
                    updateUserGroups(keycloakUserInfo);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }.runUnrestricted();
    }
    private void updateUserGroups(KeycloakUserInfo keycloakUserInfo) throws IOException {
        String token = getAccessToken();
        String keycloakServer = getIssuer(token);
        String roleMappingsUrl = keycloakServer.replace("/auth/realms/", "/auth/admin/realms/")
                .concat("/users/" + keycloakUserInfo.getId() + "/role-mappings/realm/composite");
        try {
            List<RoleMappingDto> roleMappings = getRoleMappings(roleMappingsUrl);
            List<String> keycloakRolesList = roleMappings.stream().map(RoleMappingDto::getName).toList();
            keycloakRolesList.forEach(keycloakRole -> addUserToGroup(keycloakUserInfo.getUsername(),keycloakRole));
            log.error("roleMappings ... " + roleMappings);
        } catch (Exception e) {
            log.error("AN EXCEPTION OCCURRED DURING getRoleMappings ... " + e.getMessage());
        }
    }
    private List<RoleMappingDto> getRoleMappings(String roleMappingsUrl) throws Exception {
        String token = getAccessToken();
        try (InputStream responseStream = sendHttpGetRequest(roleMappingsUrl,token)) {
            return MAPPER.readValue(responseStream, new TypeReference<>() {});
        }
    }
    private void addUserToGroup(String username, String groupName) {
        DocumentModel groupDocModel = findOrCreateGroup(groupName);
        if(groupDocModel != null) {
            List<String> users= userManager.getUsersInGroup(groupName);
            if (!users.contains(username)) {
                users.add(username);
                groupDocModel.setProperty(groupSchemaName, userManager.getGroupMembersField(), users);
                userManager.updateGroup(groupDocModel);
            }
        }
    }
    private DocumentModel findOrCreateGroup(String role) {
        DocumentModel groupDoc = findGroup(role);
        if (groupDoc == null) {
            groupDoc = userManager.getBareGroupModel();
            groupDoc.setPropertyValue(userManager.getGroupIdField(), role);
            groupDoc.setProperty(groupSchemaName, "groupname", role);
            groupDoc.setProperty(groupSchemaName, "grouplabel", role + " group");
            groupDoc.setProperty(groupSchemaName, "description",
                    "Group automatically created by Keycloak based on user role [" + role + "]");
            groupDoc = userManager.createGroup(groupDoc);
        }
        return groupDoc;
    }
    private DocumentModel findGroup(String role) {
        Map<String, Serializable> query = new HashMap<>();
        query.put(userManager.getGroupIdField(), role);
        DocumentModelList groups = userManager.searchGroups(query, null);

        if (groups.isEmpty()) {
            return null;
        }
        return groups.get(0);
    }
    private InputStream sendHttpGetRequest(String url,String authHeader) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", authHeader);
        connection.setRequestProperty("Content-Type", MediaType.APPLICATION_JSON);
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);

        int responseCode = connection.getResponseCode();
        switch (responseCode) {
            case HttpURLConnection.HTTP_OK -> {
                return connection.getInputStream();
            }
            case HttpURLConnection.HTTP_UNAUTHORIZED -> {
                connection.disconnect();
                throw new RuntimeException("Unauthorized access: Invalid token.");
            }
            case HttpURLConnection.HTTP_BAD_REQUEST -> {
                connection.disconnect();
                throw new RuntimeException("Bad request: Invalid parameters.");
            }
            case HttpURLConnection.HTTP_INTERNAL_ERROR -> {
                connection.disconnect();
                throw new RuntimeException("Server error: Keycloak is down or unreachable.");
            }
            default -> {
                connection.disconnect();
                throw new RuntimeException("Failed to fetch data: HTTP error code : " + responseCode);
            }
        }
    }

    private String getAccessTokenUsingServiceAccount() throws IOException {

        NuxeoPrincipal principal = session.getPrincipal();
        if (!principal.isAdministrator()) {
            return null;
        }

        URL url = new URL(KEYCLOAK_URL + "/realms/" + REALM_NAME + "/protocol/openid-connect/token");

            String data = "grant_type=client_credentials" +
                    "&client_id=" + URLEncoder.encode(SERVICE_CLIENT_ID, "UTF-8") +
                    "&client_secret=" + URLEncoder.encode(SERVICE_CLIENT_SECRET, "UTF-8");

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(data.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                throw new RuntimeException("Failed to get token. Status: " + status);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
            }

            org.json.JSONObject json = new org.json.JSONObject(response.toString());
            return "Bearer " + json.getString("access_token");
        }
    }
