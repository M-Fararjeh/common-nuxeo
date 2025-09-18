package org.nuxeo.extended;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.keycloak.json.StringListMapDeserializer;

import java.io.Serializable;
import java.util.*;

public class KeycloakUserInfo implements Serializable {

    private String id;
    private String firstName;
    private String lastName;
    private Boolean enabled;
    private String username;

    private Long createdTimestamp;
    @JsonIgnore
    private Boolean totp;

    private Boolean emailVerified;

    private String email;

    @JsonIgnore
    private Set<String> disableableCredentialTypes;

    @JsonIgnore
    private Integer notBefore;


    @JsonIgnore
    protected String self; // link
    @JsonIgnore
    protected String origin;

    @JsonIgnore
    protected String federationLink;

    @JsonIgnore
    protected String serviceAccountClientId; // For rep, it points to clientId (not DB ID)

    @JsonDeserialize(using = StringListMapDeserializer.class)
    protected Map<String, List<String>> attributes;

    @JsonIgnore
    protected ArrayList credentials;

    @JsonIgnore
    protected List<String> requiredActions;
    @JsonIgnore
    protected ArrayList federatedIdentities;

    @JsonIgnore
    protected List<String> realmRoles;

    protected Map<String, List<String>> clientRoles;

    @JsonIgnore
    protected ArrayList clientConsents;

    @JsonIgnore
    protected Map<String, List<String>> applicationRoles;

    @JsonIgnore
    protected ArrayList socialLinks;

    protected List<String> groups;

    @JsonIgnore
    private Map<String, Boolean> access;


    public Map<String, List<String>> getAttributes() {
        return attributes;
    }



    public String getSelf() {
        return self;
    }

    public void setSelf(String self) {
        this.self = self;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getFederationLink() {
        return federationLink;
    }

    public void setFederationLink(String federationLink) {
        this.federationLink = federationLink;
    }

    public String getServiceAccountClientId() {
        return serviceAccountClientId;
    }

    public void setServiceAccountClientId(String serviceAccountClientId) {
        this.serviceAccountClientId = serviceAccountClientId;
    }

    public void setAttributes(Map<String, List<String>> attributes) {
        this.attributes = attributes;
    }

    public ArrayList getCredentials() {
        return credentials;
    }

    public void setCredentials(ArrayList credentials) {
        this.credentials = credentials;
    }

    public void setRequiredActions(List<String> requiredActions) {
        this.requiredActions = requiredActions;
    }

    public ArrayList getFederatedIdentities() {
        return federatedIdentities;
    }

    public void setFederatedIdentities(ArrayList federatedIdentities) {
        this.federatedIdentities = federatedIdentities;
    }

    public List<String> getRealmRoles() {
        return realmRoles;
    }

    public void setRealmRoles(List<String> realmRoles) {
        this.realmRoles = realmRoles;
    }

    public Map<String, List<String>> getClientRoles() {
        return clientRoles;
    }

    public void setClientRoles(Map<String, List<String>> clientRoles) {
        this.clientRoles = clientRoles;
    }

    public ArrayList getClientConsents() {
        return clientConsents;
    }

    public void setClientConsents(ArrayList clientConsents) {
        this.clientConsents = clientConsents;
    }

    public Map<String, List<String>> getApplicationRoles() {
        return applicationRoles;
    }

    public void setApplicationRoles(Map<String, List<String>> applicationRoles) {
        this.applicationRoles = applicationRoles;
    }

    public ArrayList getSocialLinks() {
        return socialLinks;
    }

    public void setSocialLinks(ArrayList socialLinks) {
        this.socialLinks = socialLinks;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public void setAccess(Map<String, Boolean> access) {
        this.access = access;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(Long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public Boolean getTotp() {
        return totp;
    }

    public void setTotp(Boolean totp) {
        this.totp = totp;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Set<String> getDisableableCredentialTypes() {
        return disableableCredentialTypes;
    }

    public void setDisableableCredentialTypes( Set<String> disableableCredentialTypes) {
        this.disableableCredentialTypes = disableableCredentialTypes;
    }

    public List<String> getRequiredActions() {
        return requiredActions;
    }

    public void setRequiredActions(ArrayList requiredActions) {
        this.requiredActions = requiredActions;
    }

    public Integer getNotBefore() {
        return notBefore;
    }

    public void setNotBefore(Integer notBefore) {
        this.notBefore = notBefore;
    }

    public Map<String, Boolean> getAccess() {
        return access;
    }

    public void setAccess(LinkedHashMap access) {
        this.access = access;
    }


    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

