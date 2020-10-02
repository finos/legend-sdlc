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

package org.finos.legend.sdlc.server.gitlab.mode;

public enum GitLabMode
{
    PROD, UAT;

    public static GitLabMode getMode(String string, boolean caseSensitive)
    {
        return getMode(string, caseSensitive, 0, (string == null) ? 1 : string.length());
    }

    public static GitLabMode getMode(String string, boolean caseSensitive, int start, int end)
    {
        if (string == null)
        {
            throw new IllegalArgumentException("string may not be null");
        }
        int length = end - start;
        GitLabMode[] modes = values();
        for (int i = 0, size = modes.length; i < size; i++)
        {
            GitLabMode mode = modes[i];
            String modeName = mode.name();
            if ((modeName.length() == length) && string.regionMatches(!caseSensitive, start, modeName, 0, length))
            {
                return mode;
            }
        }
        throw new IllegalArgumentException(new StringBuilder("Unknown mode: ").append(string, start, end).toString());
    }
}
