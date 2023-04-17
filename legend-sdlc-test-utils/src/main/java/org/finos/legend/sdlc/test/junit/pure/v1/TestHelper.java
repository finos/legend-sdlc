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
// limitations under the License

package org.finos.legend.sdlc.test.junit.pure.v1;

import java.util.Objects;

abstract class TestHelper
{
    private final int junitVersion;
    protected final String entityPath;

    protected TestHelper(int junitVersion, String entityPath)
    {
        if ((junitVersion != 3) && (junitVersion != 4))
        {
            throw new IllegalArgumentException("Unsupported JUnit version: " + junitVersion);
        }
        this.junitVersion = junitVersion;
        this.entityPath = Objects.requireNonNull(entityPath);
    }

    abstract void runTest() throws Exception;

    protected void assertEquals(String message, Object expected, Object actual)
    {
        switch (this.junitVersion)
        {
            case 3:
            {
                junit.framework.Assert.assertEquals(message, expected, actual);
                break;
            }
            case 4:
            {
                org.junit.Assert.assertEquals(message, expected, actual);
                break;
            }
            default:
            {
                throw new IllegalStateException("Unsupported JUnit version: " + this.junitVersion);
            }
        }
    }

    protected void assertEquals(String message, String expected, String actual)
    {
        switch (this.junitVersion)
        {
            case 3:
            {
                junit.framework.Assert.assertEquals(message, expected, actual);
                break;
            }
            case 4:
            {
                org.junit.Assert.assertEquals(message, expected, actual);
                break;
            }
            default:
            {
                throw new IllegalStateException("Unsupported JUnit version: " + this.junitVersion);
            }
        }
    }

    protected void fail(String message)
    {
        switch (this.junitVersion)
        {
            case 3:
            {
                junit.framework.Assert.fail(message);
                break;
            }
            case 4:
            {
                org.junit.Assert.fail(message);
                break;
            }
            default:
            {
                throw new IllegalStateException("Unsupported JUnit version: " + this.junitVersion);
            }
        }
    }
}
