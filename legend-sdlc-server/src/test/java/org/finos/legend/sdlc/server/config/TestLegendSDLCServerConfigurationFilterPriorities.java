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

package org.finos.legend.sdlc.server.config;

import io.dropwizard.jackson.Jackson;
import org.finos.legend.sdlc.server.BaseLegendSDLCServer;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

public class TestLegendSDLCServerConfigurationFilterPriorities
{
    private static final String SECURITY = LegendSDLCServerConfiguration.PAC4J_SECURITY_FILTER_NAME;
    private static final String CALLBACK = LegendSDLCServerConfiguration.PAC4J_CALLBACK_FILTER_NAME;
    private static final String LOGOUT = LegendSDLCServerConfiguration.PAC4J_LOGOUT_FILTER_NAME;
    private static final String SESSION = BaseLegendSDLCServer.SESSION_FILTER_NAME;

    @Test
    public void noPrioritiesStillRunSessionFilterAfterEveryPac4jFilter() throws Exception
    {
        Assert.assertEquals(Collections.singletonMap(SESSION, -1), parse("{}").getFilterPriorities());
    }

    @Test
    public void unnamedSessionFilterRunsAfterEveryPac4jFilter() throws Exception
    {
        Map<String, Integer> priorities = parse("{\"filterPriorities\":{\"CORS\":4}}").getFilterPriorities();
        Assert.assertEquals(Integer.valueOf(-1), priorities.get(SESSION));
        Assert.assertEquals("CORS is left alone", Integer.valueOf(4), priorities.get("CORS"));
    }

    @Test
    public void unnamedSessionFilterRunsAfterALoweredSecurityFilter() throws Exception
    {
        Map<String, Integer> priorities = parse("{\"filterPriorities\":{\"" + SECURITY + "\":-1,\"CORS\":4}}").getFilterPriorities();
        Assert.assertEquals(Integer.valueOf(-2), priorities.get(SESSION));
        Assert.assertEquals(Integer.valueOf(-1), priorities.get(SECURITY));
    }

    @Test
    public void legacyNameWithoutPac4jEntriesRunsSessionFilterAfterEveryPac4jFilter() throws Exception
    {
        Map<String, Integer> priorities = parse("{\"filterPriorities\":{\"GitLab\":1,\"CORS\":4}}").getFilterPriorities();
        Assert.assertEquals(Integer.valueOf(-1), priorities.get(SESSION));
        Assert.assertEquals("CORS is left alone", Integer.valueOf(4), priorities.get("CORS"));
    }

    @Test
    public void shippedOrderRunsSessionFilterAfterEveryPac4jFilter() throws Exception
    {
        // config.json, config-sample.yaml and config-test.yaml: the logout filter is unlisted, so stays at 0
        Map<String, Integer> priorities = parse("{\"filterPriorities\":{\"GitLab\":1,\"" + CALLBACK + "\":2,\"" + SECURITY + "\":3,\"CORS\":4}}").getFilterPriorities();
        Assert.assertEquals(Integer.valueOf(-1), priorities.get(SESSION));
        Assert.assertEquals(Integer.valueOf(2), priorities.get(CALLBACK));
        Assert.assertEquals(Integer.valueOf(3), priorities.get(SECURITY));
        Assert.assertNull(priorities.get(LOGOUT));
    }

    @Test
    public void explicitOrderAfterEveryPac4jFilterIsKept() throws Exception
    {
        Map<String, Integer> priorities = parse("{\"filterPriorities\":{\"GitLab\":1,\"" + LOGOUT + "\":2,\"" + CALLBACK + "\":3,\"" + SECURITY + "\":4}}").getFilterPriorities();
        Assert.assertEquals(Integer.valueOf(1), priorities.get(SESSION));
    }

    @Test
    public void newNameBeforeSecurityFilterIsMovedAfterIt() throws Exception
    {
        Map<String, Integer> priorities = parse("{\"filterPriorities\":{\"" + SESSION + "\":5,\"" + SECURITY + "\":3,\"" + CALLBACK + "\":4,\"" + LOGOUT + "\":4}}").getFilterPriorities();
        Assert.assertEquals(Integer.valueOf(2), priorities.get(SESSION));
    }

    @Test
    public void sessionFilterIsMovedBelowTheLowestPac4jFilter() throws Exception
    {
        Map<String, Integer> priorities = parse("{\"filterPriorities\":{\"" + SESSION + "\":5,\"" + SECURITY + "\":3,\"" + LOGOUT + "\":-2}}").getFilterPriorities();
        Assert.assertEquals(Integer.valueOf(-3), priorities.get(SESSION));
    }

    private static LegendSDLCServerConfiguration parse(String json) throws Exception
    {
        return Jackson.newObjectMapper().readValue(json, LegendSDLCServerConfiguration.class);
    }
}
