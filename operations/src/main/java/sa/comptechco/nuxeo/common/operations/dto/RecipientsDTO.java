package  sa.comptechco.nuxeo.common.operations.dto;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RecipientsDTO implements Serializable {

    private List<String> userIds = new ArrayList<>();

    private List<String> roles = new ArrayList<>();

    private List<String> groups = new ArrayList<>();


    public List<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<String> userIds) {
        this.userIds = userIds;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    @Override
    public String toString() {
        return "RecipientsDTO{" +
                "userIds=" + Arrays.toString(userIds.toArray()) +
                ", roles='" + Arrays.toString(roles.toArray()) + '\'' +
                ", groups=" + Arrays.toString(groups.toArray()) +
                '}';
    }
}
