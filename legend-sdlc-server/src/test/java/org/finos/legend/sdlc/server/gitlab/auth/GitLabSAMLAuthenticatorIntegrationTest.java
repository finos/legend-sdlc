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

package org.finos.legend.sdlc.server.gitlab.auth;

import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.gitlab.GitLabServerInfo;
import org.finos.legend.sdlc.server.tools.AuthenticationTools;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.security.auth.Subject;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end integration test for the patched {@link GitLabSAMLAuthenticator}.
 *
 * <p>This test does <strong>not</strong> use mocks &mdash; it performs a real
 * Kerberos SAML init handshake against a real GitLab server. By default it
 * targets {@value #DEFAULT_GITLAB_HOST} over {@value #DEFAULT_GITLAB_SCHEME};
 * override via:</p>
 * <ul>
 *   <li>{@code GITLAB_INTEGRATION_HOST} env var or {@code -Dgitlab.integration.host=<host>}</li>
 *   <li>{@code -Dgitlab.integration.scheme=<scheme>} (default {@value #DEFAULT_GITLAB_SCHEME})</li>
 *   <li>{@code -Dgitlab.integration.port=<port>} (default: scheme default)</li>
 * </ul>
 *
 * <p>The test requires a valid Kerberos TGT in the local ticket cache
 * ({@code kinit} on Linux/macOS, or a domain login on Windows). If a TGT
 * cannot be obtained, the tests are skipped via JUnit's {@link Assume}
 * mechanism so they do not break CI environments without Kerberos.</p>
 *
 * <p><strong>Scope.</strong> These tests validate the server-side half of
 * the SAML flow only &mdash; they prove that:</p>
 * <ol>
 *   <li>GitLab returns a usable CSRF token to an SPNego-authenticated
 *       {@code GET /users/sign_in} (so the priming step the patch added
 *       actually works), and</li>
 *   <li>The patched POST + {@link org.apache.http.impl.client.LaxRedirectStrategy}
 *       follows GitLab's {@code 302} onto the SAML IdP, producing the same
 *       on-the-wire behaviour the original GET + default-redirect-strategy
 *       code produced (a {@code GET} request against the IdP carrying the
 *       {@code SAMLRequest} query parameter).</li>
 * </ol>
 *
 * <p>What happens at the IdP after that is the IdP's responsibility and is
 * exactly the same in the new POST flow as it was in the old GET flow
 * &mdash; the upstream code likewise just handed the IdP response to a
 * Jsoup form parser and submitted whatever form came back. Validating that
 * roundtrip requires a healthy IdP and is outside the scope of this test.</p>
 *
 * <p>Run on Linux/macOS:</p>
 * <pre>
 *     kinit
 *     mvn -pl . -Dtest=GitLabSAMLAuthenticatorIntegrationTest test
 * </pre>
 *
 * <p>Run on Windows (PowerShell):</p>
 * <pre>
 *     klist           # confirm TGT
 *     mvn -pl . "-Dtest=GitLabSAMLAuthenticatorIntegrationTest" test
 * </pre>
 *
 * <p>Override the target host:</p>
 * <pre>
 *     GITLAB_INTEGRATION_HOST=gitlab.example.com mvn -pl . -Dtest=GitLabSAMLAuthenticatorIntegrationTest test
 * </pre>
 */
public class GitLabSAMLAuthenticatorIntegrationTest
{
    private static final String DEFAULT_GITLAB_HOST = "gitlab.example.com";
    private static final String DEFAULT_GITLAB_SCHEME = "https";
    private static final String HOST_ENV_VAR = "GITLAB_INTEGRATION_HOST";
    private static final String HOST_SYS_PROP = "gitlab.integration.host";
    private static final String SCHEME_SYS_PROP = "gitlab.integration.scheme";
    private static final String PORT_SYS_PROP = "gitlab.integration.port";

    private static String gitlabHost;
    private static String gitlabScheme;
    private static Integer gitlabPort;

    private GitLabAppInfo appInfo;
    private Subject kerberosSubject;
    private String expectedKerberosId;

    @BeforeClass
    public static void resolveConfig()
    {
        gitlabHost = firstNonEmpty(
                System.getenv(HOST_ENV_VAR),
                System.getProperty(HOST_SYS_PROP),
                DEFAULT_GITLAB_HOST);
        gitlabScheme = firstNonEmpty(
                System.getProperty(SCHEME_SYS_PROP),
                DEFAULT_GITLAB_SCHEME);
        String portStr = System.getProperty(PORT_SYS_PROP);
        gitlabPort = ((portStr == null) || portStr.isEmpty()) ? null : Integer.valueOf(portStr);
    }

    @Before
    public void setUp()
    {
        GitLabServerInfo serverInfo = GitLabServerInfo.newServerInfo(gitlabScheme, gitlabHost, gitlabPort);
        this.appInfo = GitLabAppInfo.newAppInfo(
                serverInfo,
                "integration-test-app-id",
                "integration-test-app-secret",
                "https://localhost/ignored");

        // Try to acquire a Kerberos subject from the local ticket cache (KRB5CCNAME or default).
        // If no TGT is available we skip the test rather than failing the build, so this test is
        // safe to leave enabled in CI environments without Kerberos.
        try
        {
            Configuration loginConfig = AuthenticationTools.getLocalLoginConfiguration();
            LoginContext loginContext = new LoginContext("gitlab-saml-integration-test", null, null, loginConfig);
            loginContext.login();
            this.kerberosSubject = loginContext.getSubject();
        }
        catch (LoginException e)
        {
            Assume.assumeNoException(
                    "Skipping GitLab integration test: no Kerberos TGT available. Run 'kinit' to enable.",
                    e);
        }
        this.expectedKerberosId = AuthenticationTools.getKerberosIdFromSubject(this.kerberosSubject);
        assertNotNull(
                "Could not derive a Kerberos id from the local ticket cache; check that 'kinit' has been run",
                this.expectedKerberosId);
    }

    /**
     * Verifies that {@link GitLabSAMLAuthenticator#obtainGitLabCsrfToken(CloseableHttpClient)}
     * actually retrieves a Rails {@code authenticity_token} from the real GitLab
     * {@code /users/sign_in} page over SPNego. This isolates the CSRF priming step from the
     * rest of the SAML flow so a regression in token extraction is easy to spot.
     */
    @Test
    public void obtainGitLabCsrfToken_returnsTokenFromRealGitLab() throws Exception
    {
        KerberosGitLabSAMLAuthenticator authenticator =
                new KerberosGitLabSAMLAuthenticator(this.appInfo, this.kerberosSubject);

        String token = Subject.doAs(this.kerberosSubject, (PrivilegedExceptionAction<String>) () ->
        {
            try (CloseableHttpClient client = GitLabSAMLAuthenticator.createSPNegoHttpClient(new BasicCookieStore()))
            {
                return authenticator.obtainGitLabCsrfToken(client);
            }
        });

        assertNotNull(
                "obtainGitLabCsrfToken returned null - GitLab /users/sign_in either did not return a "
                        + "csrf-token meta or authenticity_token input. Host: " + gitlabHost,
                token);
        assertFalse(
                "obtainGitLabCsrfToken returned an empty token from GitLab " + gitlabHost,
                token.isEmpty());
        // Rails authenticity_token is a base64-encoded ~88-character string; allow some slack.
        assertTrue(
                "CSRF token from GitLab " + gitlabHost + " looks too short to be valid (" + token.length() + " chars): " + token,
                token.length() >= 16);
    }

    /**
     * Validates that the patched {@code POST} + {@link
     * org.apache.http.impl.client.LaxRedirectStrategy} follows GitLab's {@code 302} to the
     * SAML IdP <em>exactly</em> the way the original upstream
     * {@code GET} + default-redirect-strategy code did. The upstream flow relied on Apache
     * HttpClient's default redirect strategy silently chasing the {@code 302} onto the IdP
     * (default strategy follows {@code 302} for {@code GET}/{@code HEAD}); the new flow
     * achieves the same outcome by enabling {@link
     * org.apache.http.impl.client.LaxRedirectStrategy}, which extends the default to also
     * follow {@code 302} for {@code POST}, converting the redirected method to {@code GET}
     * the same way a browser would.
     *
     * <p>This test is intentionally robust to IdP-side outcomes. We do <strong>not</strong>
     * assert anything about what the IdP returns &mdash; that is the IdP's responsibility
     * and is the same in both the old and new flows. We only assert the things the patched
     * code is itself responsible for:</p>
     * <ol>
     *   <li>The {@code POST} produced at least one redirect (i.e.
     *       {@code LaxRedirectStrategy} actually fired).</li>
     *   <li>The first hop went <em>off</em> the GitLab host (i.e. we did not bounce back to
     *       {@code /users/sign_in}, which is the original {@code 302}-bug symptom).</li>
     *   <li>That off-host URL carries a {@code SAMLRequest} query parameter, proving the
     *       OmniAuth {@code POST} + CSRF priming were accepted and GitLab generated a real
     *       SAML AuthnRequest.</li>
     * </ol>
     */
    @Test
    public void authPost_followsGitLabRedirectToIdpHost_sameAsOldGetFlow() throws Exception
    {
        KerberosGitLabSAMLAuthenticator authenticator =
                new KerberosGitLabSAMLAuthenticator(this.appInfo, this.kerberosSubject);

        final HttpClientContext context = HttpClientContext.create();

        Integer finalStatus = Subject.doAs(this.kerberosSubject, (PrivilegedExceptionAction<Integer>) () ->
        {
            CookieStore cookieStore = new BasicCookieStore();
            try (CloseableHttpClient client = GitLabSAMLAuthenticator.createSPNegoHttpClient(cookieStore))
            {
                // Mirror the production flow: prime CSRF, then POST /users/auth/saml with the
                // token in the body. We drive these by hand (rather than calling the public
                // authenticateAndGetSessionCookie()) so we can pass an HttpClientContext and
                // inspect the redirect chain afterwards.
                String csrfToken = authenticator.obtainGitLabCsrfToken(client);
                assertNotNull("Could not obtain CSRF token from " + gitlabHost, csrfToken);

                URI authURI = this.appInfo.getServerInfo().newURIBuilder().setPath("/users/auth/saml").build();
                HttpPost post = new HttpPost(authURI);
                List<NameValuePair> params = new ArrayList<>(1);
                params.add(new BasicNameValuePair("authenticity_token", csrfToken));
                post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));

                try (CloseableHttpResponse response = client.execute(post, context))
                {
                    EntityUtils.consume(response.getEntity());
                    return response.getStatusLine().getStatusCode();
                }
            }
        });

        List<URI> chain = context.getRedirectLocations();
        assertNotNull(
                "No redirect chain was recorded. The POST to " + gitlabHost + "/users/auth/saml "
                        + "was not redirected at all. LaxRedirectStrategy did not fire - this would "
                        + "mean the new code is NOT behaving like the old GET code, which always "
                        + "followed GitLab's 302 onto the IdP. Final HTTP status: " + finalStatus,
                chain);
        assertFalse(
                "Redirect chain was empty. GitLab did not produce a redirect at all. Final HTTP "
                        + "status: " + finalStatus,
                chain.isEmpty());

        URI firstRedirect = chain.get(0);
        assertNotNull("First redirect URI had no host: " + firstRedirect, firstRedirect.getHost());
        assertNotEquals(
                "First redirect host was GitLab itself (" + firstRedirect + "). This is the "
                        + "original 302-to-/users/sign_in failure mode - GitLab rejected the POST "
                        + "and bounced us back to the sign-in page instead of generating a "
                        + "SAMLRequest. Final HTTP status: " + finalStatus,
                gitlabHost.toLowerCase(),
                firstRedirect.getHost().toLowerCase());

        String query = firstRedirect.getRawQuery();
        assertNotNull(
                "First redirect URL had no query string: " + firstRedirect,
                query);
        assertTrue(
                "First redirect URL " + firstRedirect + " did not carry a SAMLRequest query "
                        + "parameter. GitLab did not produce a valid SAML AuthnRequest, which "
                        + "means the OmniAuth POST + CSRF token were not accepted.",
                query.contains("SAMLRequest="));

        // Diagnostic - shows up in surefire's stdout capture, useful when reading test reports.
        System.out.println("[GitLabSAMLAuthenticator IT] redirect chain = " + chain.size()
                + " hop(s); first hop -> " + firstRedirect.getHost()
                + "; final HTTP status = " + finalStatus
                + " (IdP roundtrip beyond this point is out of scope for the SDK)");
    }

    private static String firstNonEmpty(String... values)
    {
        if (values != null)
        {
            for (String value : values)
            {
                if ((value != null) && !value.isEmpty())
                {
                    return value;
                }
            }
        }
        return null;
    }
}

