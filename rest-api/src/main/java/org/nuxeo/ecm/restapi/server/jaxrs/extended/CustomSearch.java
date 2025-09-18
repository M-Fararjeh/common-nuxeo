package org.nuxeo.ecm.restapi.server.jaxrs.extended;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.nuxeo.ecm.automation.*;
import org.nuxeo.ecm.automation.core.util.PaginableDocumentModelList;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventProducer;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.search.core.SavedSearchService;
import org.nuxeo.ecm.platform.userworkspace.api.UserWorkspaceService;

import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.extended.utils.CustomQueryExecutor;
import org.nuxeo.extended.utils.CustomSearchRequestDto;
import org.nuxeo.extended.utils.Operator;
import org.nuxeo.extended.utils.SearchPredicateDto;
import org.nuxeo.runtime.api.Framework;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@WebObject(type = "custom-search")
public class CustomSearch extends CustomQueryExecutor {
    private static final Logger log = LogManager.getLogger(CustomSearch.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    protected SavedSearchService savedSearchService;

    @Override
    public void initialize(Object... args) {
        initExecutor();
        savedSearchService = Framework.getService(SavedSearchService.class);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("pp/{pageProviderName}/execute")
    public Object doQueryByPageProvider(@Context UriInfo uriInfo,
                                        @PathParam("pageProviderName") String pageProviderName,
                                        String requestBody) throws JsonProcessingException {

        MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
        JsonNode rootNode = objectMapper.readTree(requestBody);
        JsonNode aggregationNames = rootNode.get("aggregationNames");
        ((com.fasterxml.jackson.databind.node.ObjectNode) rootNode).remove("aggregationNames");
        CustomSearchRequestDto customSearchRequestDto = objectMapper.treeToValue(rootNode, CustomSearchRequestDto.class);
        //  CustomSearchRequestDto customSearchRequestDto = objectMapper.readValue(requestBody, CustomSearchRequestDto.class);
        List<SearchPredicateDto> validPredicates = Optional.ofNullable(customSearchRequestDto)
                .map(CustomSearchRequestDto::getPredicateList)
                .orElse(Collections.emptyList())
                .stream()
                .filter(searchPredicateDto -> Operator.isValidOperator(searchPredicateDto.getOperator()))
                .peek(predicate -> predicate.setOperator(" " + predicate.getOperator().trim() + " "))
                .collect(Collectors.toList());
        return queryByPageProvider(pageProviderName, queryParams, validPredicates, aggregationNames);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("pp/{pageProviderName}/suggest")
    public Object doQueryByPageProviderSuggestion(@Context UriInfo uriInfo,
                                                  @PathParam("pageProviderName") String pageProviderName,
                                                  String requestBody) throws JsonProcessingException {
        SearchPredicateDto searchPredicateDto = objectMapper.readValue(requestBody, SearchPredicateDto.class);
        PaginableDocumentModelList results = (PaginableDocumentModelList) queryByPageProvider(pageProviderName,
                uriInfo.getQueryParameters(), searchPredicateDto);
        String aggregateFiled = searchPredicateDto.getName();
        Map<String, Aggregate<? extends Bucket>> aggregateMap = results.getAggregates().entrySet().stream()
                .filter(entry -> entry.getValue().getField().equalsIgnoreCase(aggregateFiled))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return aggregateMap;
    }


    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("pp/{pageProviderName}/report")
    public Blob doQueryByPageProviderRe(@Context UriInfo uriInfo,
                                        @PathParam("pageProviderName") String pageProviderName,
                                        String requestBody) throws JsonProcessingException, OperationException, JSONException {

        MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();

        JsonNode rootNode = objectMapper.readTree(requestBody);
        JsonNode reportMetaData = rootNode.get("reportMetaData");
        ((com.fasterxml.jackson.databind.node.ObjectNode) rootNode).remove("aggregationNames");
        ((com.fasterxml.jackson.databind.node.ObjectNode) rootNode).remove("reportMetaData");
        CustomSearchRequestDto customSearchRequestDto = objectMapper.treeToValue(rootNode, CustomSearchRequestDto.class);
        List<SearchPredicateDto> validPredicates = Optional.ofNullable(customSearchRequestDto)
                .map(CustomSearchRequestDto::getPredicateList)
                .orElse(Collections.emptyList())
                .stream()
                .filter(searchPredicateDto -> Operator.isValidOperator(searchPredicateDto.getOperator()))
                .peek(predicate -> predicate.setOperator(" " + predicate.getOperator().trim() + " "))
                .collect(Collectors.toList());
        EventProducer service = Framework.getService(EventProducer.class);
        UserWorkspaceService uws = Framework.getService(UserWorkspaceService.class);
        DocumentModel home = uws.getUserPersonalWorkspace(this.ctx.getCoreSession().getPrincipal().getName(), this.ctx.getCoreSession().getRootDocument());
        EventContextImpl evctx = new DocumentEventContext(this.ctx.getCoreSession(),
                this.ctx.getCoreSession().getPrincipal(), home);
        Event event = evctx.newEvent("RMExportReport");
        event.getContext().setProperty("namedParameters", (Serializable) getNamedParameters(queryParams));
        event.getContext().setProperty("validPredicates", (Serializable) validPredicates);
        event.getContext().setProperty("sortInfo", (Serializable) getSortInfo(queryParams));
        event.getContext().setProperty("highlights", (Serializable) getHighlights(queryParams));
        event.getContext().setProperty("quickFilters", (Serializable) getQuickFilters(pageProviderName, queryParams));
        event.getContext().setProperty("pageIndex", (Serializable) getCurrentPageIndex(queryParams));
        event.getContext().setProperty("pageOffset", (Serializable) getCurrentPageOffset(queryParams));
        event.getContext().setProperty("props", (Serializable) getProperties());
        event.getContext().setProperty("pageSize", (Serializable) getPageSize(queryParams));
        event.getContext().setProperty("parameters", (Serializable) getParameters(queryParams));
        event.getContext().setProperty("reportMetaData", (Serializable) reportMetaData);
        event.getContext().setProperty("providerName", (Serializable) pageProviderName);
        service.fireEvent(event);
        JSONObject jsonResult = new JSONObject();
        jsonResult.put("Statue", "Success");
        return new StringBlob(jsonResult.toString(), "application/json");

    }
}

