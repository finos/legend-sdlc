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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;


public class LegendSDLCServerDependenciesConfiguration
{

    public final Map<String, String> dependencies;

    private LegendSDLCServerDependenciesConfiguration(List<ConfigurationDependency> dependencies)
    {
        this.dependencies = getDependenciesMap(dependencies);
    }

    @JsonCreator
    public static LegendSDLCServerDependenciesConfiguration newDependenciesConfiguration(
            @JsonProperty("deps") List<ConfigurationDependency> dependencies
    )
    {
        return new LegendSDLCServerDependenciesConfiguration(dependencies);
    }

    public static LegendSDLCServerDependenciesConfiguration emptyConfiguration()
    {
        return new LegendSDLCServerDependenciesConfiguration(new ArrayList<>());
    }

    private Map<String, String> getDependenciesMap(List<ConfigurationDependency> dependencies)
    {
        Map<String, String> dependenciesMap = new HashMap<>();
        dependencies.forEach(dependency ->
        {
            if (dependency.getVersion() != null)
            {
                dependenciesMap.put(dependency.getName(), dependency.getVersion());
            }
            else if (dependency.getPackageName() != null)
            {
                try (InputStream is = getClass().getResourceAsStream(String.format("/META-INF/maven/%s/pom.properties", dependency.getPackageName())))
                {
                    Properties properties = new Properties();
                    properties.load(is);
                    dependenciesMap.put(dependency.getName(), properties.getProperty("version"));
                }
                catch (IOException e)
                {
                    dependenciesMap.put(dependency.getName(), null);
                }
            }
        });
        return dependenciesMap;
    }

    private static class ConfigurationDependency
    {

        private String name;
        private String version;
        private String packageName;

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getVersion()
        {
            return version;
        }

        public void setVersion(String version)
        {
            this.version = version;
        }

        public String getPackageName()
        {
            return packageName;
        }

        public void setPackageName(String packageName)
        {
            this.packageName = packageName;
        }
    }
}
