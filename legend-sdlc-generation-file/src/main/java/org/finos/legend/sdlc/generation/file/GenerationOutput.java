// Copyright 2021 Goldman Sachs
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

package org.finos.legend.sdlc.generation.file;

import org.apache.commons.text.StringEscapeUtils;
import org.finos.legend.engine.shared.core.ObjectMapperFactory;

import java.io.IOException;
import java.util.Objects;

public class GenerationOutput
{
    private final String content;
    private final String fileName;
    private final String format;

    public GenerationOutput(String content, String fileName, String format)
    {
        this.content = content;
        this.fileName = removeIllegalCharactersInFileName(fileName);
        this.format = format;
    }

    private String removeIllegalCharactersInFileName(String fileName)
    {
        return fileName.replaceAll("\\s+|:", "_");
    }

    public String getContent()
    {
        return content;
    }

    public String getFileName()
    {
        return fileName;
    }

    public String extractFileContent() throws IOException
    {
        if (this.format.equals("json"))
        {
            return ObjectMapperFactory.getNewStandardObjectMapper().readTree(this.content).toPrettyString();
        }
        return StringEscapeUtils.unescapeJava(this.content);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        GenerationOutput that = (GenerationOutput) o;
        return Objects.equals(getContent(), that.getContent()) &&
                Objects.equals(getFileName(), that.getFileName());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getContent(), getFileName());
    }
}

