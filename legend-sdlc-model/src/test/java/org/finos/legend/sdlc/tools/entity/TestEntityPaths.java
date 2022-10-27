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

package org.finos.legend.sdlc.tools.entity;

import org.junit.Assert;
import org.junit.Test;

public class TestEntityPaths
{
    @Test
    public void testIsValidEntityPath()
    {
        Assert.assertTrue(EntityPaths.isValidEntityPath("valid::entity::path"));
        Assert.assertTrue(EntityPaths.isValidEntityPath("valid::entity::path::ABCD_EFG_HIJK_LMNOP_QRS_TUV_WX_Y_Z_0123456789"));
        Assert.assertTrue(EntityPaths.isValidEntityPath("valid::entity::path::abcd$efg$hijk$lmnop$qrs$tuv$wx$y$z"));

        Assert.assertFalse(EntityPaths.isValidEntityPath(null));
        Assert.assertFalse(EntityPaths.isValidEntityPath(""));
        Assert.assertFalse(EntityPaths.isValidEntityPath("no_package"));
        Assert.assertFalse(EntityPaths.isValidEntityPath("meta::otherwise::valid::entity::path"));
        Assert.assertFalse(EntityPaths.isValidEntityPath("path::with$::dollar"));
        Assert.assertFalse(EntityPaths.isValidEntityPath("ends::with::separator::"));
        Assert.assertFalse(EntityPaths.isValidEntityPath("ends::with::star::*"));
        Assert.assertFalse(EntityPaths.isValidEntityPath("model::$test::$function"));
        Assert.assertFalse(EntityPaths.isValidEntityPath("model::has::other::characters::test_String_$1_10$__String_$1_*$_&"));
        Assert.assertFalse(EntityPaths.isValidEntityPath("model::has::other::characters::Class\u00A3WithUnicode"));
        Assert.assertFalse(EntityPaths.isValidEntityPath("model::has::other::characters::Class\u00BDWithUnicode"));
        Assert.assertFalse(EntityPaths.isValidEntityPath("model::has::other::characters::Class\u00E1WithUnicode"));
        Assert.assertFalse(EntityPaths.isValidEntityPath("model::has::other::characters::Class\u00F3WithUnicode"));
    }

    @Test
    public void testIsValidClassifierPath()
    {
        Assert.assertTrue(EntityPaths.isValidClassifierPath("meta::valid::classifier::path"));
        Assert.assertTrue(EntityPaths.isValidClassifierPath("meta::valid::classifier::path::ABCD_EFG_HIJK_LMNOP_QRS_TUV_WX_Y_Z_0123456789"));
        Assert.assertTrue(EntityPaths.isValidClassifierPath("meta::valid::classifier::path::abcd_efg_hijk_lmnop_qrs_tuv_wx_y_z"));
        Assert.assertTrue(EntityPaths.isValidClassifierPath("meta::valid::classifier::path::abcd$efg$hijk$lmnop$qrs$tuv$wx$y$z"));

        Assert.assertFalse(EntityPaths.isValidClassifierPath(null));
        Assert.assertFalse(EntityPaths.isValidClassifierPath(""));
        Assert.assertFalse(EntityPaths.isValidClassifierPath("no_package"));
        Assert.assertFalse(EntityPaths.isValidClassifierPath("does::not::start::with::meta"));
        Assert.assertFalse(EntityPaths.isValidClassifierPath("meta::path::with$::dollar"));
        Assert.assertFalse(EntityPaths.isValidClassifierPath("meta::"));
        Assert.assertFalse(EntityPaths.isValidClassifierPath("meta::*"));
        Assert.assertFalse(EntityPaths.isValidClassifierPath("random"));
        Assert.assertFalse(EntityPaths.isValidClassifierPath("random::entity::path"));
        Assert.assertFalse(EntityPaths.isValidClassifierPath("meta::$test::$function"));
        Assert.assertFalse(EntityPaths.isValidClassifierPath("meta::has::other::characters::test_String_$1_10$__String_$1_*$_&"));
        Assert.assertFalse(EntityPaths.isValidClassifierPath("meta::has::other::characters::Class\u00A3WithUnicode"));
        Assert.assertFalse(EntityPaths.isValidClassifierPath("meta::has::other::characters::Class\u00BDWithUnicode"));
        Assert.assertFalse(EntityPaths.isValidClassifierPath("meta::has::other::characters::Class\u00E1WithUnicode"));
        Assert.assertFalse(EntityPaths.isValidClassifierPath("meta::has::other::characters::Class\u00F3WithUnicode"));
    }

    @Test
    public void testIsValidPackagePath()
    {
        Assert.assertTrue(EntityPaths.isValidPackagePath("valid::package::path"));
        Assert.assertTrue(EntityPaths.isValidPackagePath("valid::package::path::ABCD_EFG_HIJK_LMNOP_QRS_TUV_WX_Y_Z_0123456789"));
        Assert.assertTrue(EntityPaths.isValidPackagePath("meta::valid::package::path"));
        Assert.assertTrue(EntityPaths.isValidPackagePath("model"));

        Assert.assertFalse(EntityPaths.isValidPackagePath(null));
        Assert.assertFalse(EntityPaths.isValidPackagePath(""));
        Assert.assertFalse(EntityPaths.isValidPackagePath("path::with$::dollar"));
        Assert.assertFalse(EntityPaths.isValidPackagePath("invalid::package::path::abcd$efg$hijk$lmnop$qrs$tuv$wx$y$z"));
        Assert.assertFalse(EntityPaths.isValidPackagePath("path::with$::dollar"));
        Assert.assertFalse(EntityPaths.isValidPackagePath("ends::with::separator::"));
        Assert.assertFalse(EntityPaths.isValidPackagePath("ends::with::star::*"));
        Assert.assertFalse(EntityPaths.isValidPackagePath("model::$test::$function"));
        Assert.assertFalse(EntityPaths.isValidPackagePath("model::has::other::characters::test_String_$1_10$__String_$1_*$_&"));
    }

