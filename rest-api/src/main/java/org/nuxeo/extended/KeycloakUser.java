package org.nuxeo.extended;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.List;

public class KeycloakUser  implements Serializable {

    private String username;
    private String firstName;
    private String lastName;
    private Boolean enabled;
    private String email;


    private List<KeycloakUserCredentials> credentials;


    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<KeycloakUserCredentials> getCredentials() {
        return credentials;
    }

    public void setCredentials(List<KeycloakUserCredentials> credentials) {
        this.credentials = credentials;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}

