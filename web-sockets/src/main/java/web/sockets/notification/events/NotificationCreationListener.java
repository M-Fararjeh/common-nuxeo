package web.sockets.notification.events;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.PostCommitFilteringEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.cluster.ClusterService;
import org.nuxeo.runtime.pubsub.AbstractPubSubBroker;
import web.sockets.notification.server.WebSocketEndPoint;
import web.sockets.notification.utils.NotificationContentDto;
import web.sockets.services.nuxeo.WebSocketService;

public class NotificationCreationListener implements PostCommitFilteringEventListener {
    private static final Logger log = LogManager.getLogger(NotificationCreationListener.class);


    @Override
    public boolean acceptEvent(Event event) {
        String eventName = event.getName();
        DocumentEventContext ctx = (DocumentEventContext) event.getContext();
        DocumentModel doc = ctx.getSourceDocument();
        return eventName.equals("documentCreated") && doc != null && doc.getType().equals("NotfNotificationContent");
    }

    @Override
    public void handleEvent(EventBundle eventBundle) {
        //log.error("Hello From handleEvent");
        for (Event event : eventBundle) {

            DocumentEventContext ctx = (DocumentEventContext) event.getContext();
            //log.error("ctx: " + ctx);

            if (ctx.getPrincipal() == null) {
                return;
            }

            //log.error("documentCreated event triggered");
            DocumentModel notificationContent = ctx.getSourceDocument();
            //log.error("notificationContent: " + notificationContent);

            NotificationContentDto notificationContentDto = new NotificationContentDto(notificationContent);
            notificationContentDto.setEvent("NOTIFICATION_CREATED");
            //log.error("notificationContentDto: " + notificationContentDto);
            WebSocketEndPoint.sendMessage(notificationContentDto);
            //if cluster mode is enabled then send the message through pub/sub service to avoid multi-nodes issues
            try{
                if(Framework.getService(ClusterService.class).isEnabled()){
                    AbstractPubSubBroker abstractPubSubBroker = Framework.getService(WebSocketService.class).getAbstractPubSubBroker();
                    abstractPubSubBroker.sendMessage(notificationContentDto);
                }
            }
            catch (Exception e){
                log.error("Exception occurred during send notification by pub/sub" + e);
                e.printStackTrace();
            }
        }

    }
}