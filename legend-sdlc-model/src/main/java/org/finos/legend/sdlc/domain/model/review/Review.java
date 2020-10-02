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

package org.finos.legend.sdlc.domain.model.review;

import org.finos.legend.sdlc.domain.model.user.User;

import java.time.Instant;

public interface Review
{
    String getId();

    String getProjectId();

    String getWorkspaceId();

    String getTitle();

    String getDescription();

    Instant getCreatedAt();

    Instant getLastUpdatedAt();

    Instant getClosedAt();

    Instant getCommittedAt();

    ReviewState getState();

    User getAuthor();

    String getCommitRevisionId();

    /**
     * Get the web URL of the review, if one is available.
     *
     * @return web URL of the review
     */
    String getWebURL();
}
