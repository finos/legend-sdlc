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
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Enum;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Enumeration;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.sdlc.generation.GeneratorTemplate;

class EnumerationClassGenerator extends AbstractServiceExecutionClassGenerator
{
    private static final String ENUM_VALUES_PARAM = "validEnumValues";

    private EnumerationClassGenerator(String packagePrefix)
    {
        super(GeneratorTemplate.fromResource("generation/service/ServiceParamEnumClassGenerator.ftl"), packagePrefix);
    }

    public EnumerationClassGenerator withEnumeration(Enumeration<? extends Enum> enumeration)
    {
        setPackageName(getJavaPackageName(PackageableElement.getUserPathForPackageableElement(enumeration._package())));
        setClassName(getJavaClassName(enumeration._name()));
        setParameter(ENUM_VALUES_PARAM, enumeration._values().collect(Enum::_name, Lists.mutable.empty()));
        return this;
    }

    static EnumerationClassGenerator newGenerator(String packagePrefix)
    {
        return new EnumerationClassGenerator(packagePrefix);
    }
}
