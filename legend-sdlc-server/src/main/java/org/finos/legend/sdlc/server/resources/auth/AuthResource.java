// Copyright 2026 Goldman Sachs
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

package org.finos.legend.sdlc.server.resources.auth;

import io.swagger.annotations.ApiParam;
import org.finos.legend.sdlc.backend.api.spi.AuthorizationRequiredException;
import org.finos.legend.sdlc.backend.api.spi.BackendSession;
import org.finos.legend.sdlc.error.LegendSDLCException;
import org.finos.legend.sdlc.server.auth.Token;
import org.finos.legend.sdlc.server.auth.Token.TokenReader;
import org.finos.legend.sdlc.server.error.LegendSDLCServerException;
import org.finos.legend.sdlc.server.resources.BaseResource;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

/**
 * The generic authorization surface over {@link BackendSession}'s auth contract, replacing the per-backend
 * {@code /auth} resources (routes and wire behavior unchanged). The OAuth {@code state} round-trip is owned
 * here: it encodes the original request so the callback can redirect back to it — request knowledge the backend
 * does not have. A backend needing interactive authorization throws {@link AuthorizationRequiredException}; on
 * this resource's authorize route (where redirects are allowed) it becomes a 302 to the backend's authorization
 * URI with the state appended, and everywhere else the exception mapper turns it into the 403
 * {@code auth_uri} body.
 */
@Path("/auth")
public class AuthResource extends BaseResource
{
    private final BackendSession session;
    private final HttpServletRequest httpRequest;

    @Inject
    public AuthResource(BackendSession session, HttpServletRequest httpRequest)
    {
        super();
        this.session = session;
        this.httpRequest = httpRequest;
    }

    @GET
    @Path("authorize")
    @Produces(MediaType.TEXT_HTML)
    public String authorize(@QueryParam("redirect_uri") @ApiParam("URI to redirect to when authorization is complete") String redirectUri)
    {
        return executeWithLogging("authorizing", () ->
        {
            try
            {
                this.session.authorize();
            }
            catch (AuthorizationRequiredException e)
            {
                throw new LegendSDLCServerException(appendState(e.getAuthorizationUri()), Status.FOUND);
            }
            if (redirectUri != null)
            {
                throw new LegendSDLCServerException(redirectUri, Status.FOUND);
            }
            return "<html><h1>Success</h1></html>";
        });
    }

    @GET
    @Path("callback")
    public Object callback(@QueryParam("code") String code, @QueryParam("state") String state)
    {
        return executeWithLogging("processing auth callback", () ->
        {
            TokenReader reader = Token.newReader(state);
            String originalRequestMethod = reader.getString();
            String originalRequestURL = reader.getString();

            try
            {
                this.session.handleAuthorizationCallback(code, state);
            }
            catch (LegendSDLCException e)
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
                throw new LegendSDLCServerException("Please retry request: " + originalRequestMethod + " " + originalRequestURL, Status.SERVICE_UNAVAILABLE);
            }

            // Redirect to original request URL
            throw new LegendSDLCServerException(originalRequestURL, Status.FOUND);
        });
    }

    @GET
    @Path("termsOfServiceAcceptance")
    @Produces(MediaType.APPLICATION_JSON)
    // NOTE: we have to return a set for backward compatibility reason
    public Set<String> termsOfServiceAcceptance()
    {
        return executeWithLogging("checking acceptance of terms of service", this.session::getUnacceptedTermsOfService);
    }

    private String appendState(URI authorizationUri)
    {
        Token.TokenBuilder stateBuilder = Token.newBuilder();
        stateBuilder.putString(this.httpRequest.getMethod());
        StringBuffer urlBuilder = this.httpRequest.getRequestURL();
        String requestQueryString = this.httpRequest.getQueryString();
        if (requestQueryString != null)
        {
            urlBuilder.append('?').append(requestQueryString);
        }
        stateBuilder.putString(urlBuilder.toString());

        String uriString = authorizationUri.toString();
        try
        {
            return uriString + ((authorizationUri.getRawQuery() == null) ? '?' : '&') + "state=" + URLEncoder.encode(stateBuilder.toTokenString(), StandardCharsets.UTF_8.name());
        }
        catch (UnsupportedEncodingException e)
        {
            // UTF-8 is always supported
            throw new RuntimeException(e);
        }
    }
}
