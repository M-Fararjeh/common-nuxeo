/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     <a href="mailto:glefevre@nuxeo.com">Gildas</a>
 */
package sa.comptechco.nuxeo.common.operations.operations;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.platform.suggestbox.service.Suggestion;
import org.nuxeo.ecm.platform.suggestbox.service.SuggestionContext;
import org.nuxeo.ecm.platform.suggestbox.service.SuggestionException;
import org.nuxeo.ecm.platform.suggestbox.service.SuggestionService;
import org.nuxeo.runtime.api.Framework;

import sa.comptechco.nuxeo.common.operations.service.suggester.CustomDocumentLookupSuggestion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Operation used to suggest result by getting and calling all the suggesters defined in contributions.
 *
 * @since 6.0
 */
@Operation(id = CustomSuggestOperation.ID, category = Constants.CAT_UI, label = "Custom Suggesters launcher", description = "Get and launch the suggesters defined and return a list of Suggestion objects.", addToStudio = false)
public class CustomSuggestOperation {

    public static final String ID = "Search.CustomSuggestersLauncher";

    private static final String SUGGESTER_GROUP = "customsearchbox";

    private static final String SUGGESTER_MODE_NORMAL = "normal";
    private static final String SUGGESTER_MODE_ROOTS = "roots";

    @Context
    protected CoreSession session;

    @Context
    protected SuggestionService serviceSuggestion;

    @Param(name = "searchTerm", required = true)
    protected String searchTerm;

    @Param(name = "path", required = false)
    protected String path ;

    @Param(name = "mode", required = false)
    protected String mode = SUGGESTER_MODE_NORMAL;

    @OperationMethod
    public Blob run() throws SuggestionException, IOException {
        List<Map<String, Object>> result = new ArrayList<>();

        SuggestionContext suggestionContext = new SuggestionContext(SUGGESTER_GROUP, session.getPrincipal());
        suggestionContext.withSession(session);

        if (mode == null || mode.trim().length() == 0 || (mode.trim().compareToIgnoreCase(SUGGESTER_MODE_NORMAL) != 0 && mode.trim().compareToIgnoreCase(SUGGESTER_MODE_ROOTS) != 0)) {
            mode = SUGGESTER_MODE_NORMAL;
        }
        String userInput = searchTerm;
        if(!StringUtils.isEmpty(path))
        {
            userInput = String.join(",",searchTerm,path,mode.trim());
        } else {
            userInput = String.join(",",searchTerm,mode.trim());
        }
        List<Suggestion> listSuggestions = serviceSuggestion.suggest(userInput, suggestionContext);

        // For each suggestion, create a JSON object and add it to the result
        for (Suggestion suggestion : listSuggestions) {
            Map<String, Object> suggestionJSON = new LinkedHashMap<>();
            suggestionJSON.put("id", suggestion.getId());
            suggestionJSON.put("label", suggestion.getLabel());
            suggestionJSON.put("type", suggestion.getType());
            suggestionJSON.put("icon", suggestion.getIconURL());
            suggestionJSON.put("thumbnailUrl", suggestion.getThumbnailURL());
            suggestionJSON.put("url", suggestion.getObjectUrl());
            SchemaManager schemaManager = Framework.getService(SchemaManager.class);
            if(suggestion instanceof CustomDocumentLookupSuggestion) {
                suggestionJSON.put("typeLabel", ((CustomDocumentLookupSuggestion) suggestion).getTypeLabel());
                suggestionJSON.put("isFolder", ((CustomDocumentLookupSuggestion) suggestion).isFolder());
                suggestionJSON.put("mimetype",((CustomDocumentLookupSuggestion) suggestion).getMimetype());
                suggestionJSON.put("superType",((CustomDocumentLookupSuggestion) suggestion).getSuperType());
                suggestionJSON.put("documentTypeCode",((CustomDocumentLookupSuggestion) suggestion).getDocumentTypeCode());
            }
            List<Map<String, Object>> highlights = new ArrayList<>();
            if (suggestion.getHighlights() != null) {
                for (Entry<String, List<String>> e : suggestion.getHighlights().entrySet()) {
                    Map<String, Object> h = new LinkedHashMap<>();
                    h.put("field", e.getKey());
                    h.put("segments", e.getValue());
                    highlights.add(h);
                }
            }
            suggestionJSON.put("highlights", highlights);

            result.add(suggestionJSON);
        }

        return Blobs.createJSONBlobFromValue(result);
    }
}
