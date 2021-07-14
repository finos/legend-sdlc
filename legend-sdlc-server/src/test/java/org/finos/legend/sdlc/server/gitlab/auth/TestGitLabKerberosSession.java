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

package org.finos.legend.sdlc.server.gitlab.auth;

import org.finos.legend.server.pac4j.kerberos.KerberosProfile;
import org.pac4j.core.profile.CommonProfile;

import javax.security.auth.Subject;
import java.util.Collections;

public class TestGitLabKerberosSession extends AbstractTestGitLabSession
{
    private static final String KERBEROS_ID = "someid";
    private static final KerberosProfile PROFILE = newProfile(KERBEROS_ID, new Subject(true, Collections.singleton(() -> KERBEROS_ID), Collections.emptySet(), Collections.emptySet()));

    protected CommonProfile getProfile()
    {
        return PROFILE;
    }

    private static KerberosProfile newProfile(String id, Subject subject)
    {
        KerberosProfile profile = new KerberosProfile(subject, null);
        profile.setId(id);
        return profile;
    }
}
