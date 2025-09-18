/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package sa.comptechco.nuxeo.common.operations.blob;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.blob.s3.S3BlobProvider;
import org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration;
import org.nuxeo.ecm.core.blob.BlobStore;
import org.nuxeo.ecm.core.blob.CachingBlobStore;
import org.nuxeo.ecm.core.blob.KeyStrategy;
import org.nuxeo.ecm.core.blob.TransactionalBlobStore;

import java.io.IOException;
import java.util.Map;

/**
 * Blob provider that stores files in S3.
 * <p>
 * This blob provider supports transactional record mode.
 *
 * @since 11.1
 */
public class S3CustomBlobProvider extends S3BlobProvider {

    private static final Logger log = LogManager.getLogger(S3CustomBlobProvider.class);

    // public for tests
    //public S3BlobStoreConfiguration config;

    @Override
    protected BlobStore getBlobStore(String blobProviderId, Map<String, String> properties) throws IOException {
        super.config = getConfiguration(properties);
        log.info("Registering S3 blob provider '" + blobProviderId);
        KeyStrategy keyStrategy = getKeyStrategy();

        // main S3 blob store wrapped in a caching store
        BlobStore store = new S3CustomBlobStore("S3", config, keyStrategy);
        boolean caching = !config.getBooleanProperty("test-nocaching"); // for tests
        if (caching) {
            store = new CachingBlobStore("Cache", store, config.cachingConfiguration);
        }

        // maybe wrap into a transactional store
        if (isTransactional()) {
            BlobStore transientStore;
            if (store.hasVersioning()) {
                // if versioning is used, we don't need a separate transient store for transactions
                transientStore = store;
            } else {
                // transient store is another S3 blob store wrapped in a caching store
                S3BlobStoreConfiguration transientConfig = config.withNamespace("tx");
                transientStore = new S3CustomBlobStore("S3_tmp", transientConfig, keyStrategy);
                if (caching) {
                    transientStore = new CachingBlobStore("Cache_tmp", transientStore, config.cachingConfiguration);
                }
            }
            // transactional store
            store = new TransactionalBlobStore(store, transientStore);
        }
        return store;
    }

}
