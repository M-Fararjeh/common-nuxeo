/*
 * (C) Copyright 2006-2008 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package sa.comptechco.nuxeo.common.operations.importer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.javasimon.Stopwatch;
import org.nuxeo.common.utils.ExceptionUtils;
import org.nuxeo.common.utils.IdUtils;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolderWithProperties;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.core.api.pathsegment.PathSegmentService;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventProducer;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.importer.base.GenericMultiThreadedImporter;
import org.nuxeo.ecm.platform.importer.base.GenericThreadedImportTask;
import org.nuxeo.ecm.platform.importer.factories.AbstractDocumentModelFactory;
import org.nuxeo.ecm.platform.importer.factories.DefaultDocumentModelFactory;
import org.nuxeo.ecm.platform.importer.factories.ImporterDocumentModelFactory;
import org.nuxeo.ecm.platform.importer.log.ImporterLogger;
import org.nuxeo.ecm.platform.importer.source.SourceNode;
import org.nuxeo.ecm.platform.importer.threading.ImporterThreadingPolicy;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Custom Generic importer task
 *
 * @author Rasha Bahbouh
 */
public class CustomGenericThreadedImportTask extends GenericThreadedImportTask {


    private static final Log log = LogFactory.getLog(CustomGenericThreadedImportTask.class);

    protected final CustomGenericThreadedImportTask.FilenameNormalizer filenameNormalizer = "true".equals(Framework.getProperty("nuxeo.importer.compatFilenames")) ? new CustomGenericThreadedImportTask.CompatFilenameNormalizer() : new CustomGenericThreadedImportTask.DefaultFilenameNormalizer();

    public static final String EXTENSIONS = "org.nuxeo.importer.custom.extensions";
    public static Boolean SKIP_EXTENSION=true;
    public static Boolean ENABLE_SKIP_EXTENSION=false;
    public static Boolean SKIP_FOLDERS=true;
    public static Boolean ENABLE_SKIP_FOLDERS=true;
    public static Boolean SKIP_PATHS=true;
    public static Boolean ENABLE_SKIP_PATHS=true;
    public static Boolean ENABLE_RESUME_MODE=false;
    public static Boolean SKIP_EMPTY_FILES=false;

    public static final String EXTENSION_SKIP = "org.nuxeo.importer.custom.extensions.skip";
    public static final String EXTENSION_SKIP_ENABLE = "org.nuxeo.importer.custom.extensions.skip.enable";
    public static final String HASHED_FOLDERS = "org.nuxeo.importer.custom.hashed.folders";
    public static final String FOLDERS = "org.nuxeo.importer.custom.folders";
    public static final String PATHS = "org.nuxeo.importer.custom.paths";
    public static final String PATHS_SKIP = "org.nuxeo.importer.custom.paths.skip";
    public static final String PATHS_SKIP_ENABLE = "org.nuxeo.importer.custom.paths.skip.enable";
    public static final String FOLDERS_SKIP = "org.nuxeo.importer.custom.folders.skip";
    public static final String FOLDERS_SKIP_ENABLE = "org.nuxeo.importer.custom.folders.skip.enable";
    public static final String RESUME_MODE_ENABLE = "org.nuxeo.importer.resume.enable";
    public static final String SKIP_EMPTY_FILES_ENABLE = "org.nuxeo.importer.skipemptyfiles.enable";

    private Set<String> skippedExt = null;
    private Set<String> skippedFolders = null;
    private Set<String> skippedHashedFolders = null;
    private Set<String> skippedPaths = null;
    private HashMap<String, Boolean> skippedFoldersHM = null;
    private HashMap<String, Boolean> skippedExtHM = null;
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private static synchronized int getNextTaskId() {
        taskCounter += 1;
        return taskCounter;
    }

    protected CustomGenericThreadedImportTask(CoreSession session) {
        super(session);
    }

    protected CustomGenericThreadedImportTask(CoreSession session, SourceNode rootSource, DocumentModel rootDoc, boolean skipContainerCreation, ImporterLogger rsLogger, int batchSize, ImporterDocumentModelFactory factory, ImporterThreadingPolicy threadPolicy) {
        super(session, rootSource, rootDoc, skipContainerCreation, rsLogger, batchSize, factory, threadPolicy);
    }

