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

package org.finos.legend.sdlc.server.domain.api.project;

import org.finos.legend.sdlc.domain.model.project.Project;
import org.finos.legend.sdlc.domain.model.project.ProjectType;
import org.finos.legend.sdlc.domain.model.project.accessRole.AccessRole;

import java.util.Collections;
import java.util.List;

public interface ProjectApi
{
    Project getProject(String id);

    List<Project> getProjects(boolean user, String search, Iterable<String> tags, Iterable<ProjectType> types);

    Project createProject(String name, String description, ProjectType type, String groupId, String artifactId, Iterable<String> tags);

    void deleteProject(String id);

    void changeProjectName(String id, String newName);

    void changeProjectDescription(String id, String newDescription);

    default void addProjectTag(String id, String tag)
    {
        addProjectTags(id, Collections.singleton(tag));
    }

    default void addProjectTags(String id, Iterable<String> tags)
    {
        updateProjectTags(id, Collections.emptyList(), tags);
    }

    default void removeProjectTag(String id, String tag)
    {
        removeProjectTags(id, Collections.singleton(tag));
    }

    default void removeProjectTags(String id, Iterable<String> tags)
    {
        updateProjectTags(id, tags, Collections.emptyList());
    }

    void updateProjectTags(String id, Iterable<String> tagsToRemove, Iterable<String> tagsToAdd);

    void setProjectTags(String id, Iterable<String> tags);

    AccessRole getCurrentUserAccessRole(String id);

    ImportReport importProject(String id, ProjectType type, String groupId, String artifactId);

    interface ImportReport
    {
        Project getProject();

        /**
         * The review id will be present if changes were required to configure
         * the project. Otherwise, it will be null. If present, the project will
         * not be fully configured until the review is approved and committed.
         *
         * @return review id or null
         */
        String getReviewId();
    }
}
