package org.nuxeo.extended;

import org.nuxeo.ecm.automation.core.util.Paginable;
import org.nuxeo.ecm.platform.task.Task;

import java.io.Serializable;
import java.util.List;

public  interface PaginableTaskList extends  TaskList, Paginable<Task> , Serializable {

    public static final String CODEC_PARAMETER_NAME = "URLCodecName";

    String getDocumentLinkBuilder();


}
