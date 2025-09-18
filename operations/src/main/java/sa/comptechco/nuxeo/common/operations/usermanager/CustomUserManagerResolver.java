package sa.comptechco.nuxeo.common.operations.usermanager;

import org.apache.commons.collections.CollectionUtils;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.io.download.DownloadService;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.multi.tenant.MultiTenantService;
import org.nuxeo.ecm.platform.usermanager.UserManagerResolver;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

public class CustomUserManagerResolver extends UserManagerResolver{


    private boolean includingUsers = true;

    private boolean includingGroups = true;


    @Override
    public String getName() throws IllegalStateException {
        checkConfig();
        return CustomUserManagerResolver.NAME;
    }
    @Override
    public Object fetch(Object value) throws IllegalStateException {
        checkConfig();
        if (value instanceof String) {
            String name = (String) value;
            boolean userPrefix = name.startsWith(NuxeoPrincipal.PREFIX);
            boolean groupPrefix = name.startsWith(NuxeoGroup.PREFIX);
            if (includingUsers && !includingGroups) {
                if (userPrefix) {
                    name = name.substring(NuxeoPrincipal.PREFIX.length());
                }
                if (SecurityConstants.SYSTEM_USERNAME.equals(name)) {
                    return new SystemPrincipal(null);
                }
                MultiTenantService multiTenantService = Framework.getService(MultiTenantService.class);

                CoreSession coreSession = CoreInstance.getCoreSession("default");
                if (multiTenantService.isTenantIsolationEnabled(coreSession)) {

                    return CoreInstance.doPrivileged(coreSession, s -> {
                        if (userPrefix) {
                            String username = (String) value;
                            username = username.substring(NuxeoPrincipal.PREFIX.length());

                            return getUserManager().getPrincipal(username, false);
                        }
                        else {
                            return getUserManager().getPrincipal((String) value, false);
                        }

                    });

                }
                else {
                    return getUserManager().getPrincipal(name, false);
                }
            } else if (!includingUsers && includingGroups) {
                if (groupPrefix) {
                    name = name.substring(NuxeoGroup.PREFIX.length());
                }
                return getUserManager().getGroup(name);
            } else {
                if (userPrefix) {
                    name = name.substring(NuxeoPrincipal.PREFIX.length());
                    if (SecurityConstants.SYSTEM_USERNAME.equals(name)) {
                        return new SystemPrincipal(null);
                    }
                    MultiTenantService multiTenantService = Framework.getService(MultiTenantService.class);

                    CoreSession coreSession = CoreInstance.getCoreSession("default");
                    if (multiTenantService.isTenantIsolationEnabled(coreSession)) {

                        return CoreInstance.doPrivileged(coreSession, s -> {
                            if (userPrefix) {
                                String username = (String) value;
                                username = username.substring(NuxeoPrincipal.PREFIX.length());

                                return getUserManager().getPrincipal(username, false);
                            }
                            else {
                                return getUserManager().getPrincipal((String) value, false);
                            }

                        });

                    }
                    else {
                        return getUserManager().getPrincipal(name, false);
                    }
                } else if (groupPrefix) {
                    name = name.substring(NuxeoGroup.PREFIX.length());
                    return getUserManager().getGroup(name);
                } else {
                    if (SecurityConstants.SYSTEM_USERNAME.equals(name)) {
                        return new SystemPrincipal(null);
                    }
                    MultiTenantService multiTenantService = Framework.getService(MultiTenantService.class);

                    CoreSession coreSession = CoreInstance.getCoreSession("default");
                    if (multiTenantService.isTenantIsolationEnabled(coreSession)) {

                        return CoreInstance.doPrivileged(coreSession, s -> {
                            Principal principal=null;
                            if (userPrefix) {
                                String username = (String) value;
                                username = username.substring(NuxeoPrincipal.PREFIX.length());

                                principal= getUserManager().getPrincipal(username, false);
                                if (principal != null) {
                                    return principal;
                                } else {
                                    return getUserManager().getGroup(username);
                                }
                            }
                            else {
                                principal =getUserManager().getPrincipal((String) value, false);
                                if (principal != null) {
                                    return principal;
                                } else {
                                    return getUserManager().getGroup((String) value);
                                }
                            }


                        });

                    }
                    else {
                        NuxeoPrincipal principal = getUserManager().getPrincipal(name, false);
                        if (principal != null) {
                            return principal;
                        } else {
                            return getUserManager().getGroup(name);
                        }
                    }
                }
            }
        }
        return null;
    }
}
