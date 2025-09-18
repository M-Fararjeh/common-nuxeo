package web.sockets.notification.operations;

import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;
import web.sockets.notification.server.WebSocketEndPoint;
import web.sockets.notification.utils.NotificationContentDto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Operation(id = WebSocketPushMessage.ID, category = Constants.CAT_NOTIFICATION, label = "Websocket Push Message", description = "")
public class WebSocketPushMessage {
    public static final String ID = "Websocket.PushMessage";
    @Context
    protected OperationContext ctx;
    protected UserManager userManager = Framework.getService(UserManager.class);


    @OperationMethod
    public DocumentModel run(DocumentModel doc) {
        NotificationContentDto message = new NotificationContentDto();
        String execludedSessionId = (String) ctx.get("sessionId");


        message.setEvent("DOCUMENT_UPDATED");
        message.setSourceDocumentId(doc.getId());
        List<String> allUsers = Stream.of(doc.getACP().getACLs())
                .flatMap(acl -> Stream.of(acl.getACEs()))
                .filter(ACE::isGranted)
                .map(ACE::getUsername)
                .distinct()
                .flatMap(userName -> {
                    if (isUserExist(userName))
                        return Stream.of(userName);
                    if (isGroupExist(userName))
                        return userManager.getUsersInGroupAndSubGroups(userName).stream();
                     return Stream.empty();
                })
                .distinct()
                .collect(Collectors.toList());
        WebSocketEndPoint.sendMessage(message, allUsers,execludedSessionId);
        return doc;
    }
    private boolean isUserExist(String userName) {
        return userManager.getUserModel(userName) != null;
    }
    private boolean isGroupExist(String groupName) {
        return userManager.getGroupModel(groupName) != null;
    }
}