    public CustomGenericThreadedImportTask(String repositoryName, SourceNode rootSource, DocumentModel rootDoc, boolean skipContainerCreation, ImporterLogger rsLogger, int batchSize, ImporterDocumentModelFactory factory, ImporterThreadingPolicy threadPolicy, String jobName) {
        super(repositoryName, rootSource, rootDoc, skipContainerCreation, rsLogger, batchSize, factory, threadPolicy, jobName);
    }

    protected CustomGenericThreadedImportTask createNewTask(DocumentModel parent, SourceNode node, ImporterLogger log,
                                                      Integer batchSize) {
        CustomGenericThreadedImportTask newTask = new CustomGenericThreadedImportTask(repositoryName, node, parent,
                skipContainerCreation, log, batchSize, factory, threadPolicy, null);
        newTask.addListeners(listeners);
        newTask.addImportingDocumentFilters(importingDocumentFilters);
        return newTask;
    }

    @Override
    protected void setFactory(ImporterDocumentModelFactory factory) {
        super.setFactory(factory);
    }

    @Override
    protected void setRsLogger(ImporterLogger rsLogger) {
        super.setRsLogger(rsLogger);
    }

    @Override
    protected void setThreadPolicy(ImporterThreadingPolicy threadPolicy) {
        super.setThreadPolicy(threadPolicy);
    }

    @Override
    protected void setJobName(String jobName) {
        super.setJobName(jobName);
    }


    @Override
    public synchronized void run() {
        String extensions = Framework.getProperty(EXTENSIONS, "");
        SKIP_EXTENSION = Boolean.valueOf(Framework.getProperty(EXTENSION_SKIP, "true"));
        ENABLE_SKIP_EXTENSION = Boolean.valueOf(Framework.getProperty(EXTENSION_SKIP_ENABLE, "false"));
        String folders = Framework.getProperty(FOLDERS, "");
        String hashFolders = Framework.getProperty(HASHED_FOLDERS, "");
        SKIP_FOLDERS=Boolean.valueOf(Framework.getProperty(FOLDERS_SKIP, "true"));
        ENABLE_SKIP_FOLDERS=Boolean.valueOf(Framework.getProperty(FOLDERS_SKIP_ENABLE, "false"));
        SKIP_PATHS=Boolean.valueOf(Framework.getProperty(PATHS_SKIP, "true"));
        ENABLE_SKIP_PATHS=Boolean.valueOf(Framework.getProperty(PATHS_SKIP_ENABLE, "false"));
        ENABLE_RESUME_MODE=Boolean.valueOf(Framework.getProperty(RESUME_MODE_ENABLE, "false"));
        SKIP_EMPTY_FILES=Boolean.valueOf(Framework.getProperty(SKIP_EMPTY_FILES_ENABLE, "false"));
        String paths = Framework.getProperty(PATHS, "");


        skippedExt = Stream.of(extensions.trim().split("\\s*,\\s*"))
                .collect(Collectors.toSet());

        skippedFolders = Stream.of(folders.trim().split("\\s*,\\s*"))
                .collect(Collectors.toSet());
        skippedPaths = Stream.of(paths.trim().split("\\s*,\\s*"))
                .collect(Collectors.toSet());
        skippedHashedFolders = Arrays.stream(hashFolders.trim().split("\\s*,\\s*")).map("#"::concat).collect(Collectors.toSet());
//        skippedHashedFolders = Stream.of("#".concat(hashFolders).trim().split("\\s*,\\s*"))
//                .collect(Collectors.toSet());

        skippedFoldersHM = new HashMap<>();
        skippedExtHM = new HashMap<>();
        skippedExt.forEach(ext -> {
            skippedExtHM.put(ext.toLowerCase(), Boolean.TRUE);
        });
        skippedFolders.forEach(f -> {
            skippedFoldersHM.put(f, Boolean.TRUE);
        });
        skippedHashedFolders.forEach(hf -> {
            skippedFoldersHM.put(hf, Boolean.TRUE);
        });
        synchronized (this) {
            if (isRunning) {
                throw new IllegalStateException("Task already running");
            }
            isRunning = true;
            // versions have no path, target document can be null
            if (rootSource == null) {
                isRunning = false;
                throw new IllegalArgumentException("source node must be specified");
            }
        }
        TransactionHelper.startTransaction(transactionTimeout);
        boolean completedAbruptly = true;
        try {
            session = CoreInstance.getCoreSessionSystem(repositoryName);
            log.info("Starting new custom import task");
            System.out.println("Starting new custom import task ");
//            Framework.doPrivileged(() -> {
                if (rootDoc != null) {
                    // reopen the root to be sure the session is valid
                    rootDoc = session.getDocument(rootDoc.getRef());
                }
                try {
                    recursiveCreateDocumentFromNode(rootDoc, rootSource);
                } catch (Exception e) {
                    log.error("Error during import", e);
                    System.err.println("Error during import" + e.getMessage());
                    e.printStackTrace();
                    ExceptionUtils.checkInterrupt(e);
                    notifyImportError();
                    // throw new NuxeoException(e); continue
                }
                session.save();
//            });
            CustomGenericMultiThreadedImporter.addCreatedDoc(taskId, uploadedFiles);
            completedAbruptly = false;
        } catch (Exception e) { // deals with interrupt below
            log.error("Error during import", e);
            ExceptionUtils.checkInterrupt(e);
            notifyImportError();
        } finally {
            log.info("End of task");
            if (completedAbruptly) {
                TransactionHelper.setTransactionRollbackOnly();
            }
            TransactionHelper.commitOrRollbackTransaction();
            synchronized (this) {
                isRunning = false;
            }
        }
    }


