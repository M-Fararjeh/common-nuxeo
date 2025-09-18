package web.sockets.services.nuxeo;

import org.nuxeo.runtime.pubsub.AbstractPubSubBroker;

public interface WebSocketService {
    void initializeAbstractPubSubBroker();
    AbstractPubSubBroker getAbstractPubSubBroker();
}
