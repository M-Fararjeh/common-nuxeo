package org.meeting;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(value = {"stackTrace","cause","localizedMessage","suppressed","httpStatusCode"}, allowSetters = true)
public class AutomationException extends RuntimeException {

    private String code;
    private String message;
    private String description;
    private int httpStatusCode;

    public AutomationException(int httpStatusCode, String code, String message) {
        super(message);
        this.httpStatusCode = httpStatusCode;
        this.code = code;
        this.message = message;
    }

    public AutomationException(int httpStatusCode, String code, String message,String description) {
        super(message);
        this.httpStatusCode = httpStatusCode;
        this.code = code;
        this.message = message;
        this.description=description;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(int httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "AutomationException{" +
                "code='" + code + '\'' +
                ", message='" + message + '\'' +
                ", description='" + description + '\'' +
                '}';
    }


}
