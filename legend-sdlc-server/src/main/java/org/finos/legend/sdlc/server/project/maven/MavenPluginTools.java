// Copyright 2021 Goldman Sachs
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

package org.finos.legend.sdlc.server.project.maven;

import org.apache.maven.model.ConfigurationContainer;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.Arrays;
import java.util.stream.Stream;

public class MavenPluginTools
{
    private static final String CONFIGURATION_NAME = "configuration";

    private MavenPluginTools()
    {
    }

    public static Plugin newPlugin(String groupId, String artifactId, String version)
    {
        Plugin plugin = new Plugin();
        if (groupId != null)
        {
            plugin.setGroupId(groupId);
        }
        if (artifactId != null)
        {
            plugin.setArtifactId(artifactId);
        }
        if (version != null)
        {
            plugin.setVersion(version);
        }
        return plugin;
    }

    // Plugin configuration

    public static Xpp3Dom getConfiguration(ConfigurationContainer configurationContainer)
    {
        Object configuration = configurationContainer.getConfiguration();
        if ((configuration != null) && !(configuration instanceof Xpp3Dom))
        {
            throw new RuntimeException("Unsupported configuration: " + configuration);
        }
        return (Xpp3Dom)configuration;
    }

    public static Xpp3Dom getOrAddConfiguration(ConfigurationContainer configurationContainer)
    {
        Xpp3Dom configuration = getConfiguration(configurationContainer);
        if (configuration == null)
        {
            configuration = newDom(CONFIGURATION_NAME);
            configurationContainer.setConfiguration(configuration);
        }
        return configuration;
    }

    public static <T extends ConfigurationContainer> T setConfiguration(T configurationContainer, Xpp3Dom... configs)
    {
        Xpp3Dom configuration = ((configs == null) || (configs.length == 0)) ? null : newDom(CONFIGURATION_NAME, configs);
        configurationContainer.setConfiguration(configuration);
        return configurationContainer;
    }

    public static <T extends ConfigurationContainer> T addConfiguration(T configurationContainer, Xpp3Dom config)
    {
        getOrAddConfiguration(configurationContainer).addChild(config);
        return configurationContainer;
    }

    public static <T extends ConfigurationContainer> T addConfigurations(T configurationContainer, Xpp3Dom... configs)
    {
        if (configs != null)
        {
            Xpp3Dom configuration = getOrAddConfiguration(configurationContainer);
            Arrays.stream(configs).forEach(configuration::addChild);
        }
        return configurationContainer;
    }

    public static Xpp3Dom newDom(String name)
    {
        return new Xpp3Dom(name);
    }

    public static Xpp3Dom newDom(String name, String value)
    {
        Xpp3Dom dom = newDom(name);
        dom.setValue(value);
        return dom;
    }

    public static Xpp3Dom newDom(String name, Xpp3Dom... children)
    {
        return newDom(name, (children == null) ? null : Arrays.stream(children));
    }

    public static Xpp3Dom newDom(String name, Iterable<? extends Xpp3Dom> children)
    {
        Xpp3Dom dom = new Xpp3Dom(name);
        if (children != null)
        {
            children.forEach(dom::addChild);
        }
        return dom;
    }

    public static Xpp3Dom newDom(String name, Stream<? extends Xpp3Dom> children)
    {
        Xpp3Dom dom = newDom(name);
        if (children != null)
        {
            children.forEach(dom::addChild);
        }
        return dom;
    }

    // Plugin execution

    public static Plugin addPluginExecution(Plugin plugin, PluginExecution execution)
    {
        plugin.addExecution(execution);
        return plugin;
    }

    public static Plugin addPluginExecutions(Plugin plugin, PluginExecution... executions)
    {
        if (executions != null)
        {
            Arrays.stream(executions).forEach(plugin::addExecution);
        }
        return plugin;
    }

    public static Plugin addPluginExecution(Plugin plugin, String phase, String goal)
    {
        return addPluginExecution(plugin, newPluginExecution(phase, goal));
    }

    public static Plugin addPluginExecution(Plugin plugin, String id, String phase, String... goals)
    {
        return addPluginExecution(plugin, newPluginExecution(id, phase, goals));
    }

    public static PluginExecution newPluginExecution(String id, String phase, String... goals)
    {
        PluginExecution execution = new PluginExecution();
        if (id != null)
        {
            execution.setId(id);
        }
        if (phase != null)
        {
            execution.setPhase(phase);
        }
        if (goals != null)
        {
            Arrays.stream(goals).forEach(execution::addGoal);
        }
        return execution;
    }

    public static PluginExecution newPluginExecution(String phase, String goal)
    {
        return newPluginExecution(null, phase, goal);
    }
}
