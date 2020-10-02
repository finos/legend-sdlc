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

package org.finos.legend.sdlc.server.domain.api.build;

import org.finos.legend.sdlc.domain.model.build.Build;
import org.finos.legend.sdlc.domain.model.build.BuildStatus;

import java.util.List;

public interface BuildAccessContext
{
    Build getBuild(String buildId);

    // TODO add temporal constraints
    List<Build> getBuilds(Iterable<String> revisionIds, Iterable<BuildStatus> statuses, Integer limit);
}
