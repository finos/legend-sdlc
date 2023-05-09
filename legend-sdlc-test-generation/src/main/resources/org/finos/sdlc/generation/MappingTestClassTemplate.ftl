<#--
Copyright 2023 Goldman Sachs

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
<#if packageName??>
package ${packageName};

</#if>import org.finos.legend.sdlc.test.junit.pure.v1.AbstractMappingTest;<#if legacyTestCount?? && (legacyTestCount >= 1)>
import org.junit.Test;</#if>

public class ${className} extends AbstractMappingTest
{
<#if legacyTestCount?? && (legacyTestCount >= 1)><#list 1..legacyTestCount as testNum>
    @Test
    public void test${testNum?c}() throws Exception
    {
        runTest(${testNum?c});
    }

</#list></#if>
    @Override
    protected String getEntityPath()
    {
        return "${entityPath}";
    }
}
