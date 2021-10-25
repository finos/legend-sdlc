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

package org.finos.legend.sdlc.server.gitlab.api;

import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.gitlab.GitLabServerInfo;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabAuthorizerManager;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.auth.TestGitLabSession;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabModeInfo;
import org.finos.legend.sdlc.server.tools.StringTools;
import org.gitlab4j.api.Constants.TokenType;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.Version;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GitLabApiTestSetupUtil
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabApiTestSetupUtil.class);

    /**
     * Authenticates to GitLab and creates a test GitLabUserContext.
     *
     * @param username   the name of user for whom we create this context.
     * @param password   the password of user for whom we create this context.
     * @param hostUrl    the url of the test host.
     * @param hostScheme the scheme of the test host.
     * @param hostHost   the test host.
     * @param hostPort   the port (if necessary) of the test host.
     */
    public static GitLabUserContext prepareGitLabUserContextHelper(String username, String password, String hostUrl, String hostScheme, String hostHost, Integer hostPort) throws LegendSDLCServerException
    {
        GitLabMode gitLabMode = GitLabMode.PROD;
        TestHttpServletRequest httpServletRequest = new TestHttpServletRequest();

        TestGitLabSession session = new TestGitLabSession(username);
        GitLabApi oauthGitLabApi;
        Version version;

        try
        {
            oauthGitLabApi = GitLabApi.oauth2Login(hostUrl, username, password, null, null, true);
            Assert.assertNotNull(oauthGitLabApi);
            version = oauthGitLabApi.getVersion();
        }
        catch (GitLabApiException e)
        {
            StringBuilder builder = new StringBuilder("Error instantiating GitLabApi via OAuth2; response status: ").append(e.getHttpStatus());
            StringTools.appendThrowableMessageIfPresent(builder, e, "; error message: ");
            if (e.hasValidationErrors())
            {
                builder.append("; validation error(s): ").append(e.getValidationErrors());
            }
            throw new LegendSDLCServerException(builder.toString(), e);
        }

        String oauthToken = oauthGitLabApi.getAuthToken();
        LOGGER.info("Retrieved access token: {}", oauthToken);
        Assert.assertNotNull(version);

        GitLabServerInfo gitLabServerInfo = GitLabServerInfo.newServerInfo(hostScheme, hostHost, hostPort);
        GitLabAppInfo gitLabAppInfo = GitLabAppInfo.newAppInfo(gitLabServerInfo, null, null, null);
        GitLabModeInfo gitLabModeInfo = GitLabModeInfo.newModeInfo(gitLabMode, gitLabAppInfo);

        session.setGitLabToken(gitLabMode, oauthToken, TokenType.OAUTH2_ACCESS);
        session.setModeInfo(gitLabModeInfo);
        LegendSDLCWebFilter.setSessionAttributeOnServletRequest(httpServletRequest, session);

        GitLabAuthorizerManager authorizerManager = GitLabAuthorizerManager.newManager(Collections.emptyList());
        return new GitLabUserContext(httpServletRequest, null, authorizerManager);
    }

    protected static boolean hasOnlyBranchesWithNames(List<Branch> branchList, List<String> expectedNames)
    {
        List<String> branchNames = Lists.mutable.withAll(branchList.stream().map(branch ->
                                                                                        {
                                                                                            String branchName = branch.getName();
                                                                                            return "master".equals(branchName) ? branchName : branchName.substring(branchName.lastIndexOf('/') + 1);
                                                                                        })
                                                                            .collect(Collectors.toList()));
        Collections.sort(branchNames);
        Collections.sort(expectedNames);
        return expectedNames.equals(branchNames);
    }
}
