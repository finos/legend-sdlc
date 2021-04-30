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

package org.finos.legend.sdlc.server.gitlab.resources;

import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.server.auth.Token;
import org.finos.legend.sdlc.server.auth.Token.TokenReader;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabAuthAccessException;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabUserContext;
import org.finos.legend.sdlc.server.gitlab.mode.GitLabMode;
import org.finos.legend.sdlc.server.gitlab.tools.GitLabApiTools;
import org.finos.legend.sdlc.server.resources.BaseResource;
import org.finos.legend.sdlc.server.tools.StringTools;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

@Path("/auth")
public class GitLabAuthResource extends BaseResource
{
    private static final Pattern TERMS_OF_SERVICE_MESSAGE_PATTERN = Pattern.compile("terms\\s+of\\s+service", Pattern.CASE_INSENSITIVE);

    private final GitLabUserContext userContext;

    @Inject
    public GitLabAuthResource(GitLabUserContext userContext)
    {
        super();
        this.userContext = userContext;
    }

    @GET
    @Path("callback")
    public Object callback(@QueryParam("code") String code, @QueryParam("state") String state)
    {
        return executeWithLogging("processing auth callback", this::processAuthCallback, code, state);
    }

    @GET
    @Path("authorized")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean isAuthorized(@QueryParam("mode")
                                @ApiParam("GitLab modes to check authorization for") Set<GitLabMode> modes)
    {
        return executeWithLogging("checking authorization",
                () ->
                {
                    if ((modes == null) || modes.isEmpty())
                    {
                        return this.userContext.getValidGitLabModes().stream().allMatch(mode ->
                        {
                            try
                            {
                                return this.userContext.isModeAuthorized(mode);
                            }
                            catch (GitLabAuthAccessException e)
                            {
                                // tolerate access exceptions
                                getLogger().error("Access exception occurred while checking auth for mode {}", mode, e);
                                return true;
                            }
                        });
                    }
                    else
                    {
                        return modes.stream().filter(this.userContext::isValidMode).allMatch(this.userContext::isModeAuthorized);
                    }
                });
    }

    @GET
    @Path("authorize")
    @Produces(MediaType.TEXT_HTML)
    public String authorize(@QueryParam("mode")
                            @ApiParam("GitLab modes to attempt authorization for (defaults to all)") Set<GitLabMode> modes,
                            @QueryParam("redirect_uri")
                            @ApiParam("URI to redirect to when authorization is complete") String redirectUri)
    {
        return executeWithLogging("authorizing", () ->
        {
            if ((modes == null) || modes.isEmpty())
            {
                this.userContext.getValidGitLabModes().forEach(mode -> this.userContext.getGitLabAPI(mode, true));
            }
            else
            {
                modes.stream().filter(this.userContext::isValidMode).forEach(mode -> this.userContext.getGitLabAPI(mode, true));
            }
            if (redirectUri != null)
            {
                throw new LegendSDLCServerException(redirectUri, Status.FOUND);
            }
            return "<html><h1>Success</h1></html>";
        });
    }

    @GET
    @Path("termsOfServiceAcceptance")
    @Produces(MediaType.APPLICATION_JSON)
    public Set<String> termsOfServiceAcceptance(@QueryParam("mode")
                                                @ApiParam("GitLab modes to accept terms of service for (defaults to all)") Set<GitLabMode> modes)
    {
        return executeWithLogging("checking acceptance of terms of service", () ->
                this.userContext.getValidGitLabModes()
                        .stream()
                        .filter(m -> (modes == null) || modes.isEmpty() || modes.contains(m))
                        .map(this.userContext::getGitLabAPI)
                        .filter(api ->
                        {
                            try
                            {
                                GitLabApiTools.callWithRetries(() -> api.getUserApi().getCurrentUser(), 5, 1000);
                                return false;
                            }
                            catch (Exception e)
                            {
                                Status errorStatus;
                                if (e instanceof GitLabApiException)
                                {
                                    switch (((GitLabApiException) e).getHttpStatus())
                                    {
                                        case 403:
                                        {
                                            String message = e.getMessage();
                                            if ((message != null) && TERMS_OF_SERVICE_MESSAGE_PATTERN.matcher(message).find())
                                            {
                                                // error indicates terms of service need to be accepted
                                                return true;
                                            }
                                            errorStatus = Status.FORBIDDEN;
                                            break;
                                        }
                                        case 401:
                                        {
                                            errorStatus = Status.FORBIDDEN;
                                            break;
                                        }
                                        default:
                                        {
                                            errorStatus = null;
                                        }
                                    }
                                }
                                else
                                {
                                    errorStatus = null;
                                }
                                throw new LegendSDLCServerException(StringTools.appendThrowableMessageIfPresent("Error checking acceptance of terms of service", e), errorStatus, e);
                            }
                        })
                        .map(GitLabApi::getGitLabServerUrl)
                        .collect(Collectors.toSet())
        );
    }

    private Object processAuthCallback(String code, String state)
    {
        TokenReader reader = Token.newReader(state);
        String gitLabModeName = reader.getString();
        String originalRequestMethod = reader.getString();
        String originalRequestURL = reader.getString();

        GitLabMode mode;
        try
        {
            mode = GitLabMode.valueOf(gitLabModeName);
        }
        catch (IllegalArgumentException e)
        {
            throw new LegendSDLCServerException("Unknown GitLab mode: " + gitLabModeName, Status.INTERNAL_SERVER_ERROR);
        }

        try
        {
            this.userContext.gitLabAuthCallback(mode, code);
        }
        catch (LegendSDLCServerException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            StringBuilder message = new StringBuilder("Error processing auth callback");
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                message.append(": ").append(eMessage);
            }
            throw new LegendSDLCServerException(message.toString(), Status.INTERNAL_SERVER_ERROR, e);
        }

        if (!"GET".equalsIgnoreCase(originalRequestMethod))
        {
            // TODO consider whether 503 is the right status code
            throw new LegendSDLCServerException("Please retry request: " + originalRequestMethod + " " + originalRequestURL, Status.SERVICE_UNAVAILABLE);
        }

        // Redirect to original request URL
        throw new LegendSDLCServerException(originalRequestURL, Status.FOUND);
    }
}
