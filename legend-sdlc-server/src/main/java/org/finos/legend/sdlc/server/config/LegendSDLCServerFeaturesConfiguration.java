// Copyright 2022 Goldman Sachs
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;

public class LegendSDLCServerFeaturesConfiguration
{
    public final boolean canCreateProject;
    public final boolean canCreateVersion;
    public final String legendEngineVersion;

    private LegendSDLCServerFeaturesConfiguration(boolean canCreateProject, boolean canCreateVersion)
    {
        this.canCreateProject = canCreateProject;
        this.canCreateVersion = canCreateVersion;

        this.legendEngineVersion = getLegendEngineVersion();
    }

    private String getLegendEngineVersion()
    {

        String legendEngineVersion = "";

        Properties p = new Properties();
        try (InputStream is = getClass().getResourceAsStream("/META-INF/maven/org.finos.legend.engine/legend-engine-protocol-pure/pom.properties");)
        {
            p.load(is);
            legendEngineVersion = p.getProperty("version", "");
        }
        catch (IOException e)
        {
            // ignore
        }

        return legendEngineVersion;
    }

    @JsonCreator
    public static LegendSDLCServerFeaturesConfiguration newFeaturesConfiguration(
        @JsonProperty("canCreateProject") boolean canCreateProject,
        @JsonProperty("canCreateVersion") boolean canCreateVersion
        )
    {
        return new LegendSDLCServerFeaturesConfiguration(canCreateProject, canCreateVersion);
    }

    public static LegendSDLCServerFeaturesConfiguration emptyConfiguration()
    {
        return new LegendSDLCServerFeaturesConfiguration(
            false,
            false
        );
    }
}
