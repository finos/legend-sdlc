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

package org.finos.legend.sdlc.test.junit;

import org.finos.legend.sdlc.test.EntityValidator;
import org.finos.legend.sdlc.test.PathTools;
import org.junit.Assert;
import org.junit.Test;

public class TestEntityValidator
{
    @Test
    public void testEntityValidator() throws Exception
    {
        EntityValidator.ValidationReport report = EntityValidator.validateEntities(PathTools.resourceToPath("entities").getParent());
        assertEntityValidationReport(report, 27, 0);
    }

    private void assertEntityValidationReport(EntityValidator.ValidationReport report, int expectedEntityCount, int expectedViolationCount)
    {
        Assert.assertEquals(expectedEntityCount, report.getEntityCount());
        Assert.assertEquals((expectedViolationCount > 0), report.hasViolations());
        Assert.assertEquals(expectedViolationCount, report.getViolationMessages().size());
        if (expectedViolationCount == 0)
        {
            Assert.assertEquals("There are no violations", report.getFormattedViolationMessage());
        }
        else if (expectedViolationCount == 1)
        {
            Assert.assertEquals(report.getViolationMessages().get(0), report.getFormattedViolationMessage());
        }
        else
        {
            Assert.assertEquals(String.format("There are %,d violations: \n\t%s", expectedViolationCount, String.join("\n\t", report.getViolationMessages())), report.getFormattedViolationMessage());
        }
    }
}
