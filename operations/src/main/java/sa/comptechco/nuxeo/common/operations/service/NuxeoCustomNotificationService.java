package sa.comptechco.nuxeo.common.operations.service;

import org.nuxeo.ecm.core.api.DocumentModel;
import sa.comptechco.nuxeo.common.operations.dto.NotifyDTO;

import java.io.IOException;
import java.util.List;

public interface NuxeoCustomNotificationService {

    public Object callNotification(DocumentModel doc, String applicationKey, String notificationKey, String channel, String data, List<String> groups, List<String> users, String sourceTypee, String customMetaData, String companyName) throws  RuntimeException;

    }
