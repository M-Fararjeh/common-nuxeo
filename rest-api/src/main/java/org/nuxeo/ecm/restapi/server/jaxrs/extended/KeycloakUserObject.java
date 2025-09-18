package org.nuxeo.ecm.restapi.server.jaxrs.extended;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.multipart.impl.MultiPartWriter;
import org.json.JSONException;
import org.json.JSONObject;
import org.keycloak.representations.AccessToken;
import org.nuxeo.ecm.automation.jaxrs.io.operations.ExecutionRequest;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.platform.usermanager.UserConfig;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.user.center.profile.UserProfileService;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.DefaultObject;
import org.nuxeo.extended.KeycloakUser;
import org.nuxeo.extended.KeycloakUserCredentials;
import org.nuxeo.extended.KeycloakUserInfo;
import org.nuxeo.runtime.api.Framework;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.Serializable;
import java.util.*;


@WebObject(type = "custom-user")
public class KeycloakUserObject extends DefaultObject {

    private static final String KEYCLOAK_ACCESS_TOKEN = "KEYCLOAK_ACCESS_TOKEN";
    @Context
    protected HttpServletRequest request;

    @Context
    protected HttpServletResponse response;

    @Context
    protected CoreSession session;

    protected final String CTS_INITIAL_GROUP = "cts_initial_group";

    protected static String groupSchemaName = "group";


    private String getIssuer(String token) throws JSONException {

        token = token.replace("Bearer ","");
        String[] chunks = token.split("\\.");

        Base64.Decoder decoder = Base64.getUrlDecoder();

        String header = new String(decoder.decode(chunks[0]));
        String payload = new String(decoder.decode(chunks[1]));
        JSONObject json = new JSONObject(payload);

        System.out.println("isseur is "+json.get("iss"));
        return json.getString("iss");
    }
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Object doPost(ExecutionRequest xreq) throws JsonProcessingException, JSONException {
        xreq.createContext(request, response, session);
        Map<String, Object> vars = xreq.getRestOperationContext().getVars();
        ObjectMapper objectMapper = new ObjectMapper();
        KeycloakUser keycloakUser;

        keycloakUser = new KeycloakUser();
        JsonNode userNode = (JsonNode) vars.get("user");
        String username=null,firstName=null,lastName=null,email=null,company = null,jobTitle=null,password=null;
        if(userNode.get("username")!= null)
        {
            username=userNode.get("username").asText();
        }
        if(userNode.get("firstName")!= null)
        {
            firstName=userNode.get("firstName").asText();
        }
        if(userNode.get("lastName")!= null)
        {
            lastName=userNode.get("lastName").asText();
        }
        if(userNode.get("email")!= null)
        {
            email=userNode.get("email").asText();
        }
        if(userNode.get("company")!= null)
        {
            company=userNode.get("company").asText();
        }
        if(userNode.get("jobTitle")!= null)
        {
            jobTitle=userNode.get("jobTitle").asText();
        }
        if(userNode.get("password")!= null)
        {
            password=userNode.get("password").asText();
        }
        keycloakUser.setUsername(username);
        keycloakUser.setFirstName(firstName);
        keycloakUser.setLastName(lastName);
        keycloakUser.setEmail(email);
        keycloakUser.setEnabled(true);
        List<KeycloakUserCredentials> keycloakUserCredentials = new ArrayList<>();
        KeycloakUserCredentials keycloakUserCredential = new KeycloakUserCredentials("password", password, false);
        keycloakUserCredentials.add(keycloakUserCredential);
        keycloakUser.setCredentials(keycloakUserCredentials);


        Integer status = (Integer) invoke(keycloakUser,company,jobTitle);//(JsonNode) vars.get("user"));

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode obj = mapper.createObjectNode();
        obj.put("status",status);
        return Response.status(Response.Status.fromStatusCode(status)).entity(obj).build();

    }

