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

import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.sdlc.generation.GeneratorTemplate;
import org.finos.legend.sdlc.generation.JavaCodeGenerator;
import org.finos.legend.sdlc.tools.entity.EntityPaths;

import javax.lang.model.SourceVersion;

class AbstractTestClassGenerator extends JavaCodeGenerator
{
    private static final String ENTITY_PATH_PARAM = "entityPath";

    private final String packagePrefix;

    protected AbstractTestClassGenerator(GeneratorTemplate template, String packagePrefix)
    {
        super(template, "\n");
        this.packagePrefix = validatePackageName(packagePrefix, true);
    }

    protected void setElement(PackageableElement protocolElement)
    {
        setPackageName(generateTestClassPackage(protocolElement));
        setClassName(generateTestClassName(protocolElement));
        setParameter(ENTITY_PATH_PARAM, protocolElement.getPath());
    }

    private String generateTestClassPackage(PackageableElement protocolElement)
    {
        String pkg = protocolElement._package;
        if ((pkg == null) || pkg.isEmpty())
        {
            return this.packagePrefix;
        }
        StringBuilder builder = new StringBuilder();
        if (this.packagePrefix != null)
        {
            builder.append(this.packagePrefix);
        }
        EntityPaths.forEachPathElement(pkg, name -> appendJavaIdentifier((builder.length() == 0) ? builder : builder.append('.'), name));
        return builder.toString();
    }

    private String generateTestClassName(PackageableElement protocolElement)
    {
        return appendJavaIdentifier(new StringBuilder(), protocolElement.name).toString();
    }

    private StringBuilder appendJavaIdentifier(StringBuilder builder, String string)
    {
        char replacement = '$';
        if (string.isEmpty())
        {
            return builder.append(replacement);
        }
        if (SourceVersion.isKeyword(string))
        {
            return builder.append(replacement).append(string);
        }

        // Handle the first code point
        int start = 0;
        int index = 0;
        int cp = string.codePointAt(0);
        if (!Character.isJavaIdentifierStart(cp))
        {
            builder.append(replacement);
            index += Character.charCount(cp);
            if (!Character.isJavaIdentifierPart(cp))
            {
                start = index;
            }
        }

        // Handle the rest
        while (index < string.length())
        {
            cp = string.codePointAt(index);
            if (Character.isJavaIdentifierPart(cp))
            {
                index += Character.charCount(cp);
            }
            else
            {
                if (start < index)
                {
                    builder.append(string, start, index);
                }
                builder.append(replacement);
                index += Character.charCount(cp);
                start = index;
            }
        }
        if (start == 0)
        {
            builder.append(string);
        }
        else if (start < string.length())
        {
            builder.append(string, start, string.length());
        }
        return builder;
    }
}
