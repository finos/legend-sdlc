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

package org.finos.legend.sdlc.server.auth;

import org.finos.legend.server.pac4j.kerberos.KerberosProfile;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;

import java.time.Instant;
import java.util.Iterator;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosTicket;

public class BaseKerberosSession<P extends KerberosProfile> extends BaseCommonProfileSession<P> implements KerberosSession
{
    protected BaseKerberosSession(P profile, String kerberosId, Instant creationTime)
    {
        super(profile, kerberosId, creationTime);
    }

    public Subject getSubject()
    {
        P profile = getProfile();
        return (profile == null) ? null : profile.getSubject();
    }

    @Override
    public boolean isValid()
    {
        if (!super.isValid())
        {
            return false;
        }

        Subject subject = getSubject();
        if (subject == null)
        {
            return false;
        }

        if (subject.getPublicCredentials().stream().anyMatch(this::isValidCredential))
        {
            return true;
        }

        // We have to use an Iterator because the nature of the private credential set: see Subject.getPrivateCredentials() for more information
        Iterator<Object> privateCredIter = subject.getPrivateCredentials().iterator();
        while (privateCredIter.hasNext())
        {
            try
            {
                Object credential = privateCredIter.next();
                if (isValidCredential(credential))
                {
                    return true;
                }
            }
            catch (SecurityException ignore)
            {
                // not allowed to access this credential
            }
        }

        return false;
    }

    private boolean isValidCredential(Object credential)
    {
        if (credential instanceof GSSCredential)
        {
            return isValidGSSCredential((GSSCredential)credential);
        }
        if (credential instanceof KerberosTicket)
        {
            return isValidKerberosTicket((KerberosTicket)credential);
        }
        // TODO handle more cases
        return false;
    }

    private boolean isValidGSSCredential(GSSCredential credential)
    {
        try
        {
            // TODO should we require some minimum number of seconds greater than 0?
            return credential.getRemainingLifetime() > 0;
        }
        catch (GSSException e)
        {
            return false;
        }
    }

    private boolean isValidKerberosTicket(KerberosTicket credential)
    {
        // TODO should we try to renew it if it isn't current?
        return credential.isCurrent();
    }
}
