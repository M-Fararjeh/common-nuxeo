package sa.comptechco.nuxeo.common.operations.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.multipart.impl.MultiPartWriter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.api.Framework;
import sa.comptechco.nuxeo.common.operations.dto.NotificationServiceDTO;
import sa.comptechco.nuxeo.common.operations.dto.NotifyDTO;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationServiceImpl implements NotificationService{

    private Map<String, String> headers = new HashMap<>();
    private Client client;
    private final String baseUrl ;//= "http://3.95.77.89:8060/";

    private static Log logger = LogFactory.getLog(NotificationServiceImpl.class);

    public static final String NOTIFICATION_SERVER_URL_DEFAULT = "http://34.204.99.135:8060/";
    public static final String NOTIFICATION_SERVER_TOKEN_DEFAULT = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJvZlRkU01GWVpTWWxQdFhickN3QklsNFBxQkY4U1pxQXZuQWt2b29YLWlnIn0.eyJleHAiOjE2MTkxMzU2MDQsImlhdCI6MTYxOTA5OTYwNCwianRpIjoiMmY0NDA0YWYtZDcxOS00Mjc5LWI0NjUtMzk4ZDYxMzlmZmQ5IiwiaXNzIjoiaHR0cDovLzMuMjM4LjIzMC4zNzo4MDgxL2F1dGgvcmVhbG1zL251eGVvIiwiYXVkIjoiYWNjb3VudCIsInN1YiI6IjdkYzY1ZmI0LTc5NmEtNDQ1YS1iMmRlLWQ4NTdmNTNkZWFjZSIsInR5cCI6IkJlYXJlciIsImF6cCI6IndlYl9hcHAiLCJzZXNzaW9uX3N0YXRlIjoiMGE2NmNkMzAtNDI5ZS00MmZmLWI3ODctZDIyNWU2MGVmMzY1IiwiYWNyIjoiMSIsImFsbG93ZWQtb3JpZ2lucyI6WyIqIl0sInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJvZmZsaW5lX2FjY2VzcyIsIlJPTEVfQURNSU4iLCJibXNfcm9sZV9zeXNfVXNlciIsInVtYV9hdXRob3JpemF0aW9uIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJvcGVuaWQgamhpcHN0ZXIgZW1haWwgcHJvZmlsZSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJuYW1lIjoiQWRtaW4gQWRtaW5pc3RyYXRvciIsInByZWZlcnJlZF91c2VybmFtZSI6ImFkbWluIiwiZ2l2ZW5fbmFtZSI6IkFkbWluIiwiZmFtaWx5X25hbWUiOiJBZG1pbmlzdHJhdG9yIiwiZW1haWwiOiJhZG1pbkBsb2NhbGhvc3QifQ.DgT9Cbir8fNN8gUz-stmgul1Zoxr-eGULELzoaIomPQiVvphyrGNC8OoxwBbH4heUPXl3orJHMuepsZpU1_zWXpTxp2Y2edeDlbDzWEf37zefJtUy6diuyfxP2FYA72Oy6-UpvDovlHMb6VaW5G-fp6HpCPUMM0sjt4PFVw3Ztoe7apglJk9yAh88r8mAkBRuFj4wGHqu0PmECMG0yW-ZykTJ3PZLzgk43q8jYMiFEiaztFUc-vtbYovz6HkkGCLpYizNPyS15eb7WJt8BNJ2yd0fvG0gm4G2X0QRDuVFKWoVkfP0tOO712RBlLAoxigYx6iHrmIFpb_Q6ZmCKTmUg";
            //"eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJfQmhHSmZqOEhqVm5zd0lueXIwdjdWR19oZUdqYU9JV3Q2M0NfcFhqeUVjIn0.eyJqdGkiOiJjZTA5NmUwMC0zNGQ3LTRkYjItYjRjOS1mZWMwYWQzODQ4MDgiLCJleHAiOjE1ODY1MDc4MjAsIm5iZiI6MCwiaWF0IjoxNTg2MDc1ODIwLCJpc3MiOiJodHRwOi8vZWMyLTM0LTIyNi0yNDktMTc0LmNvbXB1dGUtMS5hbWF6b25hd3MuY29tOjkwODAvYXV0aC9yZWFsbXMvZWtoYWEiLCJhdWQiOiJnYXRld2F5LXNlcnZpY2UiLCJzdWIiOiJiYTIyMTc2Zi1jNDRhLTQ2ZTUtYjlmZS0zZWVjMzM5NmNjYzAiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJnYXRld2F5LXNlcnZpY2UiLCJhdXRoX3RpbWUiOjAsInNlc3Npb25fc3RhdGUiOiIwMzM1MDQ3OS1kMmQ5LTRkNTktYTFhZC1kY2UwMzBiOTE4MGQiLCJhY3IiOiIxIiwiYWxsb3dlZC1vcmlnaW5zIjpbImh0dHA6Ly9lYzItMTgtMjA0LTE5OC00MS5jb21wdXRlLTEuYW1hem9uYXdzLmNvbTo4MDgwLyoiLCIqIiwiaHR0cDovL2VjMi0zNC0yMjYtMjQ5LTE3NC5jb21wdXRlLTEuYW1hem9uYXdzLmNvbTo4MDgwLyoiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIlJPTEVfVVNFUiIsIm9mZmxpbmVfYWNjZXNzIiwiUk9MRV9BRE1JTiIsInVtYV9hdXRob3JpemF0aW9uIiwiZnVsbCJdfSwicmVzb3VyY2VfYWNjZXNzIjp7InJlYWxtLW1hbmFnZW1lbnQiOnsicm9sZXMiOlsidmlldy1yZWFsbSIsInZpZXctaWRlbnRpdHktcHJvdmlkZXJzIiwibWFuYWdlLWlkZW50aXR5LXByb3ZpZGVycyIsImltcGVyc29uYXRpb24iLCJyZWFsbS1hZG1pbiIsImNyZWF0ZS1jbGllbnQiLCJtYW5hZ2UtdXNlcnMiLCJxdWVyeS1yZWFsbXMiLCJ2aWV3LWF1dGhvcml6YXRpb24iLCJxdWVyeS1jbGllbnRzIiwicXVlcnktdXNlcnMiLCJtYW5hZ2UtZXZlbnRzIiwibWFuYWdlLXJlYWxtIiwidmlldy1ldmVudHMiLCJ2aWV3LXVzZXJzIiwidmlldy1jbGllbnRzIiwibWFuYWdlLWF1dGhvcml6YXRpb24iLCJtYW5hZ2UtY2xpZW50cyIsInF1ZXJ5LWdyb3VwcyJdfSwiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoib3BlbmlkIG1pY3Jvc2VydmljZSBlbWFpbCBwcm9maWxlIiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJyb2xlcyI6WyJ1bWFfYXV0aG9yaXphdGlvbiIsImZ1bGwiLCJST0xFX1VTRVIiLCJST0xFX0FETUlOIiwib2ZmbGluZV9hY2Nlc3MiLCJST0xFX1VTRVIiLCJST0xFX0FETUlOIl0sImdyb3VwcyI6WyJBZG1pbnMiXSwicHJlZmVycmVkX3VzZXJuYW1lIjoiYWRtaW4iLCJsb2NhbGUiOiJlbiIsImVtYWlsIjoiYWRtaW5AYWRtaW4uY29tIn0.lgSwEG4OF37u6Euy5aphxxS3Pt6LHo97ehick3gd2zEYH8XCTt7ktiL5uH84a4eTON5horMnkV0sVIoITZ3oQFhsSvUsuFJf-Ch5FlVh0wQmNJvwzk5q95t1JXH2CLcb4eunXTeSzXyo7SqqC2AjnpAjqFfOwVe3kwDHvp-d4VUBWdN53Lvv9RQjSFImbQny0VUgrZ5l1r7_jJ0YqCk_A8BWAkUm8Bzzj9-M7xUiFsR3mMs8Ow5MRMKNlDLrnClupJsXzSDGjDXO7yZqWmZcrPIZ8v_yDMC-WLmLA-VDjXp1rloeSqnRv28itz27cTQgyb2iT5Y1pUcjagUzGDYKFA";

    public static final String NOTIFICATION_SERVER_URL = "org.nuxeo.tdf.notification.server.url";
    public static final String NOTIFICATION_SERVER_TOKEN = "org.nuxeo.tdf.notification.server.token";

    public static final String NOTIFICATION_SERVER_CONNECTION_TIMEOUT = "org.nuxeo.tdf.notification.server.connect.timeout";
    public static final String NOTIFICATION_SERVER_CONNECTION_TIMEOUT_DEFAULT = "3000";



    public NotificationServiceImpl() {



        baseUrl = Framework.getProperty(NOTIFICATION_SERVER_URL, NOTIFICATION_SERVER_URL_DEFAULT);
        String token = Framework.getProperty(NOTIFICATION_SERVER_TOKEN,NOTIFICATION_SERVER_TOKEN_DEFAULT);
        //headers.put("Authorization",token );
        headers.put("Content-Type", MediaType.APPLICATION_JSON);

        ClientConfig config = new DefaultClientConfig();
        config.getClasses().add(MultiPartWriter.class);
        client = Client.create(config);
        String timeout = Framework.getProperty(NOTIFICATION_SERVER_CONNECTION_TIMEOUT,NOTIFICATION_SERVER_CONNECTION_TIMEOUT_DEFAULT);
        client.setConnectTimeout(Integer.parseInt(timeout));

    }

    @Override
    public void callNotification(NotificationServiceDTO notificationServiceDTO) {
        String notificationKey = notificationServiceDTO.getNotificationKey();
        String channel = notificationServiceDTO.getChannelName();
        NotifyDTO notifyDTO = notificationServiceDTO.getNotifyDTO();
        String targetUrl =  String.format("%sapi/notify/%s/%s",baseUrl,notificationKey,channel);

        WebResource wr = client.resource(targetUrl);


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
            logger.error("notification DTO"+notifyDTO);
           // response = builder.entity(notifyDTO,MediaType.APPLICATION_JSON_TYPE).post(ClientResponse.class);
           // response = builder.post(ClientResponse.class, notifyDTO);
            response = builder.post(ClientResponse.class, writeJSON(notifyDTO));
            logger.error("notification responce"+response.getStatus()+response);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }


    private String writeJSON(NotifyDTO notDTO) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String jsonInString = mapper.writeValueAsString(notDTO);
        return  jsonInString;
    }
}
