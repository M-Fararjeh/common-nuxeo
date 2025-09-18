package sa.comptechco.nuxeo.common.operations.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sa.comptechco.nuxeo.common.operations.dto.NotificationServiceDTO;

import java.io.IOException;

public class NotificationServiceImplold implements NotificationService {

    private static Log logger = LogFactory.getLog(NotificationServiceImplold.class);

    private final String baseUrl = "http://3.95.77.89:8060/";

    private NotificationRetrofitService ctsNotificationRetrofitService;


    public NotificationServiceImplold() {

      /*  OkHttpClient client = new OkHttpClient().newBuilder().addInterceptor(new Interceptor() {
            @Override
            public okhttp3.Response intercept(@NonNull Chain chain) throws IOException {
                Request request = chain.request()
                        .newBuilder()
                        //.addHeader("Authorization", "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJfQmhHSmZqOEhqVm5zd0lueXIwdjdWR19oZUdqYU9JV3Q2M0NfcFhqeUVjIn0.eyJqdGkiOiJjZTA5NmUwMC0zNGQ3LTRkYjItYjRjOS1mZWMwYWQzODQ4MDgiLCJleHAiOjE1ODY1MDc4MjAsIm5iZiI6MCwiaWF0IjoxNTg2MDc1ODIwLCJpc3MiOiJodHRwOi8vZWMyLTM0LTIyNi0yNDktMTc0LmNvbXB1dGUtMS5hbWF6b25hd3MuY29tOjkwODAvYXV0aC9yZWFsbXMvZWtoYWEiLCJhdWQiOiJnYXRld2F5LXNlcnZpY2UiLCJzdWIiOiJiYTIyMTc2Zi1jNDRhLTQ2ZTUtYjlmZS0zZWVjMzM5NmNjYzAiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJnYXRld2F5LXNlcnZpY2UiLCJhdXRoX3RpbWUiOjAsInNlc3Npb25fc3RhdGUiOiIwMzM1MDQ3OS1kMmQ5LTRkNTktYTFhZC1kY2UwMzBiOTE4MGQiLCJhY3IiOiIxIiwiYWxsb3dlZC1vcmlnaW5zIjpbImh0dHA6Ly9lYzItMTgtMjA0LTE5OC00MS5jb21wdXRlLTEuYW1hem9uYXdzLmNvbTo4MDgwLyoiLCIqIiwiaHR0cDovL2VjMi0zNC0yMjYtMjQ5LTE3NC5jb21wdXRlLTEuYW1hem9uYXdzLmNvbTo4MDgwLyoiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIlJPTEVfVVNFUiIsIm9mZmxpbmVfYWNjZXNzIiwiUk9MRV9BRE1JTiIsInVtYV9hdXRob3JpemF0aW9uIiwiZnVsbCJdfSwicmVzb3VyY2VfYWNjZXNzIjp7InJlYWxtLW1hbmFnZW1lbnQiOnsicm9sZXMiOlsidmlldy1yZWFsbSIsInZpZXctaWRlbnRpdHktcHJvdmlkZXJzIiwibWFuYWdlLWlkZW50aXR5LXByb3ZpZGVycyIsImltcGVyc29uYXRpb24iLCJyZWFsbS1hZG1pbiIsImNyZWF0ZS1jbGllbnQiLCJtYW5hZ2UtdXNlcnMiLCJxdWVyeS1yZWFsbXMiLCJ2aWV3LWF1dGhvcml6YXRpb24iLCJxdWVyeS1jbGllbnRzIiwicXVlcnktdXNlcnMiLCJtYW5hZ2UtZXZlbnRzIiwibWFuYWdlLXJlYWxtIiwidmlldy1ldmVudHMiLCJ2aWV3LXVzZXJzIiwidmlldy1jbGllbnRzIiwibWFuYWdlLWF1dGhvcml6YXRpb24iLCJtYW5hZ2UtY2xpZW50cyIsInF1ZXJ5LWdyb3VwcyJdfSwiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoib3BlbmlkIG1pY3Jvc2VydmljZSBlbWFpbCBwcm9maWxlIiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJyb2xlcyI6WyJ1bWFfYXV0aG9yaXphdGlvbiIsImZ1bGwiLCJST0xFX1VTRVIiLCJST0xFX0FETUlOIiwib2ZmbGluZV9hY2Nlc3MiLCJST0xFX1VTRVIiLCJST0xFX0FETUlOIl0sImdyb3VwcyI6WyJBZG1pbnMiXSwicHJlZmVycmVkX3VzZXJuYW1lIjoiYWRtaW4iLCJsb2NhbGUiOiJlbiIsImVtYWlsIjoiYWRtaW5AYWRtaW4uY29tIn0.lgSwEG4OF37u6Euy5aphxxS3Pt6LHo97ehick3gd2zEYH8XCTt7ktiL5uH84a4eTON5horMnkV0sVIoITZ3oQFhsSvUsuFJf-Ch5FlVh0wQmNJvwzk5q95t1JXH2CLcb4eunXTeSzXyo7SqqC2AjnpAjqFfOwVe3kwDHvp-d4VUBWdN53Lvv9RQjSFImbQny0VUgrZ5l1r7_jJ0YqCk_A8BWAkUm8Bzzj9-M7xUiFsR3mMs8Ow5MRMKNlDLrnClupJsXzSDGjDXO7yZqWmZcrPIZ8v_yDMC-WLmLA-VDjXp1rloeSqnRv28itz27cTQgyb2iT5Y1pUcjagUzGDYKFA")
                        .build();
                return chain.proceed(request);
            }
        }).build();

        Retrofit retrofit = new Retrofit.Builder()
                .client(client)
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create()).build();

        this.ctsNotificationRetrofitService = retrofit.create(NotificationRetrofitService.class);

        LogUtil.log(logger, "CtsNotificationRetrofitService connected successfully", LogEnum.INFO);
*/
    }


    @Override
    public void callNotification(NotificationServiceDTO notificationServiceDTO) throws IOException {
        /*
        String notificationKey = notificationServiceDTO.getNotificationKey();
        String channelName = notificationServiceDTO.getChannelName();
        NotifyDTO notifyDTO = notificationServiceDTO.getNotifyDTO();
        LogUtil.log(logger, "Start call noify from notificationservice", LogEnum.INFO);
        Call<Object> notify = ctsNotificationRetrofitService.notify(notificationKey, channelName, notifyDTO);
        Response<Object> execute = notify.execute();
        LogUtil.log(logger, "noify from notificationservice called successfully", LogEnum.INFO);
        LogUtil.log(logger, "noify calling result : " + execute.body(), LogEnum.DEBUG);
        return execute.body();
       */

    }

    
}
