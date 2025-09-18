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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.utils.RFC2231;
import org.nuxeo.ecm.blob.s3.S3BlobStore;
import org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.SystemPrincipal;
import org.nuxeo.ecm.core.blob.BlobContext;
import org.nuxeo.ecm.core.blob.BlobUpdateContext;
import org.nuxeo.ecm.core.blob.KeyStrategy;
import org.nuxeo.ecm.core.io.download.DownloadHelper;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Custom Blob storage in S3.
 *
 * @since 11.1
 */
public class S3CustomBlobStore extends S3BlobStore {

    private static final Logger log = LogManager.getLogger(S3BlobStore.class);


    public S3CustomBlobStore(String name, S3BlobStoreConfiguration config, KeyStrategy keyStrategy) {
        super(name, config, keyStrategy);
    }


    public S3CustomBlobStore getS3BinaryManager() {
        return S3CustomBlobStore.this;
    }




    protected void setMetadata(ObjectMetadata objectMetadata, BlobContext blobContext) {
        try {
            if (blobContext != null) {
                Blob blob = blobContext.blob;
                String filename = blob.getFilename();
                if (filename != null) {
                    String contentDisposition = RFC2231.encodeContentDisposition(filename, false, null);
                    objectMetadata.setContentDisposition(contentDisposition);
                }
                String contentType = DownloadHelper.getContentTypeHeader(blob);

                if (contentType == null || contentType.equals("???") || Pattern.compile("^.*[~#@*+%{}<>\\[\\]|\"\\_].*$").matcher(contentType).matches()) {
                    objectMetadata.setContentType(MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE);
                } else {
                    objectMetadata.setContentType(contentType);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (config.metadataAddUsername) {
            NuxeoPrincipal principal = NuxeoPrincipal.getCurrent();
            if (principal != null && !(principal instanceof SystemPrincipal)) {
                String username = principal.getActingUser();
                if (username != null) {
                    Map<String, String> userMetadata = Collections.singletonMap(USER_METADATA_USERNAME, username);
                    objectMetadata.setUserMetadata(userMetadata);
                }
            }
        }
    }
    public void writeBlobProperties(BlobUpdateContext blobUpdateContext) throws IOException {
        String key = blobUpdateContext.key;
        String objectKey;
        String versionId;
        int seppos = key.indexOf("@");
        if (seppos < 0) {
            objectKey = key;
            versionId = null;
        } else {
            objectKey = key.substring(0, seppos);
            versionId = key.substring(seppos + 1);
        }
        String bucketKey = bucketPrefix + objectKey;
        // do nothing on s3 retention
    }


}
