// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.server.tools;

import org.finos.legend.sdlc.server.auth.LegendSDLCWebFilter;
import org.finos.legend.sdlc.server.auth.Session;
import org.finos.legend.sdlc.server.gitlab.GitLabAppInfo;
import org.finos.legend.sdlc.server.gitlab.GitLabServerInfo;
import org.finos.legend.sdlc.server.gitlab.api.TestHttpServletRequest;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabSession;
import org.finos.legend.sdlc.server.gitlab.auth.GitLabSessionBuilder;
import org.finos.legend.server.pac4j.gitlab.GitlabPersonalAccessTokenProfile;
import org.finos.legend.server.pac4j.kerberos.KerberosProfile;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.oidc.profile.OidcProfile;

import javax.security.auth.Subject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for SessionProvider.findSession() method.
 * Tests the various scenarios for finding or creating sessions from request attributes,
 * HTTP sessions, and pac4j profiles.
 */
public class TestSessionProvider
{
    private static final GitLabAppInfo APP_INFO = GitLabAppInfo.newAppInfo(
            GitLabServerInfo.newServerInfo("https", "gitlab.example.com", null),
            "test-app-id",
            "test-secret",
            "http://redirect.example.com"
    );

    private static final String APP_INFO_ATTRIBUTE = "org.finos.legend.sdlc.GitLabAppInfo";

    private SimpleHttpSession httpSession;
    private SimpleServletContext servletContext;

    @Before
    public void setUp()
    {
        this.httpSession = new SimpleHttpSession();
        this.servletContext = new SimpleServletContext();
        this.servletContext.setAttribute(APP_INFO_ATTRIBUTE, APP_INFO);
    }

    private TestHttpServletRequest createRequest()
    {
        return new TestHttpServletRequest(this.httpSession, this.servletContext);
    }

    @Test
    public void testFindSession_FromRequestAttribute()
    {
        OidcProfile profile = createOidcProfile("user123");
        GitLabSession expectedSession = GitLabSessionBuilder.newBuilder(APP_INFO).withProfile(profile).build();

        TestHttpServletRequest request = new TestHttpServletRequest();
        request.setAttribute(LegendSDLCWebFilter.SESSION_ATTRIBUTE, expectedSession);

        Session foundSession = SessionProvider.findSession(request);

        Assert.assertNotNull("Session should be found from request attribute", foundSession);
        Assert.assertSame("Should return the same session from request attribute", expectedSession, foundSession);
    }

    @Test
    public void testFindSession_FromHttpSession()
    {
        OidcProfile profile = createOidcProfile("user456");
        GitLabSession expectedSession = GitLabSessionBuilder.newBuilder(APP_INFO).withProfile(profile).build();
        this.httpSession.setAttribute(LegendSDLCWebFilter.SESSION_ATTRIBUTE, expectedSession);

        Session foundSession = SessionProvider.findSession(createRequest());

        Assert.assertNotNull("Session should be found from HTTP session", foundSession);
        Assert.assertSame("Should return the same session from HTTP session", expectedSession, foundSession);
    }

    @Test
    public void testFindSession_FromPac4jProfiles_OidcProfile()
    {
        setProfileInSession("OidcClient", createOidcProfile("oidc-user"));

        Session foundSession = SessionProvider.findSession(createRequest());

        Assert.assertNotNull("Session should be created from OIDC profile", foundSession);
        Assert.assertEquals("Session user ID should match profile ID", "oidc-user", foundSession.getUserId());
    }

    @Test
    public void testFindSession_FromPac4jProfiles_KerberosProfile()
    {
        setProfileInSession("KerberosClient", createKerberosProfile("kerberos-user"));

        Session foundSession = SessionProvider.findSession(createRequest());

        Assert.assertNotNull("Session should be created from Kerberos profile", foundSession);
        Assert.assertEquals("Session user ID should match profile ID", "kerberos-user", foundSession.getUserId());
    }

    @Test
    public void testFindSession_FromPac4jProfiles_GitlabPATProfile()
    {
        setProfileInSession("GitlabPATClient", createPATProfile("pat-token", "pat-user-id", "pat-username"));

        Session foundSession = SessionProvider.findSession(createRequest());

        Assert.assertNotNull("Session should be created from GitLab PAT profile", foundSession);
        Assert.assertEquals("Session user ID should match profile ID", "pat-user-id", foundSession.getUserId());
    }

    @Test
    public void testFindSession_NoProfiles_ReturnsNull()
    {
        this.httpSession.setAttribute(Pac4jConstants.USER_PROFILES, new HashMap<String, CommonProfile>());

        Session foundSession = SessionProvider.findSession(createRequest());

        Assert.assertNull("Session should be null when no profiles exist", foundSession);
    }

