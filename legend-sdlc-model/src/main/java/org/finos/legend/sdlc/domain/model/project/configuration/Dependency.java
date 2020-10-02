// Copyright 2020 Goldman Sachs
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

package org.finos.legend.sdlc.domain.model.project.configuration;

public abstract class Dependency
{
    protected static final char DEFAULT_DELIMITER = ':';

    @Override
    public String toString()
    {
        return appendDependencyString(new StringBuilder().append('<').append(getClass().getSimpleName()).append(' ')).append('>').toString();
    }

    public String toDependencyString()
    {
        return toDependencyString(DEFAULT_DELIMITER);
    }

    public String toDependencyString(char delimiter)
    {
        return appendDependencyString(new StringBuilder(), delimiter).toString();
    }

    public StringBuilder appendDependencyString(StringBuilder builder)
    {
        return appendDependencyString(builder, DEFAULT_DELIMITER);
    }

    public StringBuilder appendDependencyString(StringBuilder builder, char delimiter)
    {
        return appendVersionIdString(appendDependencyIdString(builder).append(delimiter));
    }

    public abstract StringBuilder appendDependencyIdString(StringBuilder builder);

    public abstract StringBuilder appendVersionIdString(StringBuilder builder);

    static <T extends Comparable<? super T>> int comparePossiblyNull(T obj1, T obj2)
    {
        return (obj1 == obj2) ? 0 : ((obj1 == null) ? 1 : ((obj2 == null) ? -1 : obj1.compareTo(obj2)));
    }
}
