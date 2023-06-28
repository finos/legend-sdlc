// Copyright 2022 Goldman Sachs
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

package org.finos.legend.sdlc.server.gitlab.api;

import org.finos.legend.sdlc.domain.model.project.workspace.WorkspaceType;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSource;
import org.finos.legend.sdlc.server.domain.api.workspace.WorkspaceSpecification;
import org.finos.legend.sdlc.server.project.ProjectFileAccessProvider;
import org.junit.Assert;
import org.junit.Test;

import java.util.function.Supplier;

public class TestBaseGitLabApi
{
    @Test
    public void testIsValidEntityName()
    {
        Assert.assertTrue(BaseGitLabApi.isValidEntityName("randomEntityName"));
        Assert.assertTrue(BaseGitLabApi.isValidEntityName("entityNameAllows$"));
        Assert.assertTrue(BaseGitLabApi.isValidEntityName("entityNameAllows$And_"));
        Assert.assertTrue(BaseGitLabApi.isValidEntityName("entity_name_only_has_underscore"));
        Assert.assertTrue(BaseGitLabApi.isValidEntityName("test_String_$1_10$__String_MANY_"));
        Assert.assertTrue(BaseGitLabApi.isValidEntityName("test_String_$1_10$__String_$1_MANY$_"));
        Assert.assertTrue(BaseGitLabApi.isValidEntityName("test_String_$1_MANY$__String_1_"));

        Assert.assertFalse(BaseGitLabApi.isValidEntityName("random::entity::path::entityName"));
        Assert.assertFalse(BaseGitLabApi.isValidEntityName("test_String_$1_10$__String_$1_*$_&"));
        Assert.assertFalse(BaseGitLabApi.isValidEntityName("entity_name_has_other_characters_*#@"));
    }

    @Test
    public void testIsValidEntityPath()
    {
        Assert.assertTrue(BaseGitLabApi.isValidEntityPath("random::entity::path"));
        Assert.assertTrue(BaseGitLabApi.isValidEntityPath("model::test::function::test_String_$1_10$__String_MANY_"));
        Assert.assertTrue(BaseGitLabApi.isValidEntityPath("model::test::function::test_String_$1_MANY$__String_1_"));

        Assert.assertFalse(BaseGitLabApi.isValidEntityPath("model"));
        Assert.assertFalse(BaseGitLabApi.isValidEntityPath("model::"));
        Assert.assertFalse(BaseGitLabApi.isValidEntityPath("model::*"));
        Assert.assertFalse(BaseGitLabApi.isValidEntityPath("model::$test::$function"));
        Assert.assertFalse(BaseGitLabApi.isValidEntityPath("meta::test::ValidClassName"));
        Assert.assertFalse(BaseGitLabApi.isValidEntityPath("model::has::other::characters::test_String_$1_10$__String_$1_*$_&"));
    }

    @Test
    public void testIsValidPackagePath()
    {
        Assert.assertTrue(BaseGitLabApi.isValidPackagePath("model"));
        Assert.assertTrue(BaseGitLabApi.isValidPackagePath("random::entity::path"));
        Assert.assertTrue(BaseGitLabApi.isValidPackagePath("meta"));
        Assert.assertTrue(BaseGitLabApi.isValidPackagePath("meta::entity::path"));

        Assert.assertFalse(BaseGitLabApi.isValidPackagePath("model::"));
        Assert.assertFalse(BaseGitLabApi.isValidPackagePath("model::*"));
        Assert.assertFalse(BaseGitLabApi.isValidPackagePath("model::$test::$function"));
        Assert.assertFalse(BaseGitLabApi.isValidPackagePath("model::test::function::test_String_$1_10$__String_MANY_"));
    }

    @Test
    public void testIsValidPackageableElementPath()
    {
        Assert.assertTrue(BaseGitLabApi.isValidPackageableElementPath("model"));
        Assert.assertTrue(BaseGitLabApi.isValidPackageableElementPath("meta::entity::path"));
        Assert.assertTrue(BaseGitLabApi.isValidPackageableElementPath("random::entity::path"));
        Assert.assertTrue(BaseGitLabApi.isValidPackageableElementPath("model::test::function::test_String_$1_10$__String_MANY_"));

        Assert.assertFalse(BaseGitLabApi.isValidPackageableElementPath("model::"));
        Assert.assertFalse(BaseGitLabApi.isValidPackageableElementPath("model::*"));
        Assert.assertFalse(BaseGitLabApi.isValidPackageableElementPath("model::$test::$function"));
        Assert.assertFalse(BaseGitLabApi.isValidPackageableElementPath("model::has::other::characters::test_String_$1_10$__String_$1_*$_&"));
    }