    @Test
    public void testIsValidEntityName()
    {
        Assert.assertTrue(EntityPaths.isValidEntityName("valid_entity_name"));
        Assert.assertTrue(EntityPaths.isValidEntityName("validEntityName"));
        Assert.assertTrue(EntityPaths.isValidEntityName("valid_entity_name2"));
        Assert.assertTrue(EntityPaths.isValidEntityName("validEntityName3"));
        Assert.assertTrue(EntityPaths.isValidEntityName("ABCD_EFG_HIJK_LMNOP_QRS_TUV_WX_Y_Z$0123456789"));
        Assert.assertTrue(EntityPaths.isValidEntityName("abcd_efg$hijk_lmnop$qrs_tuv$wx_y$z"));
        Assert.assertTrue(EntityPaths.isValidEntityName("test_String_$1_10$__String_MANY_"));
        Assert.assertTrue(EntityPaths.isValidEntityName("test_String_$1_10$__String_$1_MANY$_"));
        Assert.assertTrue(EntityPaths.isValidEntityName("test_String_$1_MANY$__String_1_"));

        Assert.assertFalse(EntityPaths.isValidEntityName(null));
        Assert.assertFalse(EntityPaths.isValidEntityName(""));
        Assert.assertFalse(EntityPaths.isValidEntityName("bad#symbols"));
        Assert.assertFalse(EntityPaths.isValidEntityName("bad!symbols"));
        Assert.assertFalse(EntityPaths.isValidEntityName("bad_symbols&"));
        Assert.assertFalse(EntityPaths.isValidEntityName("bad_symbols_\u00A3"));
        Assert.assertFalse(EntityPaths.isValidEntityName("bad_symbols_\u00BD"));
        Assert.assertFalse(EntityPaths.isValidEntityName("bad_symbols_\u00E1"));
        Assert.assertFalse(EntityPaths.isValidEntityName("bad_symbols_\u00F3"));
        Assert.assertFalse(EntityPaths.isValidEntityName("valid::entity::path"));
        Assert.assertFalse(EntityPaths.isValidEntityName("valid::entity::path::ABCD_EFG_HIJK_LMNOP_QRS_TUV_WX_Y_Z_0123456789"));
        Assert.assertFalse(EntityPaths.isValidEntityName("valid::entity::path::abcd$efg$hijk$lmnop$qrs$tuv$wx$y$z"));
        Assert.assertFalse(EntityPaths.isValidEntityName("test_String_$1_10$__String_$1_*$_&"));
        Assert.assertFalse(EntityPaths.isValidEntityName("entity_name_has_other_characters_*#@"));
    }

    @Test
    public void testIsValidPackageName()
    {
        Assert.assertTrue(EntityPaths.isValidPackageName("valid_entity_name"));
        Assert.assertTrue(EntityPaths.isValidPackageName("ABCD_EFG_HIJK_LMNOP_QRS_TUV_WX_Y_Z_0123456789"));
        Assert.assertTrue(EntityPaths.isValidEntityName("abcd_efg_hijk_lmnop_qrs_tuv_wx_y_z"));

        Assert.assertFalse(EntityPaths.isValidPackageName(null));
        Assert.assertFalse(EntityPaths.isValidPackageName(""));
        Assert.assertFalse(EntityPaths.isValidPackageName("bad#symbols"));
        Assert.assertFalse(EntityPaths.isValidPackageName("bad!symbols"));
        Assert.assertFalse(EntityPaths.isValidPackageName("bad_symbols&"));
        Assert.assertFalse(EntityPaths.isValidPackageName("bad_symbols_\u00A3"));
        Assert.assertFalse(EntityPaths.isValidPackageName("bad_symbols_\u00BD"));
        Assert.assertFalse(EntityPaths.isValidPackageName("bad_symbols_\u00E1"));
        Assert.assertFalse(EntityPaths.isValidPackageName("bad_symbols_\u00F3"));
        Assert.assertFalse(EntityPaths.isValidPackageName("abcd$efg$hijk$lmnop$qrs$tuv$wx$y$z"));
        Assert.assertFalse(EntityPaths.isValidPackageName("valid::entity::path"));
        Assert.assertFalse(EntityPaths.isValidPackageName("valid::entity::path::ABCD_EFG_HIJK_LMNOP_QRS_TUV_WX_Y_Z_0123456789"));
        Assert.assertFalse(EntityPaths.isValidPackageName("valid::entity::path::abcd$efg$hijk$lmnop$qrs$tuv$wx$y$z"));
        Assert.assertFalse(EntityPaths.isValidPackageName("test_String_$1_10$__String_$1_*$_&"));
        Assert.assertFalse(EntityPaths.isValidPackageName("entity_name_has_other_characters_*#@"));
    }
}
