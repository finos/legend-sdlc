// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.test.junit.junit4.testTestSuites;

import org.finos.legend.sdlc.test.junit.pure.v1.AbstractServiceTest;
import org.junit.Assert;
import org.junit.Test;

public class TestService2 extends AbstractServiceTest
{
    @Test
    @Override
    public void testTestable()
    {
        AssertionError error = Assert.assertThrows(AssertionError.class, super::testTestable);
        String expected = "Test Failed for testTestSuites::TestService2:\n" +
                "=============================================\n" +
                "Test Failures : testSuite1.test1\n" +
                "testSuite1.test1:\n" +
                "Failed Asserts : assert1\n" +
                "assert1: Actual result does not match Expected result\n" +
                "  Expected : {\n" +
                "  \"kerberos\" : \"dummy kerberos\",\n" +
                "  \"employeeID\" : \"dummy id2\",\n" +
                "  \"title\" : \"dummy title\",\n" +
                "  \"firstName\" : \"dummy firstName\",\n" +
                "  \"lastName\" : \"dummy lastname\",\n" +
                "  \"countryCode\" : \"dummy countryCode\"\n" +
                "}\n" +
                "  Actual : {\n" +
                "  \"kerberos\" : \"dummy kerberos\",\n" +
                "  \"employeeID\" : \"dummy id\",\n" +
                "  \"title\" : \"dummy title\",\n" +
                "  \"firstName\" : \"dummy firstName\",\n" +
                "  \"lastName\" : \"dummy lastname\",\n" +
                "  \"countryCode\" : \"dummy countryCode\"\n" +
                "}";
        Assert.assertEquals(expected, error.getMessage());
    }

    @Override
    protected String getEntityPath()
    {
        return "testTestSuites::TestService2";
    }
}
