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

import java.util.Objects;

public abstract class MetamodelDependency extends Dependency implements Comparable<MetamodelDependency>
{
    public abstract String getMetamodel();

    public abstract int getVersion();

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof MetamodelDependency))
        {
            return false;
        }

        MetamodelDependency that = (MetamodelDependency)other;
        return (this.getVersion() == that.getVersion()) && Objects.equals(this.getMetamodel(), that.getMetamodel());
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(getMetamodel()) + 61 * getVersion();
    }

    @Override
    public int compareTo(MetamodelDependency other)
    {
        if (this == other)
        {
            return 0;
        }

        int cmp = comparePossiblyNull(this.getMetamodel(), other.getMetamodel());
        return (cmp == 0) ? Integer.compare(this.getVersion(), other.getVersion()) : cmp;
    }

    @Override
    public StringBuilder appendDependencyIdString(StringBuilder builder)
    {
        return builder.append(getMetamodel());
    }

    @Override
    public StringBuilder appendVersionIdString(StringBuilder builder)
    {
        return builder.append(getVersion());
    }

    public static MetamodelDependency parseMetamodelDependency(String string)
    {
        return parseMetamodelDependency(string, DEFAULT_DELIMITER);
    }

    public static MetamodelDependency parseMetamodelDependency(String string, char delimiter)
    {
        if (string == null)
        {
            throw new IllegalArgumentException("Invalid metamodel dependency string: null");
        }
        return parseMetamodelDependency(string, 0, string.length(), delimiter);
    }

    public static MetamodelDependency parseMetamodelDependency(String string, int start, int end)
    {
        return parseMetamodelDependency(string, start, end, DEFAULT_DELIMITER);
    }

    public static MetamodelDependency parseMetamodelDependency(String string, int start, int end, char delimiter)
    {
        if (string == null)
        {
            throw new IllegalArgumentException("Invalid metamodel dependency string: null");
        }

        int delimiterIndex = string.indexOf(delimiter, start);
        if ((delimiterIndex == -1) || (delimiterIndex >= end))
        {
            throw new IllegalArgumentException(new StringBuilder("Invalid metamodel dependency string: \"").append(string, start, end).append('"').toString());
        }

        String metamodel = string.substring(start, delimiterIndex);
        int version;
        try
        {
            version = Integer.parseInt(string.substring(delimiterIndex + 1, end));
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException(new StringBuilder("Invalid metamodel dependency string: \"").append(string, start, end).append('"').toString(), e);
        }
        return newMetamodelDependency(metamodel, version);
    }

    public static MetamodelDependency newMetamodelDependency(String metamodel, int version)
    {
        if (metamodel == null)
        {
            throw new IllegalArgumentException("Invalid metamodel: null");
        }
        if (version < 0)
        {
            throw new IllegalArgumentException("Invalid version: " + version);
        }
        return new MetamodelDependency()
        {
            @Override
            public String getMetamodel()
            {
                return metamodel;
            }

            @Override
            public int getVersion()
            {
                return version;
            }
        };
    }
}
