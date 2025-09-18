package sa.comptechco.nuxeo.common.operations.listener;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mvel2.PropertyAccessException;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.comment.api.CommentEvents;
import org.nuxeo.ecm.platform.ec.notification.NotificationConstants;
import org.nuxeo.ecm.platform.ec.notification.NotificationEventListener;
import org.nuxeo.ecm.platform.ec.notification.NotificationImpl;
import org.nuxeo.ecm.platform.ec.notification.service.NotificationServiceHelper;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;
import sa.comptechco.nuxeo.common.operations.dto.NotificationServiceDTO;
import sa.comptechco.nuxeo.common.operations.dto.NotifyDTO;
import sa.comptechco.nuxeo.common.operations.dto.RecipientsDTO;
import sa.comptechco.nuxeo.common.operations.dto.SourceDTO;
import sa.comptechco.nuxeo.common.operations.service.NotificationService;
import sa.comptechco.nuxeo.common.operations.utils.LogEnum;
import sa.comptechco.nuxeo.common.operations.utils.LogUtil;

import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class CustomNotificationEventListener extends NotificationEventListener {

    private static Log logger = LogFactory.getLog(CustomNotificationEventListener.class);
    public static final String NOTIFICATION_EXTENDED_INFO_EMAIL = "org.nuxeo.comptech.email-config.path";
    public static final String NOTIFICATION_EXTENDED_INFO_EMAIL_DEFAULT = "/default-domain/workspaces/DMS-Configuration/email-config";

    @Override
    public void sendNotification(Event event, DocumentEventContext ctx) {
        Map<String, Serializable> eventInfo = ctx.getProperties();
        NotificationImpl notif = (NotificationImpl) eventInfo.get(NotificationConstants.NOTIFICATION_KEY);
        try {
            CoreSession session = event.getContext().getCoreSession();
            DocumentModel sourceDoc = ctx.getSourceDocument();

            if ((notif.getName().toLowerCase().equals(CommentEvents.COMMENT_ADDED.toLowerCase()) || (notif.getName().toLowerCase().equals(CommentEvents.COMMENT_UPDATED.toLowerCase()))) && (sourceDoc.getDocumentType().getName().equals("ExtRequest") || sourceDoc.getDocumentType().getName().equals("IntRequest"))) {
                String sourceUUID = (String) sourceDoc.getPropertyValue("req:externalEntity");
                try {
                    DocumentModel anotherDoc = session.getDocument(new IdRef(sourceUUID));
                    eventInfo.put("ExternalDoc", anotherDoc);
                } catch (DocumentNotFoundException e) {
                    LogUtil.log(logger, "Exception when read external property  " + e.getMessage(), LogEnum.ERROR);
                    e.printStackTrace();
                }

            }
            DocumentModel config = null;

            String pathRefValue = Framework.getProperty(NOTIFICATION_EXTENDED_INFO_EMAIL, NOTIFICATION_EXTENDED_INFO_EMAIL_DEFAULT);
            PathRef pathRef = new PathRef(pathRefValue);
            if (!session.exists(pathRef)) {
                throw new NuxeoException("System configuration is not found");
            }
            try {
                config = session.getDocument(pathRef);
                String jsonStr = (String) config.getPropertyValue("note:note");
                Gson gson = new Gson();
                Map<String, Serializable> map = gson.fromJson(jsonStr, Map.class);
                eventInfo.putAll(map);
            } catch (DocumentNotFoundException | JsonSyntaxException e) {
                LogUtil.log(logger, "Exception when read inject extra data config file " + e.getMessage(), LogEnum.ERROR);
                e.printStackTrace();
            }

        } catch (Exception e) {
            LogUtil.log(logger, "Exception when prepare inject extra data into message " + e.getMessage(), LogEnum.ERROR);
            e.printStackTrace();
        }
        sendEmailNotification(event, ctx);
        System.out.println("custom listener");


        sa.comptechco.nuxeo.common.operations.service.NotificationService notificationService = Framework.getService(NotificationService.class);
        LogUtil.log(logger, "Start Notification action", LogEnum.INFO);

        System.out.println("Start Notification action");
        Map<String, Object> infoMap = new HashMap<>();
        NotifyDTO notifyDTO = null;
        try {
            notifyDTO = prepareNotifyDTO(ctx);
        } catch (Exception e) {
            LogUtil.log(logger, "Exception when prepare notification message " + e.getMessage(), LogEnum.ERROR);
            e.printStackTrace();
        }


        try {
            String channelName = "SYSTEM";
            NotificationServiceDTO notificationServiceDTO = new NotificationServiceDTO();
            notificationServiceDTO.setNotificationKey(notif.getName());
            notificationServiceDTO.setChannelName(channelName);
            notificationServiceDTO.setNotifyDTO(notifyDTO);
            notificationService.callNotification(notificationServiceDTO);
        } catch (Exception e) {
            LogUtil.log(logger, "Exception when callNotification", LogEnum.ERROR);
            e.printStackTrace();
        }

    }

    public void sendEmailNotification(Event event, DocumentEventContext ctx) {
        Boolean isCreator=false;
        DocumentModel sourceDoc = ctx.getSourceDocument();
        String eventId = event.getName();
        logger.debug("Received a message for notification sender with eventId : " + eventId);

        Map<String, Serializable> eventInfo = ctx.getProperties();
        String userDest = (String) eventInfo.get(NotificationConstants.DESTINATION_KEY);
        NotificationImpl notif = (NotificationImpl) eventInfo.get(NotificationConstants.NOTIFICATION_KEY);

        // send email
        NuxeoPrincipal recepient = NotificationServiceHelper.getUsersService().getPrincipal(userDest);
        if (recepient == null) {
            logger.error("Couldn't find user: " + userDest + " to send her a mail.");
            return;
        }
        String authorUsername = (String) eventInfo.get(NotificationConstants.AUTHOR_KEY);



        if ((notif.getName().toLowerCase().equals(CommentEvents.COMMENT_ADDED.toLowerCase()) || (notif.getName().toLowerCase().equals(CommentEvents.COMMENT_UPDATED.toLowerCase())))) {
            if (recepient.getName().equals(authorUsername)) {

                logger.error("No email will be sent to comment creator when comment added or updated.");
                isCreator=true;
            }

        }
        if(!isCreator) {
            String email = recepient.getEmail();
            if (email == null || "".equals(email)) {
                logger.error("No email found for user: " + userDest);
                return;
            }

            String subjectTemplate = notif.getSubjectTemplate();

            String mailTemplate = null;
            // mail template can be dynamically computed from a MVEL expression
            if (notif.getTemplateExpr() != null) {
                try {
                    mailTemplate = getEmailHelper().evaluateMvelExpresssion(notif.getTemplateExpr(), eventInfo);
                } catch (PropertyAccessException pae) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Cannot evaluate mail template expression '" + notif.getTemplateExpr()
                                + "' in that context " + eventInfo, pae);
                    }
                }
            }
            // if there is no mailTemplate evaluated, use the defined one
            if (StringUtils.isEmpty(mailTemplate)) {
                mailTemplate = notif.getTemplate();
            }

            logger.debug("email: " + email);
            logger.debug("mail template: " + mailTemplate);
            logger.debug("subject template: " + subjectTemplate);

            Map<String, Object> mail = new HashMap<>();
            mail.put("mail.to", email);

          //  String authorUsername = (String) eventInfo.get(NotificationConstants.AUTHOR_KEY);

            if (authorUsername != null) {
                NuxeoPrincipal author = NotificationServiceHelper.getUsersService().getPrincipal(authorUsername);
                mail.put(NotificationConstants.PRINCIPAL_AUTHOR_KEY, author);
            }

            mail.put(NotificationConstants.DOCUMENT_KEY, ctx.getSourceDocument());

            String subject = notif.getSubject() == null ? NotificationConstants.NOTIFICATION_KEY : notif.getSubject();

            if ((notif.getName().toLowerCase().equals(CommentEvents.COMMENT_ADDED.toLowerCase()) || (notif.getName().toLowerCase().equals(CommentEvents.COMMENT_UPDATED.toLowerCase()))) && (sourceDoc.getDocumentType().getName().equals("ExtRequest") || sourceDoc.getDocumentType().getName().equals("IntRequest"))) {
                String requestNo = (String) sourceDoc.getPropertyValue("req:number");

                subject = String.format("New comment on request no: %s", requestNo);
            }
            subject = NotificationServiceHelper.getNotificationService().getEMailSubjectPrefix() + subject;
            mail.put("subject", subject);
            mail.put("template", mailTemplate);
            mail.put("subjectTemplate", subjectTemplate);

            // Transferring all data from event to email
            for (String key : eventInfo.keySet()) {
                mail.put(key, eventInfo.get(key) == null ? "" : eventInfo.get(key));
                logger.debug("Mail prop: " + key);
            }

            mail.put(NotificationConstants.EVENT_ID_KEY, eventId);

            try {
                getEmailHelper().sendmail(mail);
            } catch (MessagingException e) {
                String cause = "";
                if ((e instanceof SendFailedException) && (e.getCause() instanceof SendFailedException)) {
                    cause = " - Cause: " + e.getCause().getMessage();
                }
                logger.warn("Failed to send notification email to '" + email + "': " + e.getClass().getName() + ": "
                        + e.getMessage() + cause);
            }
        }
    }

    public NotifyDTO prepareNotifyDTO(DocumentEventContext ctx) throws Exception {

        Map<String, Serializable> eventInfo = ctx.getProperties();
        String userDest = (String) eventInfo.get(NotificationConstants.DESTINATION_KEY);


        // send email
        NuxeoPrincipal recepient = NotificationServiceHelper.getUsersService().getPrincipal(userDest);
        if (recepient == null) {
            logger.error("Couldn't find user: " + userDest + " to send her a mail.");
            throw new Exception("subscribed User not found");
        }
        // RecipientsDTO

        List<String> userIds = new ArrayList<String>();
        userIds.add(userDest);

        RecipientsDTO recipientsDTO = new RecipientsDTO();
        recipientsDTO.setUserIds(userIds);


        // add commonData
        Gson gson = new Gson();
        String data = "{}";
        JsonObject jo = gson.fromJson(data, JsonObject.class);
        HashMap<String, Object> dataMap = new Gson().fromJson(jo.toString(), HashMap.class);

        SourceDTO sourceDTO = new SourceDTO();
        sourceDTO.setSourceId(ctx.getSourceDocument().getId());
        sourceDTO.setSourceType("Document");
        sourceDTO.setSourceName(ctx.getSourceDocument().getTitle());
        sourceDTO.setData(dataMap);
        sourceDTO.setApplication("ctsn");


        // NotifyDTO
        gson = new Gson();
        jo = gson.fromJson("{}", JsonObject.class);
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
                for (String u : grpUsers) {
                    System.out.println("User: " + u + "\n");
                    users.add(u);
                }
            }


        }
        List<String> listWithoutDuplicates = users.stream().distinct().collect(Collectors.toList());
        return listWithoutDuplicates;
    }
}
