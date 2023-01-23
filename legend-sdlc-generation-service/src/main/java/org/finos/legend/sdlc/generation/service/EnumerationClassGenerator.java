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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Enum;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Enumeration;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;

import java.util.List;
import java.util.function.BiConsumer;

class EnumerationClassGenerator extends AbstractServiceExecutionClassGenerator
{
    private final String enumerationPackage;
    private final String enumerationName;
    private final List<String> enumValues;

    private EnumerationClassGenerator(String packagePrefix, String enumerationPackage, String enumerationName, List<String> enumValues)
    {
        super(packagePrefix);
        this.enumerationPackage = enumerationPackage;
        this.enumerationName = enumerationName;
        this.enumValues = enumValues;
    }

    @Override
    protected String getLegendPackage()
    {
        return this.enumerationPackage;
    }

    @Override
    protected String getLegendName()
    {
        return this.enumerationName;
    }

    @Override
    protected void collectTemplateParameters(BiConsumer<String, Object> consumer)
    {
        consumer.accept("validEnumValues", this.enumValues);
    }

    @Override
    protected String getTemplateResourceName()
    {
        return "generation/service/ServiceParamEnumClassGenerator.ftl";
    }

    static EnumerationClassGenerator newGenerator(String packagePrefix, Enumeration<? extends Enum> enumeration)
    {
        String packageName = PackageableElement.getUserPathForPackageableElement(enumeration._package());
        String enumerationName = enumeration._name();
        MutableList<String> values = enumeration._values().collect(Enum::_name, Lists.mutable.empty());
        return new EnumerationClassGenerator(packagePrefix, packageName, enumerationName, values);
    }
}
