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

package org.finos.legend.sdlc.server.depot.api;

import org.finos.legend.sdlc.server.gitlab.auth.GitLabAuthException;

public class DepotServerException extends RuntimeException
{
    private static final String BASE_MESSAGE = "Depot server error";

    private final String instanceUri;
    private final String detail;

    DepotServerException(String instanceUri, String detail, Throwable cause)
    {
        super(detail, cause);
        this.instanceUri = instanceUri;
        this.detail = detail;
    }

    DepotServerException(String instanceUri, String detail)
    {
        super(detail);
        this.instanceUri = instanceUri;
        this.detail = detail;
    }

    DepotServerException(String instanceUri, Throwable cause)
    {
        this(instanceUri, null, cause);
    }

    DepotServerException(String details)
    {
        this(null, details);
    }

    public String getInstanceUri()
    {
        String localInstance = this.instanceUri;
        if (localInstance == null)
        {
            Throwable cause = getCause();
            if (cause instanceof DepotServerException)
            {
                localInstance = ((DepotServerException)cause).getInstanceUri();
            }
        }
        return localInstance;
    }

    public String getDetail()
    {
        String localDetail = this.detail;
        if (localDetail == null)
        {
            Throwable cause = getCause();
            if (cause instanceof GitLabAuthException)
            {
                localDetail = ((GitLabAuthException)cause).getDetail();
            }
        }
        return localDetail;
    }

    @Override
    public String getMessage()
    {
        return buildMessage(getInstanceUri(), getDetail());
    }

    private static String buildMessage(String instanceUri, String detail)
    {
        if ((detail == null) && (instanceUri == null))
        {
            return BASE_MESSAGE;
        }

        StringBuilder message = new StringBuilder(BASE_MESSAGE);
        if (instanceUri != null)
        {
            message.append(" (instance URI= ").append(instanceUri).append(")");
        }
        if (detail != null)
        {
            message.append(": ").append(detail);
        }
        return message.toString();
    }
}
