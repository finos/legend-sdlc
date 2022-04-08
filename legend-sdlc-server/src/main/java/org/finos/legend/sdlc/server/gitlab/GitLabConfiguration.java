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

package org.finos.legend.sdlc.server.gitlab;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabAuthorizer;
import org.gitlab4j.api.models.Visibility;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class GitLabConfiguration
{
    private static final Pattern LEGEND_SDLC_PROJECT_ID_PREFIX_PATTERN = Pattern.compile("^\\w++$");
    private static final Pattern LEGEND_SDLC_PROJECT_TAG_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9]*$");

    private final String projectTag;
    private final String projectIdPrefix;
    private final AuthConfiguration authConfig;
    private final ServerConfiguration serverConfig;
    private final AppConfiguration appConfig;
    private final NewProjectVisibility newProjectVisibility;
    private final List<GitLabAuthorizer> gitLabAuthorizers;

    private GitLabConfiguration(String projectTag, String projectIdPrefix, AuthConfiguration authConfig, ServerConfiguration serverConfig, AppConfiguration appConfig, NewProjectVisibility newProjectVisibility, List<GitLabAuthorizer> gitLabAuthorizers)
    {
        if ((projectTag != null) && !LEGEND_SDLC_PROJECT_TAG_PATTERN.matcher(projectTag).matches())
        {
            throw new IllegalArgumentException("Invalid project tag: " + projectTag);
        }
        if ((projectIdPrefix != null) && !LEGEND_SDLC_PROJECT_ID_PREFIX_PATTERN.matcher(projectIdPrefix).matches())
        {
            throw new IllegalArgumentException("Invalid project ID prefix: " + projectIdPrefix);
        }
        this.projectTag = projectTag;
        this.projectIdPrefix = projectIdPrefix;
        this.authConfig = authConfig;
        this.serverConfig = serverConfig;
        this.appConfig = appConfig;
        this.newProjectVisibility = newProjectVisibility;
        this.gitLabAuthorizers = gitLabAuthorizers == null ? Collections.emptyList() : gitLabAuthorizers;
    }

    public String getProjectTag()
    {
        return this.projectTag;
    }

    public String getProjectIdPrefix()
    {
        return this.projectIdPrefix;
    }

    public AuthConfiguration getAuthConfig()
    {
        return this.authConfig;
    }

    public ServerConfiguration getServerConfiguration()
    {
        return this.serverConfig;
    }

    public AppConfiguration getAppConfiguration()
    {
        return this.appConfig;
    }

    public Visibility getNewProjectVisibility()
    {
        return (this.newProjectVisibility == null) ? null : this.newProjectVisibility.getGitLabVisibility();
    }

    public List<GitLabAuthorizer> getGitLabAuthorizers()
    {
        return this.gitLabAuthorizers;
    }

    @JsonCreator
    public static GitLabConfiguration newGitLabConfiguration(
        @JsonProperty("projectTag") String projectTag,
        @JsonProperty("projectIdPrefix") String projectIdPrefix,
        @JsonProperty("auth") AuthConfiguration authConfig,
        @JsonProperty("uat") ModeConfiguration uatConfig,
        @JsonProperty("prod") ModeConfiguration prodConfig,
        @JsonProperty("server") ServerConfiguration serverConfig,
        @JsonProperty("app") AppConfiguration appConfig,
        @JsonProperty("newProjectVisibility") NewProjectVisibility newProjectVisibility,
        @JsonProperty("gitlabAuthorizers") List<GitLabAuthorizer> gitLabAuthorizers)
    {
        ServerConfiguration _serverConfig = serverConfig;
        AppConfiguration _appConfig = appConfig;
        String _projectIdPrefix = projectIdPrefix;
        if (uatConfig != null && prodConfig != null)
        {
            throw new IllegalArgumentException("Configuration with multiple Gitlab modes is no longer supported");
        }
        else if (uatConfig != null)
        {
            if (serverConfig != null || appConfig != null)
            {
                throw new IllegalArgumentException("Gitlab server and/or application configuration should not be specified together with Gitlab mode configuration");
            }
            _serverConfig = uatConfig.getServerConfiguration();
            _appConfig = uatConfig.getAppConfiguration();
            _projectIdPrefix = "UAT";
        }
        else if (prodConfig != null)
        {
            if (serverConfig != null || appConfig != null)
            {
                throw new IllegalArgumentException("Gitlab server and/or application configuration should not be specified together with Gitlab mode configuration");
            }
            _serverConfig = prodConfig.getServerConfiguration();
            _appConfig = prodConfig.getAppConfiguration();
            _projectIdPrefix = "PROD";
        }
        return new GitLabConfiguration(projectTag, _projectIdPrefix, authConfig, _serverConfig, _appConfig, newProjectVisibility, gitLabAuthorizers);
    }

    public static GitLabConfiguration newGitLabConfiguration(String projectTag, String projectIdPrefix, AuthConfiguration authConfig, ServerConfiguration serverConfig, AppConfiguration appConfig, NewProjectVisibility newProjectVisibility)
    {
        return newGitLabConfiguration(projectTag, projectIdPrefix, authConfig, null, null, serverConfig, appConfig, newProjectVisibility, Collections.emptyList());
    }

    public static void configureObjectMapper(ObjectMapper objectMapper)
    {
        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_OBJECT)
        abstract class WrapperMixin
        {
        }

        objectMapper.addMixIn(GitLabAuthorizer.class, WrapperMixin.class);
    }

    public static class AuthConfiguration
    {
        private final String keytabLocation;

        private AuthConfiguration(String keytabLocation)
        {
            this.keytabLocation = keytabLocation;
        }

        public String getKeytabLocation()
        {
            return this.keytabLocation;
        }

        @JsonCreator
        public static AuthConfiguration newAuthConfiguration(@JsonProperty("keytabLocation") String keytabLocation)
        {
            return new AuthConfiguration(keytabLocation);
        }
    }

    @Deprecated
    public static class ModeConfiguration
    {
        private final ServerConfiguration serverConfig;
        private final AppConfiguration appConfig;

        private ModeConfiguration(ServerConfiguration serverConfig, AppConfiguration appConfig)
        {
            this.serverConfig = serverConfig;
            this.appConfig = appConfig;
        }

        public ServerConfiguration getServerConfiguration()
        {
            return this.serverConfig;
        }

        public AppConfiguration getAppConfiguration()
        {
            return this.appConfig;
        }

        @JsonCreator
        public static ModeConfiguration newModeConfiguration(@JsonProperty("server") ServerConfiguration serverConfig, @JsonProperty("app") AppConfiguration appConfig)
        {
            return new ModeConfiguration(serverConfig, appConfig);
        }
    }

    public static class ServerConfiguration
    {
        private final String scheme;
        private final String host;
        private final Integer port;

        private ServerConfiguration(String scheme, String host, Integer port)
        {
            this.scheme = scheme;
            this.host = host;
            this.port = port;
        }

        public String getScheme()
        {
            return this.scheme;
        }

        public String getHost()
        {
            return this.host;
        }

        public Integer getPort()
        {
            return this.port;
        }

        @JsonCreator
        public static ServerConfiguration newServerConfiguration(@JsonProperty("scheme") String scheme, @JsonProperty("host") String host, @JsonProperty("port") Integer port)
        {
            return new ServerConfiguration(scheme, host, port);
        }
    }

    public static class AppConfiguration
    {
        private final String id;
        private final String secret;
        private final String redirectURI;

        private AppConfiguration(String id, String secret, String redirectURI)
        {
            this.id = id;
            this.secret = secret;
            this.redirectURI = redirectURI;
        }

        public String getId()
        {
            return this.id;
        }

        public String getSecret()
        {
            return this.secret;
        }

        public String getRedirectURI()
        {
            return this.redirectURI;
        }

        @JsonCreator
        public static AppConfiguration newAppConfiguration(@JsonProperty("id") String id, @JsonProperty("secret") String secret, @JsonProperty("redirectURI") String redirectURI)
        {
            return new AppConfiguration(id, secret, redirectURI);
        }
    }

    public enum NewProjectVisibility
    {
        PUBLIC(Visibility.PUBLIC), PRIVATE(Visibility.PRIVATE), INTERNAL(Visibility.INTERNAL);

        private final Visibility gitLabVisibility;

        NewProjectVisibility(Visibility gitLabVisibility)
        {
            this.gitLabVisibility = gitLabVisibility;
        }

        public Visibility getGitLabVisibility()
        {
            return this.gitLabVisibility;
        }
    }
}