    @Test
    public void testFindSession_UnsupportedProfile_ReturnsNull()
    {
        CommonProfile profile = new CommonProfile();
        profile.setId("unsupported-user");
        setProfileInSession("UnsupportedClient", profile);

        Session foundSession = SessionProvider.findSession(createRequest());

        Assert.assertNull("Session should be null for unsupported profile types", foundSession);
    }

    @Test
    public void testFindSession_NoAppInfo_ReturnsNull()
    {
        this.servletContext.removeAttribute(APP_INFO_ATTRIBUTE);
        setProfileInSession("OidcClient", createOidcProfile("user-no-appinfo"));

        Session foundSession = SessionProvider.findSession(createRequest());

        Assert.assertNull("Session should be null when GitLabAppInfo is missing from context", foundSession);
    }

    @Test
    public void testFindSession_NoHttpSession_ReturnsNull()
    {
        TestHttpServletRequest request = new TestHttpServletRequest();

        Session foundSession = SessionProvider.findSession(request);

        Assert.assertNull("Session should be null when no HTTP session exists", foundSession);
    }

    @Test
    public void testFindSession_SessionCachedInRequestAttribute()
    {
        setProfileInSession("OidcClient", createOidcProfile("cache-test-user"));

        TestHttpServletRequest request = createRequest();
        Session firstSession = SessionProvider.findSession(request);
        Assert.assertNotNull("First call should create session", firstSession);

        Object cachedSession = request.getAttribute(LegendSDLCWebFilter.SESSION_ATTRIBUTE);
        Assert.assertNotNull("Session should be cached in request attribute", cachedSession);
        Assert.assertSame("Cached session should be the same as returned session", firstSession, cachedSession);

        Object httpSessionCached = this.httpSession.getAttribute(LegendSDLCWebFilter.SESSION_ATTRIBUTE);
        Assert.assertNotNull("Session should be cached in HTTP session", httpSessionCached);
        Assert.assertSame("HTTP session cached value should be the same session", firstSession, httpSessionCached);
    }

    // Helper methods

    private void setProfileInSession(String clientName, CommonProfile profile)
    {
        Map<String, CommonProfile> profileMap = new HashMap<>();
        profileMap.put(clientName, profile);
        this.httpSession.setAttribute(Pac4jConstants.USER_PROFILES, profileMap);
    }

    private static OidcProfile createOidcProfile(String id)
    {
        OidcProfile profile = new OidcProfile();
        profile.setId(id);
        return profile;
    }

    private static KerberosProfile createKerberosProfile(String id)
    {
        Subject subject = new Subject(true, Collections.singleton(() -> id), Collections.emptySet(), Collections.emptySet());
        KerberosProfile profile = new KerberosProfile(subject, null);
        profile.setId(id);
        return profile;
    }

    private static GitlabPersonalAccessTokenProfile createPATProfile(String token, String userId, String username)
    {
        return new GitlabPersonalAccessTokenProfile(token, userId, username, "gitlab.example.com");
    }

    /**
     * Minimal HttpSession implementation for testing.
     */
    private static class SimpleHttpSession implements HttpSession
    {
        private final Map<String, Object> attributes = new HashMap<>();

        @Override
        public Object getAttribute(String name)
        {
            return this.attributes.get(name);
        }

        @Override
        public void setAttribute(String name, Object value)
        {
            this.attributes.put(name, value);
        }

        @Override
        public void removeAttribute(String name)
        {
            this.attributes.remove(name);
        }

        @Override
        public Enumeration<String> getAttributeNames()
        {
            return Collections.enumeration(this.attributes.keySet());
        }

        @Override
        public long getCreationTime()
        {
            return 0;
        }

        @Override
        public String getId()
        {
            return "test-session-id";
        }

        @Override
        public long getLastAccessedTime()
        {
            return 0;
        }

        @Override
        public ServletContext getServletContext()
        {
            return null;
        }

        @Override
        public void setMaxInactiveInterval(int interval)
        {
        }

        @Override
        public int getMaxInactiveInterval()
        {
            return 0;
        }

        @Override
        @Deprecated
        public HttpSessionContext getSessionContext()
        {
            return null;
        }

        @Override
        @Deprecated
        public Object getValue(String name)
        {
            return getAttribute(name);
        }

        @Override
        @Deprecated
        public String[] getValueNames()
        {
            return this.attributes.keySet().toArray(new String[0]);
        }

        @Override
        @Deprecated
        public void putValue(String name, Object value)
        {
            setAttribute(name, value);
        }

        @Override
        @Deprecated
        public void removeValue(String name)
        {
            removeAttribute(name);
        }

        @Override
        public void invalidate()
        {
            this.attributes.clear();
        }

        @Override
        public boolean isNew()
        {
            return false;
        }
    }

    /**
     * Minimal ServletContext implementation for testing.
     */
    private static class SimpleServletContext implements ServletContext
    {
        private final Map<String, Object> attributes = new HashMap<>();

