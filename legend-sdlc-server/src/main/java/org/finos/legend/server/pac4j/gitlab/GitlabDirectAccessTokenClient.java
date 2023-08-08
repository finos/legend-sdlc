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

package org.finos.legend.server.pac4j.gitlab;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.finos.legend.server.pac4j.SerializableProfile;
import org.pac4j.http.client.direct.DirectBearerAuthClient;


@SerializableProfile
public class GitlabDirectAccessTokenClient extends DirectBearerAuthClient
{
    @JsonProperty
    public String headerTokenName;

    @JsonProperty
    public String scheme;

    @JsonProperty
    public String gitlabApiVersion;

    @JsonProperty
    public String gitlabHost;

    @Override
    public String getName()
    {
        return "TODO";
    }

    @Override
    protected void clientInit()
    {
        defaultAuthenticator(new GitlabDirectAccessTokenAuthenticator(scheme, gitlabHost, gitlabApiVersion));
        defaultCredentialsExtractor(new GitlabDirectAccessTokenExtractor(headerTokenName));
        defaultProfileCreator(new GitlabDirectAccessTokenProfileCreator(gitlabHost));
    }
}
