package sa.comptechco.nuxeo.common.services;

import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;
import org.nuxeo.common.Environment;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentName;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.services.config.ConfigurationService;
import sa.comptechco.nuxeo.common.services.api.CustomRestrictionsService;
import sa.comptechco.nuxeo.common.services.model.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class CustomRestrictionsComponent extends DefaultComponent implements CustomRestrictionsService {

    public static final ComponentName NAME = new ComponentName("sa.meeting.services.api.CustomRestrictionsService");

    protected static final String SETTINGS_PROP_NAME = "sa.meeting.services.restrictions.settings.file";

    protected static final String DEFAULT_VALUE = "comptechco-custom-restrictions-settings.json";

    protected static final String ALL_KEYWORD = "all";

    protected String settingsFile;
    protected String settings;
    protected RestrictionsModel restrictionsModel;

    public static CustomRestrictionsComponent instance() {
        return (CustomRestrictionsComponent) Framework.getRuntime().getComponent(CustomRestrictionsComponent.NAME);
    }

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        settingsFile = Framework.getService(ConfigurationService.class).getString(SETTINGS_PROP_NAME, DEFAULT_VALUE);



        Gson gson = new Gson();
        restrictionsModel = new RestrictionsModel();
        try {
            settings = getSettings();
            restrictionsModel = gson.fromJson(settings, RestrictionsModel.class);
        } catch (Exception e) {
            // no restrictions will be used
        }
    }

    public String getSettings() {
        if (settingsFile != null) {
            return contentOfFile(settingsFile);
        } else if (settings != null && !settings.isEmpty()) {
            return settings;
        }
        return contentOfFile(DEFAULT_VALUE);
    }

    protected String contentOfFile(String filename) {
        try (InputStream stream = getResourceStream(filename)) {
            return IOUtils.toString(stream, "UTF-8");
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot load resource file: " + filename, e);
        }
    }

    @SuppressWarnings("resource") // closed by caller
    protected InputStream getResourceStream(String filename) {
        // First check if the resource is available on the config directory
        File file = new File(Environment.getDefault().getConfig(), filename);
        if (file.exists()) {
            try {
                return new FileInputStream(file);
            } catch (FileNotFoundException e) {
                // try another way
            }
        }

        // getResourceAsStream is needed getResource will not work when called from another module
        InputStream ret = this.getClass().getClassLoader().getResourceAsStream(filename);
        if (ret == null) {
            // Then try to get it from jar
            ret = this.getClass().getClassLoader().getResourceAsStream(filename);
        }
        if (ret == null) {
            throw new IllegalArgumentException(
                    String.format("Resource file cannot be found: %s or %s", file.getAbsolutePath(), filename));
        }
        return ret;
    }

    @Override
    public RestrictionsModel getRestrictionsModel() {
        return restrictionsModel;
    }

    @Override
    public Boolean checkPathOrTypeAllowed(String path, String docType, List<String> userGroups, ActionEnum action) {
        boolean isAllowed = true;
        int docTypeAllowed = -1, pathAllowed = -1;
        if (isEmptyRestrictions()) {
            return true;
        }

        if (docType != null && !docType.trim().isEmpty() && !docType.trim().isBlank() && !isEmptyDocTypeRestrictions()) {

            // check type restrictions
            List<TypeRestriction> relatedTypeRestrictions = getRelatedTypeRestrictions(docType);
            if (relatedTypeRestrictions != null && !relatedTypeRestrictions.isEmpty()) {
                for (TypeRestriction tr: relatedTypeRestrictions) {
                    int allowStatus = checkAllowStatus(tr, action, userGroups);
                    if (allowStatus > -1) {
                        docTypeAllowed = allowStatus;
                    }
                }
            }
        }
        // path restrictions can override type restrictions
        if (path != null && !path.trim().isEmpty() && !path.trim().isBlank() && !isEmptyPathRestrictions()) {
            // check path restrictions
            List<PathRestriction> relatedPathRestrictions = getRelatedPathRestrictions(path);

            if (relatedPathRestrictions != null && !relatedPathRestrictions.isEmpty()) {
                for (PathRestriction pr : relatedPathRestrictions) {
                    int allowStatus = checkAllowStatus(pr, action, userGroups);
                    if (allowStatus > -1) {
                        pathAllowed = allowStatus;
                    }

                }
            }
        }
        if (pathAllowed > 0) {
            return true;
        } else if (pathAllowed == 0) {
            return false;
        } else if (docTypeAllowed > 0) {
            return true;
        } else return docTypeAllowed != 0;
    }

    private int checkAllowStatus(AbstractRestriction restriction, ActionEnum action, List<String> userGroups) {
        if (restriction == null || restriction.getActions() == null
                || restriction.getActions().isEmpty() || action == null || restriction.getGroups() == null
        || restriction.getGroups().isEmpty() || restriction.getMode() == null) {
            return -1;
        }
        boolean applicableForAction = containsAction(restriction.getActions(), action);
        boolean applicableForUser = containsOneGroup(restriction.getGroups(), userGroups);
        if (restriction.getMode() != null && restriction.getMode().getValue().equalsIgnoreCase(ModeEnum.deny.getValue())) {
            if (applicableForAction && applicableForUser) {
                return 0;
            } else {
                return 1;
            }
        } else if(restriction.getMode() != null && restriction.getMode().getValue().equalsIgnoreCase(ModeEnum.allow.getValue())) {
            if (applicableForAction && applicableForUser) {
                return 1;
            } else {
                return 0;
            }
        }
        return -1;
    }
    private boolean containsOneGroup(List<String> groups, List<String> userGroups) {
        for (String group : groups) {
            for (String userGroup : userGroups) {
                if (group != null && group.equalsIgnoreCase(userGroup)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsAction(List<ActionEnum> actions, ActionEnum action) {
        for (ActionEnum a : actions) {
            if (a != null && action != null && (a.compareTo(ActionEnum.all) == 0 || a.compareTo(action) == 0)) {
                return true;
            }
        }
        return false;
    }

    protected Boolean isEmptyPathRestrictions() {
        if (getRestrictionsModel() == null) {
            return true;
        }
        return getRestrictionsModel().getPathRestrictions() == null || getRestrictionsModel().getPathRestrictions().isEmpty();
    }
    protected Boolean isEmptyDocTypeRestrictions() {
        if (getRestrictionsModel() == null) {
            return true;
        }
        return getRestrictionsModel().getTypeRestrictions() == null || getRestrictionsModel().getTypeRestrictions().isEmpty();
    }
    protected Boolean isEmptyRestrictions() {
        return isEmptyPathRestrictions() && isEmptyDocTypeRestrictions();
    }

    protected List<PathRestriction> getRelatedPathRestrictions(String path) {
        if (isEmptyPathRestrictions()) {
            return new ArrayList<>();
        }
        List<PathRestriction> result = new ArrayList<>();
        for (PathRestriction pr : getRestrictionsModel().getPathRestrictions()) {
            if (pr.getPath().trim().compareTo(path.trim()) == 0) {
                result.add(pr);
            }
        }
        return result;
    }

    protected List<TypeRestriction> getRelatedTypeRestrictions(String docType) {
        if (isEmptyDocTypeRestrictions()) {
            return new ArrayList<>();
        }
        List<TypeRestriction> result = new ArrayList<>();
        for (TypeRestriction tr : getRestrictionsModel().getTypeRestrictions()) {
            if (tr.getDocType().trim().compareTo(docType.trim()) == 0) {
                result.add(tr);
            }
        }
        return result;
    }

}
