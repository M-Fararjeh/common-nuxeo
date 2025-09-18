package web.sockets.notification.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.nuxeo.runtime.pubsub.AbstractPubSubBroker;
import web.sockets.notification.server.WebSocketEndPoint;
import web.sockets.notification.utils.NotificationContentDto;

import java.io.IOException;
import java.io.InputStream;
public class NotificationPubSubBroker extends AbstractPubSubBroker<NotificationContentDto>{
    @Override
    public NotificationContentDto deserialize(InputStream inputStream) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(inputStream, NotificationContentDto.class);
    }

    @Override
    public void receivedMessage(NotificationContentDto notificationContentDto) {
        WebSocketEndPoint.sendMessage(notificationContentDto);
    }
}
