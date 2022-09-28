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

import org.junit.Assert;
import org.junit.Test;

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
        Assert.assertFalse(BaseGitLabApi.isValidClassifierPath("meta::$test::$function"));
        Assert.assertFalse(BaseGitLabApi.isValidClassifierPath("meta::has::other::characters::test_String_$1_10$__String_$1_*$_&"));
    }
}
