package web.sockets.services.nuxeo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.pubsub.AbstractPubSubBroker;
import web.sockets.notification.pubsub.NotificationPubSubBroker;

public class WebSocketServiceImpl extends DefaultComponent implements WebSocketService{
    private AbstractPubSubBroker abstractPubSubBroker = new NotificationPubSubBroker();
    private static final Log log = LogFactory.getLog(WebSocketServiceImpl.class);
    @Override
    public void start(ComponentContext context) {
        super.start(context);
        initializeAbstractPubSubBroker();
    }

    @Override
    public void initializeAbstractPubSubBroker() {
        abstractPubSubBroker.initialize("pushNotification", Framework.getProperty("nuxeo.cluster.nodeid"));
    }

    @Override
    public AbstractPubSubBroker getAbstractPubSubBroker() {
        return this.abstractPubSubBroker;
    }
}