    protected CustomGenericThreadedImportTask createNewTaskIfNeeded(DocumentModel parent, SourceNode node) {
        if (isRootTask) {
            isRootTask = false; // don't fork Root thread on first folder
            return null;
        }
        int scheduledTasks = GenericMultiThreadedImporter.getExecutor().getQueue().size();
        boolean createTask = getThreadPolicy().needToCreateThreadAfterNewFolderishNode(parent, node, uploadedFiles,
                batchSize, scheduledTasks);

        if (createTask) {
            CustomGenericThreadedImportTask newTask = (CustomGenericThreadedImportTask) createNewTask(parent, node, rsLogger, batchSize);
            newTask.setBatchSize(getBatchSize());
            newTask.setSkipContainerCreation(true);
            newTask.setTransactionTimeout(transactionTimeout);
            return newTask;
        } else {
            return null;
        }
    }

    protected void recursiveCreateDocumentFromNode(DocumentModel parent, SourceNode node) throws IOException {

        if (getFactory().isTargetDocumentModelFolderish(node)) {
            DocumentModel folder = null;
            Boolean newThread = false;
            if (skipContainerCreation) {
                folder = parent;
                skipContainerCreation = false;
                newThread = true;
            } else {
                // Try to create folder for 3 times
                int i = 0;
                while (i < 4) {
                    i++;
                    try {
                        DocumentRef documentRef = new PathRef(parent.getPathAsString() + "/" + getValidNameFromFileName(node.getName()));
                        if (ENABLE_RESUME_MODE && session.exists(documentRef)) {
                            folder = doGetAlreadyCreatedFolderishNode(documentRef, parent, node);
                        } else {
                            folder = doCreateFolderishNode(parent, node);
                        }
                        if (folder != null) {
                            break;
                        } else {
                            TransactionHelper.setTransactionRollbackOnly();
                            TransactionHelper.commitOrRollbackTransaction();
                            TransactionHelper.startTransaction(Integer.MAX_VALUE - 1);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println(sdf.format(new Date()) + " : " + this.getClass().getName() + " : Error while creating folder TRIAL::" + i);
                        try {
                            if (i >= 2) {
                                try {
                                    TransactionHelper.setTransactionRollbackOnly();
                                    TransactionHelper.commitOrRollbackTransaction();
                                    TransactionHelper.startTransaction(Integer.MAX_VALUE - 1);
                                } catch (Exception e2) {

                                    try {
                                        TransactionHelper.setTransactionRollbackOnly();
                                        TransactionHelper.commitOrRollbackTransaction();
                                        TransactionHelper.startTransaction(Integer.MAX_VALUE - 1);
                                    } catch (Exception e3) {
                                        e3.printStackTrace();
                                    }
                                }

                            } else {
                                commitWithoutFiles(true);
                            }
                        } catch (Exception e1) {
                            System.out.println(sdf.format(new Date()) + " : " + this.getClass().getName() + " : Error while committing session for folder creation TRIAL::" + i);
                            System.out.println(sdf.format(new Date()) + " : " + this.getClass().getName() + String.format("Parent : [ %s ], Document source path [ %s ], Document Name: [ %s ]", parent.getPath(), node.getSourcePath(), node.getName()) );

                            e.printStackTrace();

                        }
                    }
                }
                if (folder == null) {
                    return;
                }
            }

            // get a new TaskImporter if available to start
            // processing the sub-tree
            CustomGenericThreadedImportTask task = null;
            if (!newThread) {
                task = createNewTaskIfNeeded(folder, node);
            }
            if (task != null) {
                // force comit before starting new thread
                try {
                    commit(true);
                } catch (Exception e1) {
                    System.out.println(sdf.format(new Date()) + " : " + this.getClass().getName() + " : Error while force comit before starting new thread");
                    e1.printStackTrace();
                }
                try {
                    GenericMultiThreadedImporter.getExecutor().execute(task);
                } catch (RejectedExecutionException e) {
                    log.error("Import task rejected", e);
                }

            } else {
                Stopwatch stopwatch = SimonManager.getStopwatch("org.nuxeo.ecm.platform.importer.node_get_children");
                Split split = stopwatch.start();
                List<SourceNode> nodes = node.getChildren();
                split.stop();
                /*String skipped = Framework.getProperty(SKIPPED_EXTENSIONS, "");
                Set<String> set = Stream.of(skipped.trim().split("\\s*,\\s*"))
                        .collect(Collectors.toSet());
*/

                if (nodes != null) {
                    for (SourceNode child : nodes) {
                        Boolean skip = false;
                        if(ENABLE_SKIP_FOLDERS && getFactory().isTargetDocumentModelFolderish(child)) { //directory
                            if ((SKIP_FOLDERS && skippedFoldersHM.containsKey(child.getName()))
                                    || (!SKIP_FOLDERS && !skippedFoldersHM.containsKey(child.getName())) ) {
                                skip = true;
                            }
/*
                            if (SKIP_FOLDERS && skippedFolders.contains(child.getName())) {
                                skip = true;
                            }

                            if (!SKIP_FOLDERS && !skippedFolders.contains(child.getName())) {
                                skip = true;
                            }*/
                        } else if (ENABLE_SKIP_EXTENSION) { //file

                            int extensionStartIndx = child.getName().lastIndexOf('.');
                            if (extensionStartIndx > -1 && extensionStartIndx < child.getName().length() - 1) {
                                String extension = child.getName().substring(extensionStartIndx + 1).toLowerCase();
                                // skip extensions

                                if ((SKIP_EXTENSION && skippedExtHM.containsKey(extension))
                                        || (!SKIP_EXTENSION && !skippedExtHM.containsKey(extension))) {
                                    skip = true;
                                }
                            } else {
                                // File has no extension ---> skip
                                skip = true;
                            }
                        }
                        if (!skip && ENABLE_SKIP_PATHS) {
                            String sourcePath = child.getSourcePath();
//                            System.out.println("Source Path = " + sourcePath);
                            if (SKIP_PATHS) {
                                for (String path : skippedPaths) {
                                    boolean startWithPath = sourcePath.startsWith(path) || sourcePath.compareTo(path) == 0;
//                                    System.out.printf("Comparing Source Path [ %s ] with path [ %s ] %n", sourcePath, path);
                                    if (startWithPath) {
                                        skip = true;
                                        break;
                                    }
                                }
                            } else {
                                boolean matchFounded = false;
                                for (String path : skippedPaths) {
                                    boolean startWithPath = sourcePath.startsWith(path) || sourcePath.compareTo(path) == 0;
//                                    System.out.printf("Comparing Source Path [ %s ] with path [ %s ] %n", sourcePath, path);
                                    if (startWithPath) {
                                        matchFounded = true;
                                        break;
                                    }
                                }
                                if (!matchFounded) {
                                    skip = true;
                                }
                            }

                        }
                        if (!skip) {
                            try {
                                recursiveCreateDocumentFromNode(folder, child);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }


                        }
                    }
                }
            }
        } else {
            DocumentModel doc;
            // Try to create folder for 3 times
            int i = 0;
            while (i < 4) {
                i++;
                try {
                    DocumentRef documentRef = new PathRef(parent.getPathAsString() + "/" + getValidNameFromFileName(node.getName()));
                    if (ENABLE_RESUME_MODE && session.exists(documentRef)) {
                        doc = doGetAlreadyCreatedLeafNode(documentRef, parent, node);
                    } else {
                        if (SKIP_EMPTY_FILES && (node.getBlobHolder() == null || node.getBlobHolder().getBlob() == null || node.getBlobHolder().getBlob().getLength() == 0)) {
                            System.out.printf("Empty file skipped [ %s ]%n", node.getSourcePath());
                            break;
                        } else {
                            doc = doCreateLeafNode(parent, node);
                        }
                    }

                    if (doc != null) {
                        break;
                    } else {
                        TransactionHelper.setTransactionRollbackOnly();
                        TransactionHelper.commitOrRollbackTransaction();
                        TransactionHelper.startTransaction(Integer.MAX_VALUE - 1);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println(sdf.format(new Date()) + " : " + this.getClass().getName() + " : Error while creating Leaf Document TRIAL::" + i);
                    System.out.println(sdf.format(new Date()) + " : " + this.getClass().getName() + String.format("Parent : [ %s ], Document source path [ %s ], Document Name: [ %s ]", parent.getPath(), node.getSourcePath(), node.getName()) );
                    try {
                        if (i <= 2) {
                            try {
                                TransactionHelper.setTransactionRollbackOnly();
                                TransactionHelper.commitOrRollbackTransaction();
                                TransactionHelper.startTransaction(Integer.MAX_VALUE - 1);
                            } catch (Exception e2) {

                                try {
                                    TransactionHelper.setTransactionRollbackOnly();
                                    TransactionHelper.commitOrRollbackTransaction();
                                    TransactionHelper.startTransaction(Integer.MAX_VALUE - 1);
                                } catch (Exception e3) {
                                    e3.printStackTrace();
                                }
                            }

                        } else {
                            commitWithoutFiles(true);
                        }

                    } catch (Exception e1) {
                        System.out.println(sdf.format(new Date()) + " : " + this.getClass().getName() + " : Error while committing session for Leaf Document TRIAL::" + i );
                        System.out.println(sdf.format(new Date()) + " : " + this.getClass().getName() + String.format("Parent : [ %s ], Document source path [ %s ], Document Name: [ %s ]", parent.getPath(), node.getSourcePath(), node.getName()) );

                        e.printStackTrace();
                    }
                }
            }
        }
    }

    protected void commitWithoutFiles(boolean force) {
//        this.uploadedFiles++;
        if (this.uploadedFiles % 10L == 0L) {
            GenericMultiThreadedImporter.addCreatedDoc(this.taskId, this.uploadedFiles);
        }

        if (this.uploadedFiles % this.batchSize == 0L || force) {
            Stopwatch stopwatch = SimonManager.getStopwatch("org.nuxeo.ecm.platform.importer.session_save");
            Split split = stopwatch.start();
            fslog("Committing Core Session after " + this.uploadedFiles + " files", true);
            this.session.save();
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction(this.transactionTimeout);
            split.stop();
        }
    }

    protected String getValidNameFromFileName(String fileName) {
        return this.filenameNormalizer.normalize(fileName);
    }

    protected static class DefaultFilenameNormalizer implements CustomGenericThreadedImportTask.FilenameNormalizer {
        protected DefaultFilenameNormalizer() {
        }

        public String normalize(String name) {
            DocumentModel fake = new DocumentModelImpl("/", name, "File");
            return ((PathSegmentService)Framework.getService(PathSegmentService.class)).generatePathSegment(fake);
        }
    }

    protected static class CompatFilenameNormalizer implements CustomGenericThreadedImportTask.FilenameNormalizer {
        protected CompatFilenameNormalizer() {
        }

        public String normalize(String name) {
            name = IdUtils.generateId(name, "-", true, 100);
            name = name.replace("'", "");
            name = name.replace("(", "");
            name = name.replace(")", "");
            name = name.replace("+", "");
            return name;
        }
    }

    protected interface FilenameNormalizer {
        String normalize(String var1);
    }

    protected DocumentModel doGetAlreadyCreatedFolderishNode(DocumentRef documentRef, DocumentModel parent, SourceNode node) {
        if (documentRef == null) {
            return null;
        }
        Stopwatch stopwatch = SimonManager.getStopwatch("org.nuxeo.ecm.platform.importer.create_folder");
        Split split = stopwatch.start();
        DocumentModel folder = null;
        try {
            folder = session.getDocument(documentRef);
        } catch (Exception e) {

            String errorMsg = "Unable to get already created folderish document for " + node.getSourcePath() + ":" + e + ((e.getCause() != null) ? e.getCause().toString() : "");
            fslog(errorMsg, true);
            log.error(errorMsg);


            boolean shouldImportTaskContinue = getFactory().processFolderishNodeCreationError(this.session, parent, node);
            if (!shouldImportTaskContinue) {
                throw new NuxeoException(e);
            }
        } finally {
            split.stop();
        }
        if (folder != null) {
            String parentPath = (parent == null) ? "null" : parent.getPathAsString();
            fslog("Already Created Folder " + folder.getName() + " at " + parentPath, true);


            commit();
        }
        return folder;
    }

    protected DocumentModel doGetAlreadyCreatedLeafNode(DocumentRef documentRef, DocumentModel parent, SourceNode node) throws IOException {
        if (!shouldImportDocument(node)) {
            return null;
        }
        Stopwatch stopwatch = SimonManager.getStopwatch("org.nuxeo.ecm.platform.importer.create_leaf");
        Split split = stopwatch.start();
        DocumentModel leaf = null;
        try {
            leaf = session.getDocument(documentRef);
        } catch (Exception e) {

            String errMsg = "Unable to fetch already created leaf document for " + node.getSourcePath() + ":" + e + ((e.getCause() != null) ? (String)e.getCause().toString() : "");
            fslog(errMsg, true);
            log.error(errMsg);


            boolean shouldImportTaskContinue = getFactory().processLeafNodeCreationError(this.session, parent, node);
            if (!shouldImportTaskContinue) {
                throw new NuxeoException(e);
            }
        } finally {
            split.stop();
        }
        BlobHolder bh = node.getBlobHolder();
        if (leaf != null && bh != null) {
            Blob blob = bh.getBlob();
            if (blob != null) {
                long fileSize = blob.getLength();
                String fileName = blob.getFilename();
                if (fileSize > 0L) {
                    long kbSize = fileSize / 1024L;
                    String parentPath = (parent == null) ? "null" : parent.getPathAsString();
                    fslog("Already Created doc " + leaf.getName() + " at " + parentPath + " with file " + fileName + " of size " + kbSize + "KB", true);
                }

                this.uploadedKO += fileSize;
            }

            EventProducer eventProducer = (EventProducer)Framework.getService(EventProducer.class);
            DocumentEventContext documentEventContext = new DocumentEventContext(this.session, this.session.getPrincipal(), leaf);
            Event event = documentEventContext.newEvent("documentImportedWithPlatformImporter");
            eventProducer.fireEvent(event);


            commit();
        }
        return leaf;
    }
}
