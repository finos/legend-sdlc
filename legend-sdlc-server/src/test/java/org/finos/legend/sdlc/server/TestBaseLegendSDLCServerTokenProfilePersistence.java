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
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenClient;
import org.junit.Assert;
import org.junit.Test;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.DirectClient;

import java.util.Arrays;

public class TestBaseLegendSDLCServerTokenProfilePersistence
{
    /**
     * Every client starts with the flag set, as legend-shared's pac4j bundle leaves it on every
     * DirectClient when all configured clients are serializable.
     */
    @Test
    public void tokenClientsStopPersistingAndOtherClientsAreLeftAlone()
    {
        GitlabPersonalAccessTokenClient tokenClient = new GitlabPersonalAccessTokenClient();
        tokenClient.setSaveProfileInSession(true);
        GitlabPersonalAccessTokenClient secondTokenClient = new GitlabPersonalAccessTokenClient();
        secondTokenClient.setSaveProfileInSession(true);
        DirectClient otherClient = new DirectClient()
        {
            @Override
            protected void internalInit(boolean forceReinit)
            {
            }
        };
        otherClient.setSaveProfileInSession(true);
        LegendPac4jConfiguration config = new LegendPac4jConfiguration();
        config.setClients(Arrays.<Client>asList(tokenClient, otherClient, secondTokenClient));

        BaseLegendSDLCServer.doNotPersistPersonalAccessTokenProfiles(config);

        Assert.assertEquals(Boolean.FALSE, tokenClient.getSaveProfileInSession());
        Assert.assertEquals(Boolean.FALSE, secondTokenClient.getSaveProfileInSession());
        Assert.assertEquals("a client that is not a token client is not this decision's to change", Boolean.TRUE, otherClient.getSaveProfileInSession());
    }

    @Test
    public void aServerWithoutPac4jClientsIsUnaffected()
    {
        BaseLegendSDLCServer.doNotPersistPersonalAccessTokenProfiles(null);
        BaseLegendSDLCServer.doNotPersistPersonalAccessTokenProfiles(new LegendPac4jConfiguration());
    }
}
