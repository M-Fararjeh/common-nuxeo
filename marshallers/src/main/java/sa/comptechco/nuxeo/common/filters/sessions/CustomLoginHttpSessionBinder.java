package sa.comptechco.nuxeo.common.filters.sessions;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class CustomLoginHttpSessionBinder implements HttpSessionBindingListener {

    private static Map<CustomLoginHttpSessionBinder, HttpSession> logins = new HashMap<CustomLoginHttpSessionBinder, HttpSession>();
    private String accountId;
    private boolean alreadyLoggedIn;

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public boolean isAlreadyLoggedIn() {
        return alreadyLoggedIn;
    }

    public void setAlreadyLoggedIn(boolean alreadyLoggedIn) {
        this.alreadyLoggedIn = alreadyLoggedIn;
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof CustomLoginHttpSessionBinder) && (getAccountId() != null) ?
                getAccountId().equals(((CustomLoginHttpSessionBinder) other).getAccountId()) : (other == this);
    }

    @Override
    public int hashCode() {
        return (getAccountId() != null) ?
                (this.getClass().hashCode() + getAccountId().hashCode()) : super.hashCode();
    }

    @Override
    public void valueBound(HttpSessionBindingEvent event) {
        // this code will keep the old session and set already logged in to true
        /*
        HttpSession oldSession = logins.get(this);
        if (oldSession != null) {
            alreadyLoggedIn = true;
        } else {
            logins.put(this, event.getSession());
        }*/


        //below code will remove old session of user and let the user log-in from new session.

        HttpSession session = logins.remove(this);
        System.out.printf("New session for user [ %s ] at [ %s ] %n", getAccountId(), new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));
        if (session != null) {
            System.out.printf("Revoke the old session for user [ %s ] at [ %s ] %n", getAccountId(), new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));

            session.setAttribute("com.comptechco.custom.session.invalid", Boolean.TRUE);

        }
        logins.put(this, event.getSession());
    }

    @Override
    public void valueUnbound(HttpSessionBindingEvent event) {
        System.out.printf("Removing session for user [ %s ] at [ %s ] %n", getAccountId(), new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));
        logins.remove(this);
    }

}