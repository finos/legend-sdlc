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

package org.finos.legend.sdlc.generation;

import javax.lang.model.SourceVersion;

public abstract class JavaCodeGenerator extends TextGenerator<GeneratedJavaCode>
{
    private static final String PACKAGE_NAME_PARAM = "packageName";
    private static final String CLASS_NAME_PARAM = "className";

    protected JavaCodeGenerator(GeneratorTemplate template, String defaultLineBreak)
    {
        super(template, defaultLineBreak);
    }

    protected JavaCodeGenerator(GeneratorTemplate template)
    {
        super(template);
    }

    protected void setPackageName(String packageName)
    {
        setParameter(PACKAGE_NAME_PARAM, validatePackageName(packageName));
    }

    protected String getPackageName()
    {
        return getParameter(PACKAGE_NAME_PARAM);
    }

    protected void setClassName(String className)
    {
        setParameter(CLASS_NAME_PARAM, validateClassName(className));
    }

    protected String getClassName()
    {
        return getParameter(CLASS_NAME_PARAM);
    }

    @Override
    protected GeneratedJavaCode newGeneratedText(String text)
    {
        String className = getPackageName() + "." + getClassName();
        return new GeneratedJavaCode()
        {
            @Override
            public String getClassName()
            {
                return className;
            }

            @Override
            public String getText()
            {
                return text;
            }
        };
    }

    protected static String validatePackageName(String packageName)
    {
        return validatePackageName(packageName, false);
    }

    protected static String validatePackageName(String packageName, boolean allowNull)
    {
        if ((packageName == null) ? !allowNull : !SourceVersion.isName(packageName))
        {
            throw new IllegalArgumentException("Invalid package name: " + ((packageName == null) ? null : ('"' + packageName + '"')));
        }
        return packageName;
    }

    protected static String validateClassName(String className)
    {
        if ((className == null) || SourceVersion.isKeyword(className) || !SourceVersion.isIdentifier(className))
        {
            throw new IllegalArgumentException("Invalid class name: " + ((className == null) ? null : ('"' + className + '"')));
        }
        return className;
    }
}
