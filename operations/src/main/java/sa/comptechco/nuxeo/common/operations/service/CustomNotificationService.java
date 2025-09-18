package sa.comptechco.nuxeo.common.operations.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.NuxeoGroup;
import org.nuxeo.ecm.platform.ec.notification.NotificationListenerHook;
import org.nuxeo.ecm.platform.ec.notification.email.EmailHelper;
import org.nuxeo.ecm.platform.ec.notification.service.GeneralSettingsDescriptor;
import org.nuxeo.ecm.platform.ec.notification.service.NotificationListenerVetoRegistry;
import org.nuxeo.ecm.platform.ec.notification.service.NotificationService;
import org.nuxeo.ecm.platform.notification.api.NotificationRegistry;
import org.nuxeo.ecm.platform.url.api.DocumentViewCodecManager;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentName;
import org.nuxeo.runtime.model.Extension;
import sa.comptechco.nuxeo.common.operations.dto.NotificationServiceDTO;
import sa.comptechco.nuxeo.common.operations.dto.NotifyDTO;
import sa.comptechco.nuxeo.common.operations.dto.RecipientsDTO;
import sa.comptechco.nuxeo.common.operations.dto.SourceDTO;
import sa.comptechco.nuxeo.common.operations.utils.LogEnum;
import sa.comptechco.nuxeo.common.operations.utils.LogUtil;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class CustomNotificationService extends NotificationService {
    public static final ComponentName NAME = new ComponentName(
            "sa.comptechco.nuxeo.common.operations.service.CustomNotificationService");

    private static Log logger = LogFactory.getLog(CustomNotificationService.class);
    protected static final String NOTIFICATIONS_EP = "notifications";

    protected static final String TEMPLATES_EP = "templates";

    protected static final String GENERAL_SETTINGS_EP = "generalSettings";

    protected static final String NOTIFICATION_HOOK_EP = "notificationListenerHook";

    protected static final String NOTIFICATION_VETO_EP = "notificationListenerVeto";

    // FIXME: performance issue when putting URLs in a Map.
    protected static final Map<String, URL> TEMPLATES_MAP = new HashMap<>();

    protected EmailHelper emailHelper = new EmailHelper();

    protected GeneralSettingsDescriptor generalSettings;

    protected NotificationRegistry notificationRegistry;

    protected DocumentViewCodecManager docLocator;

    protected final Map<String, NotificationListenerHook> hookListeners = new HashMap<>();

    protected NotificationListenerVetoRegistry notificationVetoRegistry;


    @Override
    public void sendNotification(String notificationName, Map<String, Object> infoMap, String userPrincipal) {
        super.sendNotification(notificationName, infoMap, userPrincipal);


        sa.comptechco.nuxeo.common.operations.service.NotificationService  notificationService = Framework.getService(NotificationServiceImpl.class);
        LogUtil.log(logger, "Start Notification action", LogEnum.INFO);

        System.out.println("Start Notification action");
        NotifyDTO notifyDTO = prepareNotifyDTO(notificationName,infoMap,userPrincipal);


        try {

            NotificationServiceDTO notificationServiceDTO = new NotificationServiceDTO();

            notificationServiceDTO.setNotificationKey(notificationName);
            notificationServiceDTO.setChannelName("SYSTEM");
            notificationServiceDTO.setNotifyDTO(notifyDTO);
            notificationService.callNotification(notificationServiceDTO);
        } catch (IOException e) {
            LogUtil.log(logger, "Exception when callNotification", LogEnum.ERROR);
            e.printStackTrace();
        }



    }
    public NotifyDTO prepareNotifyDTO(String notificationName, Map<String, Object> infoMap, String userPrincipal) {


        // RecipientsDTO

        List<String> userIds = new ArrayList<String>();
        userIds.add(userPrincipal);

        RecipientsDTO recipientsDTO = new RecipientsDTO();
        recipientsDTO.setUserIds(userIds);


        // add commonData
        Gson gson = new Gson();
        String data="{}";
        JsonObject jo = gson.fromJson(data, JsonObject.class);
        HashMap<String, Object> dataMap = new Gson().fromJson(jo.toString(), HashMap.class);

        SourceDTO sourceDTO = new SourceDTO();
        sourceDTO.setSourceId("");
        sourceDTO.setSourceType("Case");
        sourceDTO.setSourceName("");
        sourceDTO.setData(dataMap);
        sourceDTO.setApplication("ctsn");


        // NotifyDTO
        gson = new Gson();
        jo = gson.fromJson("", JsonObject.class);
        HashMap<String, Object> apiDataMap = new Gson().fromJson(jo.toString(), HashMap.class);
        //apiData.put("id", input.getId());
        //apiData.put("id", "1");

        NotifyDTO notifyDTO = new NotifyDTO();
        notifyDTO.setRecipients(recipientsDTO);
        notifyDTO.setSource(sourceDTO);
        notifyDTO.setSendDate(new Date().toString());
        notifyDTO.setApplicationKey("ctsn");
        notifyDTO.setApiData(apiDataMap);

        LogUtil.log(logger, "Object NotifyDTO prepared successfully", LogEnum.INFO);

        return notifyDTO;
    }

    public List<String> retrieveUsersList(StringList users, StringList groups) {
        if ((groups != null) && (groups.size() > 0)) {
            UserManager userManager = Framework.getService(UserManager.class);

            for (String grp : groups) {
                NuxeoGroup group = userManager.getGroup(grp);
                String label = group.getLabel();
                System.out.println("Group: " + label);
                List<String> grpUsers = group.getMemberUsers();
                for(String u: grpUsers) {
                    System.out.println("User: " + u + "\n");
                    users.add(u);
                }
            }


        }
        List<String> listWithoutDuplicates = users.stream().distinct().collect(Collectors.toList());
        return listWithoutDuplicates;
    }

    @Override
    public void registerExtension(Extension extension) {
        super.registerExtension(extension);
    }

    @Override
    protected void registerGeneralSettings(GeneralSettingsDescriptor desc) {
        super.registerGeneralSettings(desc);
    }

    @Override
    public NotificationListenerVetoRegistry getNotificationListenerVetoRegistry() {
        return super.getNotificationListenerVetoRegistry();
    }
}




