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

package org.finos.legend.sdlc.test.junit;

import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.junit.Test;

public class TestServiceTestGenerator extends AbstractTestClassGeneratorTest<Service, ServiceTestGenerator>
{
    @Test
    public void testTestService()
    {
        testGeneration(
                "testTestSuites.TestTestService",
                "generated/java/testTestSuites/TestTestService.java",
                null,
                "testTestSuites::TestService");
        testGeneration(
                "org.finos.legend.sdlc.test.junit.junit4.testTestSuites.TestTestService",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestTestService.java",
                "org.finos.legend.sdlc.test.junit.junit4",
                "testTestSuites::TestService");
        testGeneration(
                "other.test.pkg.testTestSuites.TestTestService",
                "generated/java/other/test/pkg/testTestSuites/TestTestService.java",
                "other.test.pkg",
                "testTestSuites::TestService");
    }

    @Test
    public void testTestService2()
    {
        testGeneration(
                "testTestSuites.TestTestService2",
                "generated/java/testTestSuites/TestTestService2.java",
                null,
                "testTestSuites::TestService2");
        testGeneration(
                "org.finos.legend.sdlc.test.junit.junit4.testTestSuites.TestTestService2",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestTestService2.java",
                "org.finos.legend.sdlc.test.junit.junit4",
                "testTestSuites::TestService2");
        testGeneration(
                "other.test.pkg.testTestSuites.TestTestService2",
                "generated/java/other/test/pkg/testTestSuites/TestTestService2.java",
                "other.test.pkg",
                "testTestSuites::TestService2");
    }


    @Test
    public void testTestLongServiceName()
    {
        testGeneration(
                "testTestSuites.TestMyServiceIsVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryV_38d23576",
                "generated/java/testTestSuites/TestMyServiceIsVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryV_38d23576.java",
                null,
                "testTestSuites::MyServiceIsVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLoong");
        testGeneration(
                "org.finos.legend.sdlc.test.junit.junit4.testTestSuites.TestMyServiceIsVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryV_38d23576",
                "generated/java/org/finos/legend/sdlc/test/junit/junit4/testTestSuites/TestMyServiceIsVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryV_38d23576.java",
                "org.finos.legend.sdlc.test.junit.junit4",
                "testTestSuites::MyServiceIsVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLoong");

        testGeneration(
                "other.test.pkg.testTestSuites.TestMyServiceIsVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryV_38d23576",
                "generated/java/other/test/pkg/testTestSuites/TestMyServiceIsVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryV_38d23576.java",
                "other.test.pkg",
                "testTestSuites::MyServiceIsVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLoong");

    }

    @Override
    protected ServiceTestGenerator newGenerator(String packagePrefix)
    {
        return new ServiceTestGenerator(packagePrefix);
    }

    @Override
    protected ServiceTestGenerator withElement(ServiceTestGenerator generator, Service service)
    {
        return generator.withService(service);
    }
}
