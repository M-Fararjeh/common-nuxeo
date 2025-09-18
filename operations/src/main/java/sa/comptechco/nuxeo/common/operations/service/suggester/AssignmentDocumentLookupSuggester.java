package sa.comptechco.nuxeo.common.operations.service.suggester;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.util.PageProviderHelper;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.ecm.platform.query.nxql.NXQLQueryBuilder;
import org.nuxeo.ecm.platform.suggestbox.service.Suggester;
import org.nuxeo.ecm.platform.suggestbox.service.Suggestion;
import org.nuxeo.ecm.platform.suggestbox.service.SuggestionContext;
import org.nuxeo.ecm.platform.suggestbox.service.SuggestionException;
import org.nuxeo.ecm.platform.suggestbox.service.descriptors.SuggesterDescriptor;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class AssignmentDocumentLookupSuggester implements  Suggester {

    protected List<String> highlights = null;

    protected String providerName = "DEFAULT_DOCUMENT_SUGGESTION";

    protected SuggesterDescriptor descriptor;

    private static final String SUGGESTER_MODE_NORMAL = "normal";
    private static final String SUGGESTER_MODE_ROOTS = "roots";

    @Override
    public void initWithParameters(SuggesterDescriptor descriptor) {
        this.descriptor = descriptor;
        String providerName = descriptor.getParameters().get("providerName");
        if (providerName != null) {
            this.providerName = providerName;
        }
        String highlightFields = descriptor.getParameters().get("highlightFields");
        if (highlightFields != null) {
            if (!StringUtils.isBlank(highlightFields)) {
                String[] fields = highlightFields.split(",");
                highlights = Arrays.asList(fields);
            }
        }
    }
    @Override
    @SuppressWarnings("unchecked")
    public List<Suggestion> suggest(String userInput, SuggestionContext context) throws SuggestionException {
        String[] inputs = userInput.split(",");

        PageProviderService ppService = Framework.getService(PageProviderService.class);
        Map<String, Serializable> props = new HashMap<>();
        props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY, (Serializable) context.session);
        inputs[0]= NXQLQueryBuilder.sanitizeFulltextInput(inputs[0]);
        if (inputs[0].trim().isEmpty()) {
            return Collections.emptyList();
        }
        boolean rootsMode = inputs.length > 1 && inputs[inputs.length - 1].trim().compareToIgnoreCase(SUGGESTER_MODE_ROOTS) == 0;
        if (inputs.length > 1 && (inputs[inputs.length - 1].trim().compareToIgnoreCase(SUGGESTER_MODE_ROOTS) == 0 || inputs[inputs.length - 1].trim().compareToIgnoreCase(SUGGESTER_MODE_NORMAL) == 0) ) {
            inputs[inputs.length - 1] = null;
        }
        if (rootsMode) {
            try {
                OperationContext ctx = new OperationContext(context.session);
                AutomationService service = Framework.getService(AutomationService.class);
                Map<String, Object> params = new HashMap<String, Object>();
                params.put("text", inputs[0]);
                params.put("separator", "%");
                inputs[0] = (String) service.run(ctx, "Document.TextRootExtractor", params);
            } catch (OperationException e) {
                e.printStackTrace();
            }
        }
        if (!inputs[0].endsWith(" ") && !inputs[0].endsWith("%")) {
            // perform a prefix search on the last typed word
            inputs[0] += "%";
        }
        try {
            List<Suggestion> suggestions = new ArrayList<>();

            Object[] objects = Arrays.stream(inputs).filter(Objects::nonNull).toArray(Object[]::new);
            List<String> resultsHighlights = highlights;
            if (!rootsMode) {
                resultsHighlights = highlights.stream().filter((s) -> s.compareToIgnoreCase("ctocr:wordsRoots") != 0).collect(Collectors.toList());
            }
            PageProvider<DocumentModel> pp = null;
            Properties namedParameters = new Properties();


            namedParameters.put("assignee", context.principal.getName());
            DocumentModel searchDocumentModel = PageProviderHelper.getSearchDocumentModel(context.session,
                    "VIP_ASSIGNMENT_SUGGESTION", namedParameters);
            pp = (PageProvider<DocumentModel>) ppService.getPageProvider(providerName,
                    searchDocumentModel, null, null, null, props, resultsHighlights, null, objects);

            for (DocumentModel doc : pp.getCurrentPage()) {
                suggestions.add(AssignmentDocumentLookupSuggestion.fromDocumentModel(doc));
            }
            return suggestions;
        } catch (QueryParseException e) {
            throw new SuggestionException(String.format("Suggester '%s' failed to perform query with input '%s'",
                    descriptor.getName(), userInput), e);
        }
    }


}
