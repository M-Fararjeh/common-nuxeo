package org.nuxeo.ecm.restapi.server.jaxrs.extended;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Predicates;
import org.nuxeo.ecm.platform.audit.api.AuditQueryBuilder;
import org.nuxeo.ecm.platform.audit.api.AuditReader;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.DefaultObject;
import org.nuxeo.runtime.api.Framework;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;


@WebObject(type = "custom-audit")
public class CustomAudit extends DefaultObject {



    @Context
    protected HttpServletRequest request;

    @Context
    protected HttpServletResponse response;

    @Context
    protected CoreSession session;

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
    public List<LogEntry> readAuditLog(@QueryParam("eventIds") String eventIdss, @PathParam("id") String id) {

        AuditReader reader = Framework.getService(AuditReader.class);
        if(StringUtils.isEmpty(eventIdss)){
           // List<LogEntry> logEntries = reader.getLogEntriesFor(id, ctx.getCoreSession().getRepositoryName());
            AuditQueryBuilder builder = new AuditQueryBuilder();
            Predicate docId = Predicates.eq("docUUID", id);
            builder.predicate(docId);
            builder.defaultOrder();
          
            List<LogEntry> logEntriesFiltered = reader.queryLogs(builder);
            return logEntriesFiltered;
        } else {
            AuditQueryBuilder builder = new AuditQueryBuilder();
            Predicate docId = Predicates.eq("docUUID", id);
            builder.predicate(docId);
            builder.defaultOrder();
            String[] eventIds = eventIdss.split(",", 0);
            for (String eventId : eventIds) {
                Predicate eventIdPredicate = Predicates.noteq("eventId", eventId);
                builder.and(eventIdPredicate);
            }
            List<LogEntry> logEntriesFiltered = reader.queryLogs(builder);
            return logEntriesFiltered;
        }
    }
}
