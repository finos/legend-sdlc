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

package org.finos.legend.sdlc.server.depot.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicNameValuePair;
import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.server.depot.DepotConfiguration;
import org.finos.legend.sdlc.server.depot.DepotServerInfo;
import org.finos.legend.sdlc.server.depot.model.DepotProjectId;
import org.finos.legend.sdlc.server.depot.model.DepotProjectVersion;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.tools.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DepotMetadataApi extends BaseDepotApi implements MetadataApi
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DepotMetadataApi.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().addMixIn(Entity.class, EntityMixIn.class).build();

    private static final String GET_ENTITIES_PATH = "/api/projects/%s/%s/versions/%s";
    private static final String GET_DEPENDENCIES_PATH = "/api/projects/%s/%s/versions/%s/projectDependencies";

    @Inject
    public DepotMetadataApi(DepotConfiguration configuration)
    {
        super(DepotServerInfo.newServerInfo(configuration.getServerConfiguration()), configuration.getAuthClientInjector());
    }

    @Override
    public List<Entity> getEntities(DepotProjectId projectId, String versionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "Project id may be null");
        LegendSDLCServerException.validateNonNull(versionId, "Version id may be null");

        HttpGet getRequest = this.prepareGetRequest(projectId, versionId, GET_ENTITIES_PATH, Lists.mutable.empty());
        String response = this.execute(getRequest);
        try
        {
            return jsonMapper.readValue(response, new TypeReference<List<Entity>>() {});
        }
        catch (JsonProcessingException ex)
        {
            LOGGER.error("Error getting entities from metadata server", ex);
            throw new DepotServerException(this.getServerInfo().getDepotURLString(), StringTools.appendThrowableMessageIfPresent("Failed to process response", ex), ex);
        }
    }

    @Override
    public Set<DepotProjectVersion> getProjectDependencies(DepotProjectId projectId, String versionId, boolean transitive)
    {
        LegendSDLCServerException.validateNonNull(projectId, "Project id may be null");
        LegendSDLCServerException.validateNonNull(versionId, "Version id may be null");

        NameValuePair transitiveParam = new BasicNameValuePair("transitive", transitive ? "true" : "false");
        HttpGet getRequest = this.prepareGetRequest(projectId, versionId, GET_DEPENDENCIES_PATH, Lists.mutable.with(transitiveParam));
        String response = this.execute(getRequest);
        try
        {
            return jsonMapper.readValue(response, new TypeReference<Set<DepotProjectVersion>>() {});
        }
        catch (JsonProcessingException ex)
        {
            LOGGER.error("Error getting project dependencies from metadata server", ex);
            throw new DepotServerException(this.getServerInfo().getDepotURLString(), StringTools.appendThrowableMessageIfPresent("Failed to process response", ex), ex);
        }
    }

    private HttpGet prepareGetRequest(DepotProjectId projectId, String versionId, String requestPath, List<NameValuePair> parameters)
    {
        String path = String.format(requestPath, projectId.getGroupId(), projectId.getArtifactId(), versionId);
        URI uri = buildURI(path, parameters);

        return new HttpGet(uri);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, defaultImpl = DepotEntity.class)
    public interface EntityMixIn
    {
    }

    public static class DepotEntity implements Entity
    {
        private final String path;
        private final String classifierPath;
        private final Map<String, ?> content;

        @JsonCreator
        public DepotEntity(@JsonProperty("path") String path, @JsonProperty("classifierPath") String classifierPath, @JsonProperty("content") Map<String, ?> content)
        {
            this.path = path;
            this.classifierPath = classifierPath;
            this.content = content;
        }

        @Override
        public String getPath()
        {
            return this.path;
        }

        @Override
        public String getClassifierPath()
        {
            return this.classifierPath;
        }

        @Override
        public Map<String, ?> getContent()
        {
            return this.content;
        }
    }
}
