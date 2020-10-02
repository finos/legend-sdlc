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

package org.finos.legend.sdlc.domain.model.entity;

import java.util.Map;

public interface Entity
{
    String getPath();

    String getClassifierPath();

    Map<String, ?> getContent();

    static Entity newEntity(String path, String classifierPath, Map<String, ?> content)
    {
        return new Entity()
        {
            @Override
            public String getPath()
            {
                return path;
            }

            @Override
            public String getClassifierPath()
            {
                return classifierPath;
            }

            @Override
            public Map<String, ?> getContent()
            {
                return content;
            }
        };
    }
}