    @Test
    public void testIsValidClassifierPath()
    {
        Assert.assertTrue(BaseGitLabApi.isValidClassifierPath("meta::entity::path"));
        Assert.assertTrue(BaseGitLabApi.isValidClassifierPath("meta::pure::metamodel::function::ConcreteFunctionDefinition"));
        Assert.assertTrue(BaseGitLabApi.isValidClassifierPath("meta::entity::path::test_String_$1_10$__String_MANY_"));

        Assert.assertFalse(BaseGitLabApi.isValidClassifierPath("meta::"));
        Assert.assertFalse(BaseGitLabApi.isValidClassifierPath("meta::*"));
        Assert.assertFalse(BaseGitLabApi.isValidClassifierPath("random"));
        Assert.assertFalse(BaseGitLabApi.isValidClassifierPath("random::entity::path"));
        Assert.assertFalse(BaseGitLabApi.isValidClassifierPath("meta::$test::$function"));
        Assert.assertFalse(BaseGitLabApi.isValidClassifierPath("meta::has::other::characters::test_String_$1_10$__String_$1_*$_&"));
    }

    @Test
    public void testIsVersionTagName()
    {
        Assert.assertTrue(BaseGitLabApi.isVersionTagName("release-0.0.0"));
        Assert.assertTrue(BaseGitLabApi.isVersionTagName("release-1.2.3"));
        Assert.assertTrue(BaseGitLabApi.isVersionTagName("release-100023.24.35"));
        Assert.assertTrue(BaseGitLabApi.isVersionTagName("release-" + Integer.MAX_VALUE + "." + Integer.MAX_VALUE + "." + Integer.MAX_VALUE));

        Assert.assertFalse(BaseGitLabApi.isVersionTagName("release-" + (1L + (long) Integer.MAX_VALUE) + ".2.3"));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("release-2023.04.21"));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("release-20230421000000.0.0"));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("release--1.2.3"));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("release-1.-2.3"));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("release"));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("release-"));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("release-1"));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("release-1.2"));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("release-1.2."));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("release-1.2.3."));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("release-1.2.3.4"));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("release-1.2.3-SNAPSHOT"));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("release-1.2.3a"));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("1.2.3"));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("r-1.2.3"));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("release_1.2.3"));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("release/1.2.3"));
        Assert.assertFalse(BaseGitLabApi.isVersionTagName("release-01.02.03"));
    }

    @Test
    public void testGetWorkspaceBranchName()
    {
        Supplier<String> userNameSupplier = () -> "userName";
        Assert.assertEquals(
                "workspace/userName/wid1",
                BaseGitLabApi.getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification("wid1", WorkspaceType.USER), userNameSupplier));
        Assert.assertEquals(
                "backup/userName/wid2",
                BaseGitLabApi.getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification("wid2", WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP), userNameSupplier));
        Assert.assertEquals(
                "resolution/userName/wid3",
                BaseGitLabApi.getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification("wid3", WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION), userNameSupplier));

        Assert.assertEquals(
                "group/wid1",
                BaseGitLabApi.getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification("wid1", WorkspaceType.GROUP), userNameSupplier));
        Assert.assertEquals(
                "group-backup/wid2",
                BaseGitLabApi.getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification("wid2", WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP), userNameSupplier));
        Assert.assertEquals(
                "group-resolution/wid3",
                BaseGitLabApi.getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification("wid3", WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION), userNameSupplier));

        Assert.assertEquals(
                "patch/3.5.7/workspace/userName/wid1",
                BaseGitLabApi.getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification("wid1", WorkspaceType.USER, WorkspaceSource.patchWorkspaceSource("3.5.7")), userNameSupplier));
        Assert.assertEquals(
                "patch/0.0.1/backup/userName/wid2",
                BaseGitLabApi.getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification("wid2", WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP, WorkspaceSource.patchWorkspaceSource("0.0.1")), userNameSupplier));
        Assert.assertEquals(
                "patch/10.5.1/resolution/userName/wid3",
                BaseGitLabApi.getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification("wid3", WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION, WorkspaceSource.patchWorkspaceSource("10.5.1")), userNameSupplier));

        Assert.assertEquals(
                "patch/2.4.6/group/wid1",
                BaseGitLabApi.getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification("wid1", WorkspaceType.GROUP, WorkspaceSource.patchWorkspaceSource("2.4.6")), userNameSupplier));
        Assert.assertEquals(
                "patch/1.0.0/group-backup/wid2",
                BaseGitLabApi.getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification("wid2", WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP, WorkspaceSource.patchWorkspaceSource("1.0.0")), userNameSupplier));
        Assert.assertEquals(
                "patch/99.102.3/group-resolution/wid3",
                BaseGitLabApi.getWorkspaceBranchName(WorkspaceSpecification.newWorkspaceSpecification("wid3", WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION, WorkspaceSource.patchWorkspaceSource("99.102.3")), userNameSupplier));
    }

    @Test
    public void testParseWorkspaceBranchName()
    {
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName(null));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName(""));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("main"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("master"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("other_branch_name"));

        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("wirkspace/uid/wid"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("workspice/uid/wid"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("workspace"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("workspace/"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("workspace/wid"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("workspace/uid/wid/other"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("workspace//"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("workspace/workspace/uid/wid"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("workspace_uid_wid"));

        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("grp/wid"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("group"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("group/"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("group/wid/other"));

        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("patch/not.a.version/workspace/uid/wid"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("patch/12345678901.0.0/workspace/uid/wid"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("patch/1/workspace/uid/wid"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("patch/1.2/group/wid"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("patch/1.2.3/patch/1.2.3/group/wid"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("patch/1.2.3.4/workspace/uid/wid"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("patch/1.2.3.4.5/groupd/wid"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("patch/1_2_3/workspace/uid/wid"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("pitch/1.2.3/workspace/uid/wid"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("patch/1.2.3/workspice/uid/wid"));
        Assert.assertNull(BaseGitLabApi.parseWorkspaceBranchName("patch//workspace/uid/wid"));

        Assert.assertEquals(
                WorkspaceSpecification.newWorkspaceSpecification("wid1", WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, WorkspaceSource.projectWorkspaceSource(), "uid1"),
                BaseGitLabApi.parseWorkspaceBranchName("workspace/uid1/wid1"));
        Assert.assertEquals(
                WorkspaceSpecification.newWorkspaceSpecification("wid2", WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP, WorkspaceSource.projectWorkspaceSource(), "uid2"),
                BaseGitLabApi.parseWorkspaceBranchName("backup/uid2/wid2"));
        Assert.assertEquals(
                WorkspaceSpecification.newWorkspaceSpecification("wid3", WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION, WorkspaceSource.projectWorkspaceSource(), "uid3"),
                BaseGitLabApi.parseWorkspaceBranchName("resolution/uid3/wid3"));

        Assert.assertEquals(
                WorkspaceSpecification.newWorkspaceSpecification("wid1", WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, WorkspaceSource.projectWorkspaceSource()),
                BaseGitLabApi.parseWorkspaceBranchName("group/wid1"));
        Assert.assertEquals(
                WorkspaceSpecification.newWorkspaceSpecification("wid2", WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP, WorkspaceSource.projectWorkspaceSource()),
                BaseGitLabApi.parseWorkspaceBranchName("group-backup/wid2"));
        Assert.assertEquals(
                WorkspaceSpecification.newWorkspaceSpecification("wid3", WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION, WorkspaceSource.projectWorkspaceSource()),
                BaseGitLabApi.parseWorkspaceBranchName("group-resolution/wid3"));

        Assert.assertEquals(
                WorkspaceSpecification.newWorkspaceSpecification("wid1", WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, WorkspaceSource.patchWorkspaceSource("1.2.3"), "uid1"),
                BaseGitLabApi.parseWorkspaceBranchName("patch/1.2.3/workspace/uid1/wid1"));
        Assert.assertEquals(
                WorkspaceSpecification.newWorkspaceSpecification("wid2", WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP, WorkspaceSource.patchWorkspaceSource("4.5.6"), "uid2"),
                BaseGitLabApi.parseWorkspaceBranchName("patch/4.5.6/backup/uid2/wid2"));
        Assert.assertEquals(
                WorkspaceSpecification.newWorkspaceSpecification("wid3", WorkspaceType.USER, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION, WorkspaceSource.patchWorkspaceSource("41.25.63"), "uid3"),
                BaseGitLabApi.parseWorkspaceBranchName("patch/41.25.63/resolution/uid3/wid3"));

        Assert.assertEquals(
                WorkspaceSpecification.newWorkspaceSpecification("wid1", WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.WORKSPACE, WorkspaceSource.patchWorkspaceSource("3.2.1")),
                BaseGitLabApi.parseWorkspaceBranchName("patch/3.2.1/group/wid1"));
        Assert.assertEquals(
                WorkspaceSpecification.newWorkspaceSpecification("wid2", WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.BACKUP, WorkspaceSource.patchWorkspaceSource("0.0.2")),
                BaseGitLabApi.parseWorkspaceBranchName("patch/0.0.2/group-backup/wid2"));
        Assert.assertEquals(
                WorkspaceSpecification.newWorkspaceSpecification("wid3", WorkspaceType.GROUP, ProjectFileAccessProvider.WorkspaceAccessType.CONFLICT_RESOLUTION, WorkspaceSource.patchWorkspaceSource("9.9.9")),
                BaseGitLabApi.parseWorkspaceBranchName("patch/9.9.9/group-resolution/wid3"));
    }
}
