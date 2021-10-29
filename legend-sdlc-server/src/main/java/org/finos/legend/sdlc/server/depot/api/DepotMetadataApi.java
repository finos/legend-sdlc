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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.stream.Collectors;

public class DepotMetadataApi extends BaseDepotApi implements MetadataApi
{
    private static final Logger logger = LoggerFactory.getLogger(DepotMetadataApi.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GET_ENTITIES_PATH = "/api/projects/%s/%s/versions/%s";
    private static final String GET_DEPENDENCIES_PATH = "/api/projects/%s/%s/versions/%s/projectDependencies";

    @Inject
    public DepotMetadataApi(DepotConfiguration configuration)
    {
        super(DepotServerInfo.newServerInfo(configuration.getServerConfiguration()), configuration.getAuthClientInjector());
    }

    @Override
    public List<Entity> getEntities(String projectId, String versionId)
    {
        LegendSDLCServerException.validateNonNull(projectId, "Project id may be null");
        LegendSDLCServerException.validateNonNull(versionId, "Version id may be null");

        HttpGet getRequest = this.prepareGetRequest(projectId, versionId, GET_ENTITIES_PATH, Lists.mutable.empty());
        String response = this.execute(getRequest);
        try
        {
            List<DepotEntity> entities = objectMapper.readValue(response, new TypeReference<List<DepotEntity>>()
            {
            });
            return entities.stream().map(entity -> Entity.newEntity(entity.path, entity.classifierPath, entity.content)).collect(Collectors.toList());
        }
        catch (JsonProcessingException ex)
        {
            logger.warn(ex.getMessage());
            throw new DepotServerException(this.getServerInfo().getDepotURLString(), StringTools.appendThrowableMessageIfPresent("Failed to process response", ex));
        }
    }

    @Override
    public Set<DepotProjectVersion> getProjectDependencies(String projectId, String versionId, boolean transitive)
    {
        LegendSDLCServerException.validateNonNull(projectId, "Project id may be null");
        LegendSDLCServerException.validateNonNull(versionId, "Version id may be null");

        NameValuePair transitiveParam = new BasicNameValuePair("transitive", transitive ? "true" : "false");
        HttpGet getRequest = this.prepareGetRequest(projectId, versionId, GET_DEPENDENCIES_PATH, Lists.mutable.with(transitiveParam));
        String response = this.execute(getRequest);
        try
        {
            return objectMapper.readValue(response, new TypeReference<Set<DepotProjectVersion>>() {});
        }
        catch (JsonProcessingException ex)
        {
            logger.warn(ex.getMessage());
            throw new DepotServerException(this.getServerInfo().getDepotURLString(), StringTools.appendThrowableMessageIfPresent("Failed to process response", ex));
        }
    }

    private HttpGet prepareGetRequest(String projectId, String versionId, String requestPatch, List<NameValuePair> parameters)
    {
        DepotProjectId depotProjectId = DepotProjectId.parseProjectId(projectId);
        LegendSDLCServerException.validateNonNull(depotProjectId, "Project id may be null");

        String path = String.format(requestPatch, depotProjectId.getGroupId(), depotProjectId.getArtifactId(), versionId);
        URI uri = buildURI(path, parameters);

        return new HttpGet(uri);
    }

    public static class DepotEntity implements Entity
    {
        String path;
        String classifierPath;
        Map<String, ?> content;

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
