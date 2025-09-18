/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *    Mariana Cedica
 */
package sa.comptechco.nuxeo.common.operations.importer.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.importer.base.GenericMultiThreadedImporter;
import org.nuxeo.ecm.platform.importer.base.ImporterRunnerConfiguration;
import org.nuxeo.ecm.platform.importer.executor.AbstractImporterExecutor;
import org.nuxeo.ecm.platform.importer.executor.DefaultImporterExecutor;
import org.nuxeo.ecm.platform.importer.factories.DefaultDocumentModelFactory;
import org.nuxeo.ecm.platform.importer.factories.ImporterDocumentModelFactory;
import org.nuxeo.ecm.platform.importer.filter.EventServiceConfiguratorFilter;
import org.nuxeo.ecm.platform.importer.filter.ImporterFilter;
import org.nuxeo.ecm.platform.importer.log.ImporterLogger;
import org.nuxeo.ecm.platform.importer.service.DefaultImporterService;
import org.nuxeo.ecm.platform.importer.service.DefaultImporterServiceImpl;
import org.nuxeo.ecm.platform.importer.source.FileSourceNode;
import org.nuxeo.ecm.platform.importer.source.SourceNode;
import sa.comptechco.nuxeo.common.operations.importer.CustomGenericMultiThreadedImporter;

public class CustomDefaultImporterServiceImpl extends DefaultImporterServiceImpl {

    private String repositoryName;

    private int transactionTimeout = 0;

    private boolean bulkMode = true;

    private static Log log = LogFactory.getLog(CustomDefaultImporterServiceImpl.class);


    @Override
    public String importDocuments(AbstractImporterExecutor executor, String destinationPath, String sourcePath,
            boolean skipRootContainerCreation, int batchSize, int noImportingThreads, boolean interactive)
            {

        SourceNode sourceNode = createNewSourceNodeInstanceForSourcePath(sourcePath);
        if (sourceNode == null) {
            log.error("Need to set a sourceNode to be used by this importer");
            return "Can not import";
        }
        if (getDocumentModelFactory() == null) {
            log.error("Need to set a documentModelFactory to be used by this importer");
        }

        ImporterRunnerConfiguration configuration = new ImporterRunnerConfiguration.Builder(sourceNode,
                destinationPath, executor.getLogger()).skipRootContainerCreation(skipRootContainerCreation).batchSize(
                batchSize).nbThreads(noImportingThreads).repository(repositoryName).build();
        CustomGenericMultiThreadedImporter runner = new CustomGenericMultiThreadedImporter(configuration);
        runner.setEnablePerfLogging(enablePerfLogging);
        runner.setTransactionTimeout(transactionTimeout);
        ImporterFilter filter = new EventServiceConfiguratorFilter(false, false, false, false, bulkMode);
        runner.addFilter(filter);
        runner.setFactory(getDocumentModelFactory());
        return executor.run(runner, interactive);
    }

}
