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

package org.finos.legend.sdlc.domain.model.comparison;

import java.util.List;

public interface Comparison
{
    /**
     * Get the current revision being compared to.
     *
     * @return Returns revision id
     */
    String getToRevisionId();

    /**
     * Get the starting revision being compared from.
     *
     * @return Returns revision id
     */
    String getFromRevisionId();

    /**
     * Get the list of entity diffs between from and to revision id.
     *
     * @return Returns list of entity diffs
     */
    List<EntityDiff> getEntityDiffs();

    /**
     * Get a flag indicating if project configuration has been updated.
     * Note that, although it's not difficult, we don't want to build out the diff here for project configuration
     * in order to be consistent with entity diffs (i.e. we don't include the actual entities, to lesser payload size).
     *
     * @return flag indicating if project configuration has been updated
     */
    boolean isProjectConfigurationUpdated();
}
