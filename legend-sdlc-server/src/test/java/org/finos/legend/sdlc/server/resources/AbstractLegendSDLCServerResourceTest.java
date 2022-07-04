// Copyright 2020 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.sdlc.server.resources;

import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpResponseException;
import org.finos.legend.sdlc.domain.model.project.workspace.Workspace;
import org.finos.legend.sdlc.server.LegendSDLCServerForTest;
import org.finos.legend.sdlc.server.config.LegendSDLCServerConfiguration;
import org.finos.legend.sdlc.server.inmemory.backend.InMemoryBackend;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

public abstract class AbstractLegendSDLCServerResourceTest
{
    @ClassRule
    public static final DropwizardAppRule<LegendSDLCServerConfiguration> APP_RULE =
            new DropwizardAppRule<>(
                    LegendSDLCServerForTest.class,
                    ResourceHelpers.resourceFilePath("config-test.yaml"));

    protected Client client;

    @Rule
    public final SDLCServerClientRule sdlcServerClientRule = new SDLCServerClientRule();
    protected InMemoryBackend backend;

    @Before
    public void setUp()
    {
        this.backend = ((LegendSDLCServerForTest) APP_RULE.getApplication()).getGuiceBundle().getInjector().getInstance(InMemoryBackend.class);
        configureClient();
    }

    private void configureClient()
    {
        this.client = this.sdlcServerClientRule.getClient();
        this.client.target(getServerUrl()).request().get();
    }

    protected WebTarget clientFor(String url)
    {
        return this.client.target(getServerUrl(url));
    }

    protected String getServerUrl()
    {
        return getServerUrl(null);
    }

    protected String getServerUrl(String path)
    {
        return "http://localhost:" + APP_RULE.getLocalPort() + Optional.ofNullable(path).orElse("");
    }
    public RequestHelper requestHelperFor(String url){
        return new RequestHelper(this.clientFor(url));
    }

    static class RequestHelper {

        private Optional<Set<Response.Status.Family>> acceptableResponseStatuses = Optional.empty();
        private final WebTarget webTarget;

        private RequestHelper(WebTarget webTarget){
            this.webTarget = webTarget;
        }

        public RequestHelper withAcceptableResponseStatuses(Set<javax.ws.rs.core.Response.Status.Family> acceptableResponseStatuses){
            this.acceptableResponseStatuses = Optional.of(new HashSet<>(acceptableResponseStatuses));
            return this;
        }

        private Invocation.Builder request(){
            return null;
        }

        private void checkResponseIsAcceptable(Response response) throws HttpResponseException{
            if(this.acceptableResponseStatuses.isPresent()) {
                Set<javax.ws.rs.core.Response.Status.Family> acceptableResponseStatuses = this.acceptableResponseStatuses.get();
                if (!acceptableResponseStatuses.contains(response.getStatusInfo().getFamily())) {
                    throw new HttpResponseException(response.getStatus(), "Error during http call with status: " + response.getStatus() + " , entity: " + response.readEntity(String.class));
                }
            }
        }

        public Response get() throws HttpResponseException{
            Response response = this.request().get();
            checkResponseIsAcceptable(response);
            return response;
        }
        public <T> T getTyped() throws HttpResponseException{
            Response response = this.get();
            return response.readEntity(new GenericType<T>(){});
        }
    }


}
