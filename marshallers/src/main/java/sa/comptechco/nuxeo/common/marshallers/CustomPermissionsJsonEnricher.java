/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nicolas Chapurlat <nchapurlat@nuxeo.com>
 */

package sa.comptechco.nuxeo.common.marshallers;

import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.io.marshallers.json.enrichers.AbstractJsonEnricher;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext.SessionWrapper;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;

@Setup(mode = SINGLETON, priority = REFERENCE)
public class CustomPermissionsJsonEnricher extends AbstractJsonEnricher<DocumentModel> {

    public static final String NAME = "perm";
    private static final String PERMISSIONS = "org.nuxeo.security.custom.permissions";

    public CustomPermissionsJsonEnricher() {
        super(NAME);
    }

    @Override
    public void write(JsonGenerator jg, DocumentModel document) throws IOException {
        try (SessionWrapper wrapper = ctx.getSession(document)) {
            if (!wrapper.getSession().exists(document.getRef())) {
                return;
            }
            jg.writeArrayFieldStart(NAME);
            for (String permission : getPermissionsInSession(document, wrapper.getSession())) {
                jg.writeString(permission);
            }
            jg.writeEndArray();
        }
    }

    private Collection<String> getPermissionsInSession(DocumentModel doc, CoreSession session) {
        //PermissionProvider permissionProvider = Framework.getService(PermissionProvider.class);
        // Convert into real list to avoid UnsupportedOperationException if permissions are added or removed


        String permissionString = ctx.getParameter("permissions-list");
        if(StringUtils.isEmpty(permissionString)) {
            permissionString = Framework.getProperty(PERMISSIONS, "");
        }

        if(StringUtils.isEmpty(permissionString))
        {
            permissionString="Read,Write,Everything";
        }
        List<String> permissions = Stream.of(permissionString.trim().split("\\s*,\\s*"))
                .collect(Collectors.toList());
       // List<String> permissions = new ArrayList<String>(Arrays.asList(permissionProvider.getPermissions()));
        return session.filterGrantedPermissions(session.getPrincipal(), doc.getRef(), permissions);
    }

}
