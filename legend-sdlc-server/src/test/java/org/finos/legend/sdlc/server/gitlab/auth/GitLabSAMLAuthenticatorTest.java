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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for the package-private CSRF parser in {@link GitLabSAMLAuthenticator}.
 * No network access required; runs as part of {@code mvn test}.
 */
public class GitLabSAMLAuthenticatorTest
{
    @Test
    public void extractCsrfToken_metaTag_returnsToken()
    {
        String html = "<!DOCTYPE html><html><head>"
                + "<meta name=\"csrf-param\" content=\"authenticity_token\" />"
                + "<meta name=\"csrf-token\" content=\"abc123==\" />"
                + "</head><body>sign in</body></html>";
        assertEquals("abc123==", GitLabSAMLAuthenticator.extractCsrfToken(html));
    }

    @Test
    public void extractCsrfToken_metaTagWithCaseAndWhitespace_returnsToken()
    {
        String html = "<html><head>\n"
                + "  <META   name='csrf-token'   content='tok-with-spaces' >\n"
                + "</head></html>";
        assertEquals("tok-with-spaces", GitLabSAMLAuthenticator.extractCsrfToken(html));
    }

    @Test
    public void extractCsrfToken_hiddenInputFallback_returnsToken()
    {
        String html = "<html><body>"
                + "<form action=\"/users/sign_in\" method=\"post\">"
                + "<input type=\"hidden\" name=\"authenticity_token\" value=\"fallback-tok\"/>"
                + "</form></body></html>";
        assertEquals("fallback-tok", GitLabSAMLAuthenticator.extractCsrfToken(html));
    }

    @Test
    public void extractCsrfToken_metaWins_overInputWhenBothPresent()
    {
        String html = "<html><head>"
                + "<meta name=\"csrf-token\" content=\"meta-tok\" />"
                + "</head><body>"
                + "<form><input name=\"authenticity_token\" value=\"input-tok\"/></form>"
                + "</body></html>";
        assertEquals("meta-tok", GitLabSAMLAuthenticator.extractCsrfToken(html));
    }

    @Test
    public void extractCsrfToken_metaWithEmptyContent_fallsBackToInput()
    {
        String html = "<html><head>"
                + "<meta name=\"csrf-token\" content=\"\" />"
                + "</head><body>"
                + "<form><input name=\"authenticity_token\" value=\"input-tok\"/></form>"
                + "</body></html>";
        assertEquals("input-tok", GitLabSAMLAuthenticator.extractCsrfToken(html));
    }

    @Test
    public void extractCsrfToken_noTokenAnywhere_returnsNull()
    {
        String html = "<html><body><p>nothing to see here</p></body></html>";
        assertNull(GitLabSAMLAuthenticator.extractCsrfToken(html));
    }

    @Test
    public void extractCsrfToken_emptyHtml_returnsNull()
    {
        assertNull(GitLabSAMLAuthenticator.extractCsrfToken(""));
    }

    @Test
    public void extractCsrfToken_nullHtml_returnsNull()
    {
        assertNull(GitLabSAMLAuthenticator.extractCsrfToken(null));
    }

    @Test
    public void extractCsrfToken_realisticGitLabSnippet_returnsToken()
    {
        // Trimmed snippet modelled on the real GitLab sign-in page.
        String html = ""
                + "<!DOCTYPE html><html lang='en'>"
                + "<head>"
                + "  <meta charset='utf-8'>"
                + "  <meta name='viewport' content='width=device-width, initial-scale=1, maximum-scale=1'>"
                + "  <meta name='csrf-param' content='authenticity_token'>"
                + "  <meta name='csrf-token' content='Ax9k7QwertYUiopASDFGHJklZxcvbnm1234567890=='>"
                + "  <title>Sign in &middot; GitLab</title>"
                + "</head>"
                + "<body>"
                + "  <form action='/users/auth/saml' method='post'>"
                + "    <input type='hidden' name='authenticity_token' value='form-tok' />"
                + "    <button type='submit'>SAML</button>"
                + "  </form>"
                + "</body></html>";
        assertEquals(
                "Ax9k7QwertYUiopASDFGHJklZxcvbnm1234567890==",
                GitLabSAMLAuthenticator.extractCsrfToken(html));
    }
}

