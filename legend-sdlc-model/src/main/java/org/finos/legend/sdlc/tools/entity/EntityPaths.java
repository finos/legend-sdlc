// Copyright 2022 Goldman Sachs
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

package org.finos.legend.sdlc.tools.entity;

import java.util.function.Consumer;

public class EntityPaths
{
    public static final String PACKAGE_SEPARATOR = "::";
    private static final String CLASSIFIER_PATH_START = "meta::";

    private EntityPaths()
    {
    }

    public static boolean isValidPackagePath(String string)
    {
        return (string != null) && isValidPath(string, 0, true);
    }

    public static boolean isValidEntityPath(String string)
    {
        return (string != null) &&
                !string.startsWith(CLASSIFIER_PATH_START) &&
                isValidPath(string, 0, false);
    }

    public static boolean isValidClassifierPath(String string)
    {
        return (string != null) &&
                string.startsWith(CLASSIFIER_PATH_START) &&
                isValidPath(string, CLASSIFIER_PATH_START.length(), false);
    }

    private static boolean isValidPath(String string, int start, boolean packageOnly)
    {
        if (start >= string.length())
        {
            return false;
        }

        int i = start;
        int j;
        while ((j = string.indexOf(PACKAGE_SEPARATOR, i)) != -1)
        {
            if (!isValidPackageName(string, i, j))
            {
                return false;
            }
            i = j + PACKAGE_SEPARATOR.length();
        }
        return packageOnly ?
                isValidPackageName(string, i, string.length()) :
                ((i > 0) && isValidEntityName(string, i, string.length()));
    }

    public static boolean isValidPackageName(String string)
    {
        return (string != null) && isValidPackageName(string, 0, string.length());
    }

    private static boolean isValidPackageName(String string, int start, int end)
    {
        for (int i = start; i < end; i++)
        {
            if (!isValidPackageNameChar(string.charAt(i)))
            {
                return false;
            }
        }
        return start < end;
    }

    public static boolean isValidEntityName(String seq)
    {
        return (seq != null) && isValidEntityName(seq, 0, seq.length());
    }

    private static boolean isValidEntityName(String string, int start, int end)
    {
        for (int i = start; i < end; i++)
        {
            if (!isValidEntityNameChar(string.charAt(i)))
            {
                return false;
            }
        }
        return start < end;
    }

    /**
     * Valid characters are _ and ASCII digits and letters (both lower and upper case).
     *
     * @param c character
     * @return if c is a valid package name char
     */
    private static boolean isValidPackageNameChar(char c)
    {
        return (c == '_') || ((c < 128) && Character.isLetterOrDigit(c));
    }

    /**
     * Valid characters are $, _, and ASCII digits and letters (both lower and upper case).
     *
     * @param c character
     * @return if c is a valid package name char
     */
    private static boolean isValidEntityNameChar(char c)
    {
        return (c == '$') || isValidPackageNameChar(c);
    }

    public static void forEachPathElement(String path, Consumer<? super String> consumer)
    {
        int start = 0;
        int end;
        while ((end = path.indexOf(PACKAGE_SEPARATOR, start)) != -1)
        {
            consumer.accept(path.substring(start, end));
            start = end + PACKAGE_SEPARATOR.length();
        }
        consumer.accept(path.substring(start));
    }
}
