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

package org.finos.legend.sdlc.generation.service;

import org.finos.legend.engine.plan.platform.java.JavaSourceHelper;
import org.finos.legend.sdlc.generation.GeneratorTemplate;
import org.finos.legend.sdlc.generation.JavaCodeGenerator;
import org.finos.legend.sdlc.tools.entity.EntityPaths;

abstract class AbstractServiceExecutionClassGenerator extends JavaCodeGenerator
{
    private final String packagePrefix;

    protected AbstractServiceExecutionClassGenerator(GeneratorTemplate template, String packagePrefix)
    {
        super(template, "\n");
        this.packagePrefix = validatePackageName(packagePrefix, true);
    }

    protected String getJavaPackageName(String legendPackage)
    {
        if ((legendPackage == null) || legendPackage.isEmpty())
        {
            throw new IllegalStateException("Package missing");
        }
        StringBuilder builder = appendPackagePrefixIfPresent(new StringBuilder());
        EntityPaths.forEachPathElement(legendPackage, name -> ((builder.length() == 0 ? builder : builder.append('.'))).append(JavaSourceHelper.toValidJavaIdentifier(name)));
        return builder.toString();
    }

    protected String getJavaClassName(String legendName)
    {
        if ((legendName == null) || legendName.isEmpty())
        {
            throw new IllegalStateException("Name missing");
        }
        return JavaSourceHelper.toValidJavaIdentifier(legendName);
    }

    protected StringBuilder appendPackagePrefixIfPresent(StringBuilder builder)
    {
        if (this.packagePrefix != null)
        {
            builder.append(this.packagePrefix);
        }
        return builder;
    }
}
