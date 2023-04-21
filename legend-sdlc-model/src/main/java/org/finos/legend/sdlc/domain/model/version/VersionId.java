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

package org.finos.legend.sdlc.domain.model.version;

public abstract class VersionId implements Comparable<VersionId>
{
    protected static final char DELIMITER = '.';

    public abstract int getMajorVersion();

    public abstract int getMinorVersion();

    public abstract int getPatchVersion();

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof VersionId))
        {
            return false;
        }

        VersionId that = (VersionId)other;
        return (this.getMajorVersion() == that.getMajorVersion()) &&
                (this.getMinorVersion() == that.getMinorVersion()) &&
                (this.getPatchVersion() == that.getPatchVersion());
    }

    @Override
    public int hashCode()
    {
        return getMajorVersion() + 43 * (getMinorVersion() + (43 * getPatchVersion()));
    }

    @Override
    public int compareTo(VersionId other)
    {
        if (this == other)
        {
            return 0;
        }

        // First compare major versions
        int cmp = Integer.compare(this.getMajorVersion(), other.getMajorVersion());
        if (cmp != 0)
        {
            return cmp;
        }

        // Next compare minor versions
        cmp = Integer.compare(this.getMinorVersion(), other.getMinorVersion());
        if (cmp != 0)
        {
            return cmp;
        }

        // Finally compare patch versions
        return Integer.compare(this.getPatchVersion(), other.getPatchVersion());
    }

    @Override
    public String toString()
    {
        return appendVersionIdString(new StringBuilder("<VersionId ")).append('>').toString();
    }

    public String toVersionIdString()
    {
        return toVersionIdString(DELIMITER);
    }

    public String toVersionIdString(char delimiter)
    {
        return appendVersionIdString(new StringBuilder(), delimiter).toString();
    }

    public StringBuilder appendVersionIdString(StringBuilder builder)
    {
        return appendVersionIdString(builder, DELIMITER);
    }

    public StringBuilder appendVersionIdString(StringBuilder builder, char delimiter)
    {
        return builder.append(getMajorVersion())
                .append(delimiter)
                .append(getMinorVersion())
                .append(delimiter)
                .append(getPatchVersion());
    }

    public VersionId nextMajorVersion()
    {
        return newVersionId(getMajorVersion() + 1, 0, 0);
    }

    public VersionId nextMinorVersion()
    {
        return newVersionId(getMajorVersion(), getMinorVersion() + 1, 0);
    }

    public VersionId nextPatchVersion()
    {
        return newVersionId(getMajorVersion(), getMinorVersion(), getPatchVersion() + 1);
    }

    public static VersionId parseVersionId(String versionString)
    {
        return parseVersionId(versionString, DELIMITER);
    }

    public static VersionId parseVersionId(String versionString, char delimiter)
    {
        if (versionString == null)
        {
            throw new IllegalArgumentException("Invalid version string: null");
        }
        return parseVersionId(versionString, 0, versionString.length(), delimiter);
    }

    public static VersionId parseVersionId(String string, int start, int end)
    {
        return parseVersionId(string, start, end, DELIMITER);
    }

    public static VersionId parseVersionId(String string, int start, int end, char delimiter)
    {
        if (string == null)
        {
            throw new IllegalArgumentException("Invalid version string: null");
        }
        int firstDelimiterIndex = string.indexOf(delimiter, start);
        if ((firstDelimiterIndex <= start) || (firstDelimiterIndex >= (end - 1)))
        {
            throw new IllegalArgumentException("Invalid version string: \"" + string.substring(start, end) + '"');
        }
        int secondDelimiterIndex = string.indexOf(delimiter, firstDelimiterIndex + 1);
        if ((secondDelimiterIndex <= (firstDelimiterIndex + 1)) || (secondDelimiterIndex >= (end - 1)))
        {
            throw new IllegalArgumentException("Invalid version string: \"" + string.substring(start, end) + '"');
        }

        int majorVersion;
        int minorVersion;
        int patchVersion;
        try
        {
            majorVersion = parseVersionNumber(string, start, firstDelimiterIndex);
            minorVersion = parseVersionNumber(string, firstDelimiterIndex + 1, secondDelimiterIndex);
            patchVersion = parseVersionNumber(string, secondDelimiterIndex + 1, end);
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException("Invalid version string: \"" + string.substring(start, end) + '"', e);
        }

        return newVersionId(majorVersion, minorVersion, patchVersion);
    }

    private static int parseVersionNumber(String string, int start, int end)
    {
        if (!isValidVersionNumber(string, start, end))
        {
            throw new NumberFormatException("Invalid version number: " + string.substring(start, end));
        }
        return Integer.parseInt(string.substring(start, end));
    }

    public static boolean isValidVersionIdString(String string)
    {
        return isValidVersionIdString(string, DELIMITER);
    }

    public static boolean isValidVersionIdString(String string, char delimiter)
    {
        return (string != null) && isValidVersionIdString(string, 0, string.length(), delimiter);
    }

    public static boolean isValidVersionIdString(String string, int start, int end)
    {
        return isValidVersionIdString(string, start, end, DELIMITER);
    }

    public static boolean isValidVersionIdString(String string, int start, int end, char delimiter)
    {
        if (string == null)
        {
            return false;
        }

        int firstDelimiterIndex = string.indexOf(delimiter, start);
        if ((firstDelimiterIndex == -1) || (firstDelimiterIndex >= (end - 1)))
        {
            return false;
        }

        int secondDelimiterIndex = string.indexOf(delimiter, firstDelimiterIndex + 1);
        if ((secondDelimiterIndex <= (firstDelimiterIndex + 1)) || (secondDelimiterIndex >= (end - 1)))
        {
            return false;
        }

        return isValidVersionNumber(string, start, firstDelimiterIndex) &&
                isValidVersionNumber(string, firstDelimiterIndex + 1, secondDelimiterIndex) &&
                isValidVersionNumber(string, secondDelimiterIndex + 1, end);
    }

    private static boolean isValidVersionNumber(String string, int start, int end)
    {
        int len = end - start;
        if ((len <= 0) || (len > 10))
        {
            // must have length 1-10
            return false;
        }
        if (len == 1)
        {
            char c = string.charAt(start);
            return ('0' <= c) && (c <= '9');
        }
        if (string.charAt(start) == '0')
        {
            return false;
        }
        for (int i = start; i < end; i++)
        {
            char c = string.charAt(i);
            if ((c < '0') || (c > '9'))
            {
                return false;
            }
        }
        if (len == 10)
        {
            String maxInt = "2147483647";
            for (int i = 0; i < 10; i++)
            {
                char c = string.charAt(start + i);
                char maxC = maxInt.charAt(i);
                if (c > maxC)
                {
                    return false;
                }
                if (c < maxC)
                {
                    return true;
                }
            }
        }
        return true;
    }

    public static VersionId newVersionId(int majorVersion, int minorVersion, int patchVersion)
    {
        if (majorVersion < 0)
        {
            throw new IllegalArgumentException("Invalid major version: " + majorVersion);
        }
        if (minorVersion < 0)
        {
            throw new IllegalArgumentException("Invalid minor version: " + minorVersion);
        }
        if (patchVersion < 0)
        {
            throw new IllegalArgumentException("Invalid patch version: " + patchVersion);
        }
        return new VersionId()
        {
            @Override
            public int getMajorVersion()
            {
                return majorVersion;
            }

            @Override
            public int getMinorVersion()
            {
                return minorVersion;
            }

            @Override
            public int getPatchVersion()
            {
                return patchVersion;
            }
        };
    }
}
