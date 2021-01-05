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

package org.finos.legend.sdlc.server.tools;

import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.KerberosCredentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.auth.KerberosSchemeFactory;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.impl.auth.SPNegoScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.eclipse.collections.api.factory.Maps;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

import java.security.Principal;
import java.util.Map;
import java.util.Objects;
import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

public class AuthenticationTools
{
    private AuthenticationTools()
    {
        // utility class
    }

    public static CloseableHttpClient createHttpClientForSPNego()
    {
        return createHttpClientForSPNego(null);
    }

    public static CloseableHttpClient createHttpClientForSPNego(CookieStore cookieStore)
    {
        HttpClientBuilder builder = createHttpClientBuilderForSPNego();
        if (cookieStore != null)
        {
            builder.setDefaultCookieStore(cookieStore);
        }
        return builder.build();
    }

    /**
     * Create a client builder configured for SPNego. This sets the default authentication
     * scheme registry and credentials provider.
     *
     * @return HTTP client builder configured for SPNego
     */
    public static HttpClientBuilder createHttpClientBuilderForSPNego()
    {
        return configureForSPNego(HttpClientBuilder.create());
    }

    /**
     * Configure a client builder for SPNego. This sets the default authentication
     * scheme registry and credentials provider.
     *
     * @param builder HTTP client builder
     * @return same builder after configuration
     */
    public static HttpClientBuilder configureForSPNego(HttpClientBuilder builder)
    {
        Lookup<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
                .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                .register(AuthSchemes.DIGEST, new DigestSchemeFactory())
                .register(AuthSchemes.NTLM, new NTLMSchemeFactory())
                .register(AuthSchemes.SPNEGO, new SPNegoWithDelegationSchemeFactory())
                .register(AuthSchemes.KERBEROS, new KerberosSchemeFactory(true, false))
                .build();

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, AuthSchemes.SPNEGO), new NullCredentials());

        builder.setDefaultAuthSchemeRegistry(authSchemeRegistry);
        builder.setDefaultCredentialsProvider(credentialsProvider);
        return builder;
    }

    /**
     * Return whether the subject has the given Kerberos id for
     * (at least) one of its principals.
     *
     * @param subject    subject
     * @param kerberosId Kerberos id
     * @return whether the subject has the Kerberos id for a principal
     */
    public static boolean subjectHasKerberosId(Subject subject, String kerberosId)
    {
        return subject.getPrincipals().stream().map(Principal::getName).anyMatch(name -> (name != null) && name.startsWith(kerberosId) && ((name.length() == kerberosId.length()) || (name.charAt(kerberosId.length()) == '@')));
    }

    /**
     * Get one Kerberos id for the given subject. If there are multiple
     * ids for the subject, an arbitrary one is returned. If there is no
     * Kerberos id, null is returned.
     *
     * @param subject subject
     * @return one Kerberos id (or null)
     */
    public static String getKerberosIdFromSubject(Subject subject)
    {
        return (subject == null) ? null : subject.getPrincipals().stream().map(AuthenticationTools::getKerberosIdFromPrincipal).filter(Objects::nonNull).findAny().orElse(null);
    }

    /**
     * Get the Kerberos id from a Principal. This is the principal's
     * name with any domain stripped off.
     *
     * @param principal principal
     * @return Kerberos id
     */
    public static String getKerberosIdFromPrincipal(Principal principal)
    {
        String name = principal.getName();
        if (name == null)
        {
            return null;
        }

        int atIndex = name.indexOf('@');
        return (atIndex == -1) ? name : name.substring(0, atIndex);
    }

    public static Configuration getLocalLoginConfiguration()
    {
        return new Configuration()
        {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name)
            {
                Map<String, String> options = Maps.mutable.with(
                        "useTicketCache", "true",
                        "doNotPrompt", "false");
                String ticketCacheName = System.getenv("KRB5CCNAME");
                if ((ticketCacheName != null) && !ticketCacheName.isEmpty())
                {
                    options.put("ticketCache", ticketCacheName);
                }

                AppConfigurationEntry ace = new AppConfigurationEntry(
                        "com.sun.security.auth.module.Krb5LoginModule",
                        AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                        options);

                return new AppConfigurationEntry[]{ace};
            }
        };
    }

    private static class NullCredentials implements Credentials
    {
        @Override
        public Principal getUserPrincipal()
        {
            return null;
        }

        @Override
        public String getPassword()
        {
            return null;
        }
    }

    private static class SPNegoWithDelegationSchemeFactory implements AuthSchemeProvider
    {
        @Override
        public AuthScheme create(final HttpContext context)
        {
            return new SPNegoWithDelegationScheme();
        }
    }

    private static class SPNegoWithDelegationScheme extends SPNegoScheme
    {
        private SPNegoWithDelegationScheme()
        {
            super(true, false);
        }

        @Override
        protected byte[] generateGSSToken(byte[] input, Oid oid, String authServer, Credentials credentials) throws GSSException
        {
            GSSCredential gssCredential = (credentials instanceof KerberosCredentials) ? ((KerberosCredentials)credentials).getGSSCredential() : null;

            GSSManager manager = GSSManager.getInstance();
            GSSName serverName = manager.createName("HTTP@" + authServer, GSSName.NT_HOSTBASED_SERVICE);

            GSSContext gssContext = manager.createContext(serverName.canonicalize(oid), oid, gssCredential, GSSContext.DEFAULT_LIFETIME);
            gssContext.requestCredDeleg(true);
            gssContext.requestMutualAuth(true);

            return gssContext.initSecContext((input == null) ? new byte[0] : input, 0, (input == null) ? 0 : input.length);
        }
    }
}
