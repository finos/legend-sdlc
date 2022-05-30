package org.finos.legend.sdlc.server.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;


public class LegendSDLCServerDependenciesConfiguration {

    public final List<String> dependencies;

    private LegendSDLCServerDependenciesConfiguration(List<String> dependencies)
    {
        this.dependencies = dependencies;
    }

    @JsonCreator
    public static LegendSDLCServerDependenciesConfiguration newDependenciesConfiguration(
            @JsonProperty("deps") List<String> dependencies
    )
    {
        return new LegendSDLCServerDependenciesConfiguration(dependencies);
    }

    public static LegendSDLCServerDependenciesConfiguration emptyConfiguration()
    {
        return new LegendSDLCServerDependenciesConfiguration(new ArrayList<>());
    }
}
