/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.esrestclient;

import co.elastic.apm.agent.tracer.Span;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

public class ElasticsearchEndpointMapTest {

    private static final Set<String> SEARCH_ENDPOINTS =
        new HashSet<>(
            Arrays.asList(
                "search",
                "async_search.submit",
                "msearch",
                "eql.search",
                "terms_enum",
                "search_template",
                "msearch_template",
                "render_search_template"));

    private static List<String> getPathParts(String route) {
        List<String> pathParts = new ArrayList<>();
        String routeFragment = route;
        int paramStartIndex = routeFragment.indexOf('{');
        while (paramStartIndex >= 0) {
            int paramEndIndex = routeFragment.indexOf('}');
            if (paramEndIndex < 0 || paramEndIndex <= paramStartIndex + 1) {
                throw new IllegalStateException("Invalid route syntax!");
            }
            pathParts.add(routeFragment.substring(paramStartIndex + 1, paramEndIndex));

            int nextIdx = paramEndIndex + 1;
            if (nextIdx >= routeFragment.length()) {
                break;
            }

            routeFragment = routeFragment.substring(nextIdx);
            paramStartIndex = routeFragment.indexOf('{');
        }
        return pathParts;
    }

    @Test
    public void testIsSearchEndpoint() {
        for (ElasticsearchEndpointDefinition esEndpointDefinition :
            ElasticsearchEndpointMap.getAllEndpoints()) {
            String endpointId = esEndpointDefinition.getEndpointName();
            assertEquals(SEARCH_ENDPOINTS.contains(endpointId), esEndpointDefinition.isSearchEndpoint());
        }
    }

    @Test
    public void testProcessPathParts() {
        for (ElasticsearchEndpointDefinition esEndpointDefinition :
            ElasticsearchEndpointMap.getAllEndpoints()) {
            for (String route :
                esEndpointDefinition.getRoutes().stream()
                    .map(ElasticsearchEndpointDefinition.Route::getName)
                    .collect(Collectors.toList())) {
                List<String> pathParts = getPathParts(route);
                String resolvedRoute = route.replace("{", "").replace("}", "");
                Map<String, String> observedParams = new HashMap<>();

                Span<?> dummy = Mockito.mock(Span.class);
                doAnswer((invoc) -> observedParams.put(invoc.getArgument(0), invoc.getArgument(1)))
                    .when(dummy).withOtelAttribute(any(), any());
                esEndpointDefinition.addPathPartAttributes(resolvedRoute, dummy);

                Map<String, String> expectedMap = new HashMap<>();
                pathParts.forEach(part -> expectedMap.put("db.elasticsearch.path_parts." + part, part));

                assertEquals(expectedMap, observedParams);
            }
        }
    }

    @Test
    public void testSearchEndpoint() {
        ElasticsearchEndpointDefinition esEndpoint = ElasticsearchEndpointMap.get("search");

        Map<String, String> observedParams = new HashMap<>();
        Span<?> dummy = Mockito.mock(Span.class);
        doAnswer((invoc) -> observedParams.put(invoc.getArgument(0), invoc.getArgument(1)))
            .when(dummy).withOtelAttribute(any(), any());

        esEndpoint.addPathPartAttributes("/test-index-1,test-index-2/_search", dummy);

        assertEquals("test-index-1,test-index-2", observedParams.get("db.elasticsearch.path_parts.index"));
    }

    @Test
    public void testBuildRegexPattern() {
        Pattern pattern =
            ElasticsearchEndpointDefinition.EndpointPattern.buildRegexPattern(
                "/_nodes/{node_id}/shutdown");
        assertEquals("^/_nodes/(?<node0id>[^/]+)/shutdown$", pattern.pattern());

        pattern =
            ElasticsearchEndpointDefinition.EndpointPattern.buildRegexPattern(
                "/_snapshot/{repository}/{snapshot}/_mount");
        assertEquals("^/_snapshot/(?<repository>[^/]+)/(?<snapshot>[^/]+)/_mount$", pattern.pattern());

        pattern =
            ElasticsearchEndpointDefinition.EndpointPattern.buildRegexPattern(
                "/_security/profile/_suggest");
        assertEquals("^/_security/profile/_suggest$", pattern.pattern());

        pattern =
            ElasticsearchEndpointDefinition.EndpointPattern.buildRegexPattern(
                "/_application/search_application/{name}");
        assertEquals("^/_application/search_application/(?<name>[^/]+)$", pattern.pattern());

        pattern = ElasticsearchEndpointDefinition.EndpointPattern.buildRegexPattern("/");
        assertEquals("^/$", pattern.pattern());
    }


}
