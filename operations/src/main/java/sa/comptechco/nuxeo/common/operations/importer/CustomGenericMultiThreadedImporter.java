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

import net.jodah.expiringmap.internal.NamedThreadFactory;
import org.javasimon.SimonManager;
import org.javasimon.Stopwatch;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.importer.base.GenericMultiThreadedImporter;
import org.nuxeo.ecm.platform.importer.base.GenericThreadedImportTask;
import org.nuxeo.ecm.platform.importer.base.ImporterRunnerConfiguration;
import org.nuxeo.ecm.platform.importer.log.ImporterLogger;
import org.nuxeo.ecm.platform.importer.log.PerfLogger;
import org.nuxeo.ecm.platform.importer.source.SourceNode;

import java.io.IOException;
import java.util.concurrent.*;

/**
 * Custom Generic importer
 *
 * @author Rasha Bahbouh
 */
public class CustomGenericMultiThreadedImporter extends GenericMultiThreadedImporter {


    protected CustomGenericThreadedImportTask rootImportTask;


    public CustomGenericMultiThreadedImporter(SourceNode sourceNode, String importWritePath, Boolean skipRootContainerCreation, Integer batchSize, Integer nbThreads, ImporterLogger log, int queueSize) {
        super(sourceNode, importWritePath, skipRootContainerCreation, batchSize, nbThreads, log, queueSize);
    }

    public CustomGenericMultiThreadedImporter(SourceNode sourceNode, String importWritePath, Boolean skipRootContainerCreation, Integer batchSize, Integer nbThreads, ImporterLogger log) {
        super(sourceNode, importWritePath, skipRootContainerCreation, batchSize, nbThreads, log);
    }

    public CustomGenericMultiThreadedImporter(SourceNode sourceNode, String importWritePath, Integer batchSize, Integer nbThreads, ImporterLogger log) {
        super(sourceNode, importWritePath, batchSize, nbThreads, log);
    }

    public CustomGenericMultiThreadedImporter(SourceNode sourceNode, String importWritePath, Boolean skipRootContainerCreation, Integer batchSize, Integer nbThreads, String jobName, ImporterLogger log) {
        super(sourceNode, importWritePath, skipRootContainerCreation, batchSize, nbThreads, jobName, log);
    }

    public CustomGenericMultiThreadedImporter(SourceNode sourceNode, String importWritePath, Integer batchSize, Integer nbThreads, String jobName, ImporterLogger log) {
        super(sourceNode, importWritePath, batchSize, nbThreads, jobName, log);
    }

    public CustomGenericMultiThreadedImporter(ImporterRunnerConfiguration configuration) {
        super(configuration);
    }

    public void setRootImportTask(CustomGenericThreadedImportTask rootImportTask) {
        this.rootImportTask = rootImportTask;
    }
    protected CustomGenericThreadedImportTask initRootTask(SourceNode importSource, DocumentModel targetContainer,
                                                     boolean skipRootContainerCreation, ImporterLogger log, Integer batchSize, String jobName) {

        if (rootImportTask == null) {
            setRootImportTask(new CustomGenericThreadedImportTask(repositoryName, importSource, targetContainer,
                    skipRootContainerCreation, log, batchSize, getFactory(), getThreadPolicy(), jobName));
        } else {
            rootImportTask.setInputSource(importSource);
            rootImportTask.setTargetFolder(targetContainer);
            rootImportTask.setSkipContainerCreation(skipRootContainerCreation);
            rootImportTask.setRsLogger(log);
            rootImportTask.setFactory(getFactory());
            rootImportTask.setThreadPolicy(getThreadPolicy());
            rootImportTask.setJobName(jobName);
            rootImportTask.setBatchSize(batchSize);
        }
        rootImportTask.addListeners(listeners);
        rootImportTask.addImportingDocumentFilters(importingDocumentFilters);
        rootImportTask.setTransactionTimeout(transactionTimeout);
        return rootImportTask;
    }

    protected void doRun() throws IOException {

        targetContainer = getTargetContainer();

        nbCreatedDocsByThreads = new ConcurrentHashMap<>();

        importTP = new ThreadPoolExecutor(nbThreads, nbThreads, 500L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(queueSize), new NamedThreadFactory("Nuxeo-Importer-"));

        initRootTask(importSource, targetContainer, skipRootContainerCreation, log, batchSize, jobName);

        rootImportTask.setRootTask();
        long t0 = System.currentTimeMillis();

        notifyBeforeImport();

        Future<?> result = importTP.submit(rootImportTask);
        sleep(200);
        int activeTasks = importTP.getActiveCount();
        int oldActiveTasks = 0;
        long lastLogProgressTime = System.currentTimeMillis();
        long lastCreatedDocCounter = 0;

        PerfLogger perfLogger = enablePerfLogging ? new PerfLogger(PERF_HEADERS) : null;
        while (activeTasks > 0) {
            sleep(500);
            activeTasks = importTP.getActiveCount();
            boolean logProgress = false;
            if (oldActiveTasks != activeTasks) {
                oldActiveTasks = activeTasks;
                log.debug("currently " + activeTasks + " active import Threads");
                logProgress = true;

            }
            long ti = System.currentTimeMillis();
            if (ti - lastLogProgressTime > 5000) {
                logProgress = true;
            }
            if (logProgress) {
                long inbCreatedDocs = getCreatedDocsCounter();
                long deltaT = ti - lastLogProgressTime;
                double averageSpeed = 1000 * ((float) (inbCreatedDocs) / (ti - t0));
                double imediateSpeed = averageSpeed;
                if (deltaT > 0) {
                    imediateSpeed = 1000 * ((float) (inbCreatedDocs - lastCreatedDocCounter) / (deltaT));
                }
                log.info(inbCreatedDocs + " docs created");
                log.info("average speed = " + averageSpeed + " docs/s");
                log.info("immediate speed = " + imediateSpeed + " docs/s");

                if (enablePerfLogging) {
                    Double[] perfData = { Double.valueOf(inbCreatedDocs), averageSpeed, imediateSpeed };
                    perfLogger.log(perfData);
                }

                lastLogProgressTime = ti;
                lastCreatedDocCounter = inbCreatedDocs;
            }
        }
        stopImportProcrocess();
        log.info("All Threads terminated");
        if (enablePerfLogging) {
            perfLogger.release();
        }
        notifyAfterImport();

        long t1 = System.currentTimeMillis();
        long nbCreatedDocs = getCreatedDocsCounter();
        log.info(nbCreatedDocs + " docs created");
        log.info(1000 * ((float) (nbCreatedDocs) / (t1 - t0)) + " docs/s");
        for (String k : nbCreatedDocsByThreads.keySet()) {
            log.info(k + " --> " + nbCreatedDocsByThreads.get(k));
        }
        Stopwatch stopwatch;
        for (String name : SimonManager.simonNames()) {
            if (name == null || name.isEmpty() || !name.startsWith("org.nuxeo.ecm.platform.importer")) {
                continue;
            }
            stopwatch = SimonManager.getStopwatch(name);
            if (stopwatch.getCounter() > 0) {
                log.info(stopwatch.toString());
            }
        }

    }

}
