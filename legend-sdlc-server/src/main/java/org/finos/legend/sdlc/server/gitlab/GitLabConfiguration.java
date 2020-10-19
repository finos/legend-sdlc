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
import org.gitlab4j.api.models.Visibility;

import java.util.regex.Pattern;

public class GitLabConfiguration
{
    private static final Pattern LEGEND_SDLC_PROJECT_TAG_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9]*$");

    private final String projectTag;
    private final AuthConfiguration authConfig;
    private final ModeConfiguration uatConfig;
    private final ModeConfiguration prodConfig;
    private final NewProjectVisibility newProjectVisibility;

    private GitLabConfiguration(String projectTag, AuthConfiguration authConfig, ModeConfiguration uatConfig, ModeConfiguration prodConfig, NewProjectVisibility newProjectVisibility)
    {
        if ((projectTag != null) && !LEGEND_SDLC_PROJECT_TAG_PATTERN.matcher(projectTag).matches())
        {
            throw new IllegalArgumentException("Invalid project tag: " + projectTag);
        }
        this.projectTag = projectTag;
        this.authConfig = authConfig;
        this.uatConfig = uatConfig;
        this.prodConfig = prodConfig;
        this.newProjectVisibility = newProjectVisibility;
    }

    public String getProjectTag()
    {
        return this.projectTag;
    }

    public AuthConfiguration getAuthConfig()
    {
        return this.authConfig;
    }

    public ModeConfiguration getUATConfiguration()
    {
        return this.uatConfig;
    }

    public ModeConfiguration getProdConfiguration()
    {
        return this.prodConfig;
    }

    public Visibility getNewProjectVisibility()
    {
        return (this.newProjectVisibility == null) ? null : this.newProjectVisibility.getGitLabVisibility();
    }

    @JsonCreator
    public static GitLabConfiguration newGitLabConfiguration(@JsonProperty("projectTag") String projectTag, @JsonProperty("auth") AuthConfiguration authConfig, @JsonProperty("uat") ModeConfiguration uatConfig, @JsonProperty("prod") ModeConfiguration prodConfig, @JsonProperty("newProjectVisibility") NewProjectVisibility newProjectVisibility)
    {
        return new GitLabConfiguration(projectTag, authConfig, uatConfig, prodConfig, newProjectVisibility);
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
