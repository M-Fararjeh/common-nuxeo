package org.nuxeo.extended;

import org.nuxeo.ecm.platform.task.Task;

import java.io.Serializable;
import java.util.List;


public interface TaskList extends List<Task>, Serializable {

    long totalSize();

}
