// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.server;

import org.finos.legend.server.pac4j.LegendPac4jConfiguration;
import org.finos.legend.server.pac4j.LegendSecurityLogic;
import org.finos.legend.server.pac4j.LegendUserProfileStorageDecision;
import org.finos.legend.server.pac4j.gitlab.GitlabClient;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenClient;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenProfile;
import org.junit.Assert;
import org.junit.Test;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.client.DirectClient;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.FrameworkParameters;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.test.context.MockWebContext;
import org.pac4j.test.context.session.MockSessionStore;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class TestPersonalAccessTokenMultiProfilePersistence
{
    private static final String TOKEN_HEADER = "PRIVATE-TOKEN";
    private static final String MOZILLA = "Mozilla/5.0 (X11; Linux x86_64) Gecko/20100101 Firefox/128.0";

    @Test
    public void multiProfileBrowserCallDoesNotPersistTokenProfile()
    {
        MockSessionStore sessionStore = new MockSessionStore();
        MockWebContext context = tokenRequest().addRequestHeader("User-Agent", MOZILLA);

        Object result = perform(context, sessionStore, true);

        Assert.assertEquals("the token request must be granted", "GRANTED", result);
        Assert.assertEquals("the multiProfile browser path persisted the personal-access-token profile",
                Optional.empty(), sessionStore.get(context, Pac4jConstants.USER_PROFILES));
    }

    @Test
    public void multiProfileBrowserSessionDoesNotOutliveTheToken()
    {
        MockSessionStore sessionStore = new MockSessionStore();
        perform(tokenRequest().addRequestHeader("User-Agent", MOZILLA), sessionStore, true);

        MockWebContext withoutToken = MockWebContext.create()
                .setRequestMethod("GET")
                .setFullRequestURL("http://localhost:6100/api/projects?client_name=gitlabPAToken")
                .addRequestParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER, GitlabPersonalAccessTokenClient.GITLAB_PERSONAL_ACCESS_TOKEN_CLIENT_NAME)
                .addRequestHeader("User-Agent", MOZILLA);
        Object result = perform(withoutToken, sessionStore, true);

        Assert.assertNotEquals("a request without the token was granted from the persisted token profile", "GRANTED", result);
    }

    @Test
    public void multiProfileNonBrowserCallDoesNotPersistTokenProfile()
    {
        MockSessionStore sessionStore = new MockSessionStore();
        MockWebContext context = tokenRequest().addRequestHeader("User-Agent", "curl/8.5.0");

        Object result = perform(context, sessionStore, true);

        Assert.assertEquals("GRANTED", result);
        Assert.assertEquals(Optional.empty(), sessionStore.get(context, Pac4jConstants.USER_PROFILES));
    }

    @Test
    public void singleProfileBrowserCallDoesNotPersistTokenProfile()
    {
        MockSessionStore sessionStore = new MockSessionStore();
        MockWebContext context = tokenRequest().addRequestHeader("User-Agent", MOZILLA);

        Object result = perform(context, sessionStore, false);

        Assert.assertEquals("GRANTED", result);
        Assert.assertEquals(Optional.empty(), sessionStore.get(context, Pac4jConstants.USER_PROFILES));
    }

    private static MockWebContext tokenRequest()
    {
        return MockWebContext.create()
                .setRequestMethod("GET")
                .setFullRequestURL("http://localhost:6100/api/projects?client_name=gitlabPAToken")
                .addRequestParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER, GitlabPersonalAccessTokenClient.GITLAB_PERSONAL_ACCESS_TOKEN_CLIENT_NAME)
                .addRequestHeader(TOKEN_HEADER, "glpat-secret");
    }

    /**
     * Mirrors legend-shared's LegendPac4jBundle wiring for the shipped GitLab configuration: an OIDC client plus the
     * token client, both {@code @SerializableProfile}.
     */
    private static Object perform(MockWebContext context, MockSessionStore sessionStore, boolean multiProfile)
    {
        GitlabClient oidcClient = new GitlabClient();
        GitlabPersonalAccessTokenClient tokenClient = new GitlabPersonalAccessTokenClient();
        tokenClient.headerTokenName = TOKEN_HEADER;
        tokenClient.host = "gitlab.example.com";
        tokenClient.setAuthenticator((ctx, credentials) -> Optional.of(credentials));
        tokenClient.setProfileCreator((ctx, credentials) -> Optional.of(new GitlabPersonalAccessTokenProfile("glpat-secret", "42", "someone", "gitlab.example.com")));
        List<Client> clients = Arrays.asList(oidcClient, tokenClient);

        LegendUserProfileStorageDecision decision = new LegendUserProfileStorageDecision();
        LegendSecurityLogic securityLogic = new LegendSecurityLogic();
        securityLogic.setProfileStorageDecision(decision);
        securityLogic.setMultiProfile(multiProfile);
        // the bundle copies the all-clients decision onto every DirectClient
        boolean save = decision.mustSaveProfileInSession(null, clients, null, null);
        clients.stream().filter(c -> c instanceof DirectClient).forEach(c -> ((DirectClient) c).setSaveProfileInSession(save));

        Config config = new Config(new Clients("http://localhost:6100/api/callback", clients));
        config.setSecurityLogic(securityLogic);
        config.setWebContextFactory(parameters -> context);
        config.setSessionStoreFactory(parameters -> sessionStore);
        config.setHttpActionAdapter((action, webContext) -> action);
        config.setProfileManagerFactory(ProfileManager::new);

        LegendPac4jConfiguration pac4jConfiguration = new LegendPac4jConfiguration();
        pac4jConfiguration.setClients(clients);
        applyServerHardening(pac4jConfiguration, config);

        return securityLogic.perform(config, (webContext, store, profiles) -> "GRANTED",
                GitlabPersonalAccessTokenClient.GITLAB_PERSONAL_ACCESS_TOKEN_CLIENT_NAME, "none", "none", new FrameworkParameters()
                {
                });
    }

    private static void applyServerHardening(LegendPac4jConfiguration pac4jConfiguration, Config config)
    {
        BaseLegendSDLCServer.doNotPersistPersonalAccessTokenProfiles(pac4jConfiguration);
    }
}
