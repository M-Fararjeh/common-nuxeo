package web.sockets.notification.server;
import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import web.sockets.notification.utils.MessageEncoder;
import web.sockets.notification.utils.NotificationContentDto;

import org.apache.tomcat.websocket.WsSession;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.*;

@ServerEndpoint(
        value = "/notifications",
        encoders = {MessageEncoder.class}
)
public class WebSocketEndPoint  {

    private static final Logger log = LogManager.getLogger(WebSocketEndPoint.class);

    private static final Map<String, List<Session>> userSessions = Collections.synchronizedMap(new HashMap<>());
    @OnOpen
    public void onOpen(Session session) {
        //log.error("WebSocket connection opened: ");
        registerUserSession(session);
    }


    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        //log.error("Message received: " + message);
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        //log.error("WebSocket connection closed: " + session.getId());
        removeUserSession(session);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        //log.error("WebSocket error: " + throwable.getMessage());
    }

    private void registerUserSession(Session session) {
        String userName = session.getUserPrincipal().getName();
        if (userName!=null)
            userSessions.computeIfAbsent(userName, k -> new ArrayList<>()).add(session);
    }

    private void removeUserSession(Session session) {
        String userName =  session.getUserPrincipal().getName();
        if (userName != null) {
            userSessions.computeIfPresent(userName, (key, sessions) -> {
                sessions.remove(session);
                return sessions.isEmpty() ? null : sessions;
            });
        }
    }
    public static void sendMessage(NotificationContentDto message) {
        String userName = message.getRecipientUser();
        sendMessage(message,userName,null);
    }
    public static void sendMessage(NotificationContentDto message,List<String> users, String execludedSessionId) {
        users.forEach(userName -> sendMessage(message,userName,execludedSessionId));
    }
    private static void sendMessage(NotificationContentDto message,String userName,String execludedSessionId) {
        //log.error("sendDocumentCreationMessage to user: " + userName);

        List<Session> sessions = userSessions.get(userName);

        if (sessions == null || sessions.isEmpty()) {
            //log.error("No active sessions found for user: " + userName);
            return;
        }
        Set<String> seenSessionIds = new HashSet<>();
        sessions.stream().filter(Session::isOpen).forEach(session -> {
            try {
                String httpSessionId = getHttpSessionId(session);
                if(httpSessionId!=null) {
                    if (!httpSessionId.equals(execludedSessionId)) {
                       // if(!seenSessionIds.contains(httpSessionId)) {
                           // session.getBasicRemote().sendObject(message);
                            seenSessionIds.add(httpSessionId);
                            session.getBasicRemote().sendObject(message);
                            //log.error("Message sent to user: " + userName);
                       // }
                    }
                }
                else{
                    session.getBasicRemote().sendObject(message);
                    //log.error("Message sent to user: " + userName);
                }
            } catch (IOException | EncodeException e) {
                //log.error("Error sending message to user " + userName + ": " + e.getMessage());
            }
        });
    }


    public static String getHttpSessionId(Session session) {
        org.apache.tomcat.websocket.WsSession wsSocket = (org.apache.tomcat.websocket.WsSession) session;
        return wsSocket.getHttpSessionId();

    }
}