        private Object invoke(KeycloakUser data, String company, String jobTitle) throws JSONException {//String requestType, String url, Object data, MultiPart multipart, Map<String, Object> options) {
        Map<String, String> headers = new HashMap<>();
       /* AccessToken token = (AccessToken) request.getAttribute(KEYCLOAK_ACCESS_TOKEN);
        String keycloakServer = token.getIssuer();*/

            System.out.println("get issure");
            String keycloakServer= getIssuer(request.getHeader("Authorization"));
            String usersApiUrl = keycloakServer.replace("/auth/realms/", "/auth/admin/realms/").concat("/users");
        headers.put("Authorization", request.getHeader("Authorization"));
        headers.put("Content-Type", MediaType.APPLICATION_JSON);


         ClientConfig config = new DefaultClientConfig();
        config.getClasses().add(MultiPartWriter.class);
        Client client = Client.create(config);
        client.setConnectTimeout(3000);

        WebResource wr = client.resource(usersApiUrl);



        WebResource.Builder builder;
        builder = wr.accept(MediaType.APPLICATION_JSON);

       // Adding some headers if needed
        if (headers != null && !headers.isEmpty()) {
            for (String headerKey : headers.keySet()) {
                builder.header(headerKey, headers.get(headerKey));
            }
        }
        String multipart = null;
        String requestType = "POST";
        ClientResponse response;
        try {
            switch (requestType) {
                case "HEAD":
                case "GET":
                    response = builder.get(ClientResponse.class);
                    break;
                case "POST":
                    if (multipart != null) {
                        response = builder.post(ClientResponse.class, multipart);
                    } else {
                        response = builder.post(ClientResponse.class, data);
                    }
                    break;
                case "PUT":
                    if (multipart != null) {
                        response = builder.put(ClientResponse.class, multipart);
                    } else {
                        response = builder.put(ClientResponse.class, data);
                    }
                    break;
                case "DELETE":
                    response = builder.delete(ClientResponse.class, data);
                    break;
                default:
                    throw new NuxeoException("Unknown request type: " + requestType);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (response.getStatus() >= 200 && response.getStatus() < 300) {
            if (response.getStatus() == 201) {// user created successfully so go and create Nuxeo user

                UserManager userManager = Framework.getService(UserManager.class);
                DocumentModel newUserDoc = userManager.getBareUserModel();
                newUserDoc.setPropertyValue(UserConfig.USERNAME_COLUMN, data.getUsername());
                newUserDoc.setPropertyValue(UserConfig.PASSWORD_COLUMN,
                        UUID.randomUUID().toString());
                newUserDoc.setPropertyValue(UserConfig.FIRSTNAME_COLUMN, data.getFirstName());
                newUserDoc.setPropertyValue(UserConfig.LASTNAME_COLUMN, data.getLastName());
                newUserDoc.setPropertyValue(UserConfig.EMAIL_COLUMN, data.getEmail());
                newUserDoc.setPropertyValue(UserConfig.COMPANY_COLUMN, company);
                newUserDoc.setPropertyValue("jobTitle", jobTitle);
                //newUserDoc.setPropertyValue(UserConfig.GROUPS_COLUMN, groups.toArray());
                //newUserDoc.setPropertyValue(UserConfig.TENANT_ID_COLUMN,null);
                final DocumentModel userDoc = newUserDoc;
                new UnrestrictedSessionRunner(session) {
                    @Override
                    public void run() {
                        UserManager us = Framework.getService(UserManager.class);
                        DocumentModel user=userManager.createUser(userDoc);

                        UserProfileService userProfileService = Framework.getService(UserProfileService.class);
                        DocumentModel profile = userProfileService.getUserProfileDocument(data.getUsername(), session);

                        DocumentModel groupDoc = findGroup(CTS_INITIAL_GROUP);

                        if(groupDoc!=null)
                        {
                            List<String> users= userManager.getUsersInGroup(CTS_INITIAL_GROUP);
                            if (!users.contains(data.getUsername())) {
                                users.add(data.getUsername());
                                groupDoc.setProperty(groupSchemaName, userManager.getGroupMembersField(), users);
                                userManager.updateGroup(groupDoc);
                            }

                        }
                        
                    }
                }.runUnrestricted();


               // NuxeoPrincipal user = userManager.getPrincipal(data.getUsername());
                //return user;
                response.getStatus();
            }
            return response.getStatus();
        } else {
            return response.getStatus();
        }
    }

    private DocumentModel findGroup(String role) {
        UserManager userManager = Framework.getService(UserManager.class);

        Map<String, Serializable> query = new HashMap<>();
        query.put(userManager.getGroupIdField(), role);
        DocumentModelList groups = userManager.searchGroups(query, null);

        if (groups.isEmpty()) {
            return null;
        }
        return groups.get(0);
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Object doGet(@QueryParam("username") String username) throws JsonProcessingException, JSONException {

       // MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();


        //String username = queryParams.getFirst("username");


        List<KeycloakUserInfo> users =  invokeGet(username);

        ObjectMapper mapper = new ObjectMapper();
        //ObjectNode obj = mapper.createObjectNode();
        if(users!=null) {

            String usersAsJson = mapper.writeValueAsString(users);
            return Response.ok().entity(usersAsJson).build();
        }
        else
        {
            return Response.serverError().build();
        }

    }

    private List<KeycloakUserInfo> invokeGet(String username) throws JSONException {
        Map<String, String> headers = new HashMap<>();
        /*AccessToken token = (AccessToken) request.getAttribute(KEYCLOAK_ACCESS_TOKEN);
        String keycloakServer = token.getIssuer();*/
        System.out.println("get issure");
        String keycloakServer= getIssuer(request.getHeader("Authorization"));
        String usersApiUrl = keycloakServer.replace("/auth/realms/", "/auth/admin/realms/").concat("/users");
        headers.put("Authorization", request.getHeader("Authorization"));
        headers.put("Content-Type", MediaType.APPLICATION_JSON);


        ClientConfig config = new DefaultClientConfig();
        config.getClasses().add(MultiPartWriter.class);
        Client client = Client.create(config);
        client.setConnectTimeout(3000);

        WebResource wr = client.resource(usersApiUrl).queryParam("search",username).queryParam("first","0").queryParam("max","20");



        WebResource.Builder builder;
        builder = wr.accept(MediaType.APPLICATION_JSON);

        // Adding some headers if needed
        if (headers != null && !headers.isEmpty()) {
            for (String headerKey : headers.keySet()) {
                builder.header(headerKey, headers.get(headerKey));
            }
        }
        ClientResponse response;
        try {
                    response = builder.get(ClientResponse.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
            if (response.getStatus() == 200) {// users retrieved successfully

                List<LinkedHashMap> listOfUsers = response.getEntity(List.class);
                ObjectMapper mapper = new ObjectMapper();
                List<KeycloakUserInfo> users = mapper.convertValue(listOfUsers, new TypeReference<List<KeycloakUserInfo>>() { });

                return users;

        } else {
            return null;
        }
    }


    @POST
    @Path("create")
    @Produces(MediaType.APPLICATION_JSON)
    public Object doCreate(ExecutionRequest xreq) throws JsonProcessingException, JSONException {
        xreq.createContext(request, response, session);
        Map<String, Object> vars = xreq.getRestOperationContext().getVars();
        String userid = (String) vars.get("userKeycloackId");
        NuxeoPrincipal user =  invokeCreate(userid);

        ObjectMapper mapper = new ObjectMapper();
        //ObjectNode obj = mapper.createObjectNode();
        if(user!=null) {

            //String usersAsJson = mapper.writeValueAsString(user);
            return Response.ok().entity(user).build();
        }
        else
        {
            return Response.serverError().build();
        }

    }

    private NuxeoPrincipal invokeCreate(String userKeyclockId) throws JSONException {

        Map<String, String> headers = new HashMap<>();
        /*AccessToken token = (AccessToken) request.getAttribute(KEYCLOAK_ACCESS_TOKEN);
        String keycloakServer = token.getIssuer();
*/
        String keycloakServer= getIssuer(request.getHeader("Authorization"));
        String usersApiUrl = keycloakServer.replace("/auth/realms/", "/auth/admin/realms/").concat("/users");
        headers.put("Authorization", request.getHeader("Authorization"));
        headers.put("Content-Type", MediaType.APPLICATION_JSON);


        ClientConfig config = new DefaultClientConfig();
        config.getClasses().add(MultiPartWriter.class);
        Client client = Client.create(config);
        client.setConnectTimeout(3000);

        WebResource wr = client.resource(usersApiUrl).path(userKeyclockId);


        WebResource.Builder builder;
        builder = wr.accept(MediaType.APPLICATION_JSON);

        // Adding some headers if needed
        if (headers != null && !headers.isEmpty()) {
            for (String headerKey : headers.keySet()) {
                builder.header(headerKey, headers.get(headerKey));
            }
        }
        ClientResponse response;
        try {
            response = builder.get(ClientResponse.class);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (response.getStatus() == 200) {// users retrieved successfully

            Object listOfUsers = response.getEntity(Object.class);
            ObjectMapper mapper = new ObjectMapper();
            KeycloakUserInfo user = mapper.convertValue(listOfUsers, new TypeReference<KeycloakUserInfo>() {
            });

            /// here go and create nuxeo user
            UserManager userManager = Framework.getService(UserManager.class);
            DocumentModel newUserDoc = userManager.getBareUserModel();
            newUserDoc.setPropertyValue(UserConfig.USERNAME_COLUMN, user.getUsername());
            newUserDoc.setPropertyValue(UserConfig.PASSWORD_COLUMN,
                    UUID.randomUUID().toString());
            newUserDoc.setPropertyValue(UserConfig.FIRSTNAME_COLUMN, user.getFirstName());
            newUserDoc.setPropertyValue(UserConfig.LASTNAME_COLUMN, user.getLastName());
            newUserDoc.setPropertyValue(UserConfig.EMAIL_COLUMN, user.getEmail());

            final DocumentModel userDoc = newUserDoc;
            UserManager us = Framework.getService(UserManager.class);
            DocumentModel nuxeoUser = userManager.createUser(userDoc);

            UserProfileService userProfileService = Framework.getService(UserProfileService.class);
            DocumentModel profile = userProfileService.getUserProfileDocument(user.getUsername(), session);

            DocumentModel groupDoc = findGroup(CTS_INITIAL_GROUP);

            if (groupDoc != null) {
                List<String> users = userManager.getUsersInGroup(CTS_INITIAL_GROUP);
                if (!users.contains(user.getUsername())) {
                    users.add(user.getUsername());
                    groupDoc.setProperty(groupSchemaName, userManager.getGroupMembersField(), users);
                    userManager.updateGroup(groupDoc);
                }

            }
            NuxeoPrincipal principal = us.getPrincipal(user.getUsername());
            return  principal;
        } else {
            return null;
        }
    }
}