        @Override
        public Object getAttribute(String name)
        {
            return this.attributes.get(name);
        }

        @Override
        public void setAttribute(String name, Object object)
        {
            this.attributes.put(name, object);
        }

        @Override
        public void removeAttribute(String name)
        {
            this.attributes.remove(name);
        }

        @Override
        public Enumeration<String> getAttributeNames()
        {
            return Collections.enumeration(this.attributes.keySet());
        }

        // All other methods return null/default values
        @Override public String getContextPath() { return ""; }
        @Override public ServletContext getContext(String uripath) { return null; }
        @Override public int getMajorVersion() { return 3; }
        @Override public int getMinorVersion() { return 1; }
        @Override public int getEffectiveMajorVersion() { return 3; }
        @Override public int getEffectiveMinorVersion() { return 1; }
        @Override public String getMimeType(String file) { return null; }
        @Override public java.util.Set<String> getResourcePaths(String path) { return null; }
        @Override public java.net.URL getResource(String path) { return null; }
        @Override public java.io.InputStream getResourceAsStream(String path) { return null; }
        @Override public javax.servlet.RequestDispatcher getRequestDispatcher(String path) { return null; }
        @Override public javax.servlet.RequestDispatcher getNamedDispatcher(String name) { return null; }
        @Override @Deprecated public javax.servlet.Servlet getServlet(String name) { return null; }
        @Override @Deprecated public Enumeration<javax.servlet.Servlet> getServlets() { return Collections.emptyEnumeration(); }
        @Override @Deprecated public Enumeration<String> getServletNames() { return Collections.emptyEnumeration(); }
        @Override public void log(String msg) { }
        @Override @Deprecated public void log(Exception exception, String msg) { }
        @Override public void log(String message, Throwable throwable) { }
        @Override public String getRealPath(String path) { return null; }
        @Override public String getServerInfo() { return "TestServer/1.0"; }
        @Override public String getInitParameter(String name) { return null; }
        @Override public Enumeration<String> getInitParameterNames() { return Collections.emptyEnumeration(); }
        @Override public boolean setInitParameter(String name, String value) { return false; }
        @Override public String getServletContextName() { return "TestContext"; }
        @Override public javax.servlet.ServletRegistration.Dynamic addServlet(String servletName, String className) { return null; }
        @Override public javax.servlet.ServletRegistration.Dynamic addServlet(String servletName, javax.servlet.Servlet servlet) { return null; }
        @Override public javax.servlet.ServletRegistration.Dynamic addServlet(String servletName, Class<? extends javax.servlet.Servlet> servletClass) { return null; }
        @Override public <T extends javax.servlet.Servlet> T createServlet(Class<T> clazz) { return null; }
        @Override public javax.servlet.ServletRegistration getServletRegistration(String servletName) { return null; }
        @Override public Map<String, ? extends javax.servlet.ServletRegistration> getServletRegistrations() { return Collections.emptyMap(); }
        @Override public javax.servlet.FilterRegistration.Dynamic addFilter(String filterName, String className) { return null; }
        @Override public javax.servlet.FilterRegistration.Dynamic addFilter(String filterName, javax.servlet.Filter filter) { return null; }
        @Override public javax.servlet.FilterRegistration.Dynamic addFilter(String filterName, Class<? extends javax.servlet.Filter> filterClass) { return null; }
        @Override public <T extends javax.servlet.Filter> T createFilter(Class<T> clazz) { return null; }
        @Override public javax.servlet.FilterRegistration getFilterRegistration(String filterName) { return null; }
        @Override public Map<String, ? extends javax.servlet.FilterRegistration> getFilterRegistrations() { return Collections.emptyMap(); }
        @Override public javax.servlet.SessionCookieConfig getSessionCookieConfig() { return null; }
        @Override public void setSessionTrackingModes(java.util.Set<javax.servlet.SessionTrackingMode> sessionTrackingModes) { }
        @Override public java.util.Set<javax.servlet.SessionTrackingMode> getDefaultSessionTrackingModes() { return Collections.emptySet(); }
        @Override public java.util.Set<javax.servlet.SessionTrackingMode> getEffectiveSessionTrackingModes() { return Collections.emptySet(); }
        @Override public void addListener(String className) { }
        @Override public <T extends java.util.EventListener> void addListener(T t) { }
        @Override public void addListener(Class<? extends java.util.EventListener> listenerClass) { }
        @Override public <T extends java.util.EventListener> T createListener(Class<T> clazz) { return null; }
        @Override public javax.servlet.descriptor.JspConfigDescriptor getJspConfigDescriptor() { return null; }
        @Override public ClassLoader getClassLoader() { return getClass().getClassLoader(); }
        @Override public void declareRoles(String... roleNames) { }
        @Override public String getVirtualServerName() { return "localhost"; }
    }
}
