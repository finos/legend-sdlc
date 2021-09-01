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

package org.finos.legend.sdlc.protocol.pure.v1;

import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.language.pure.grammar.from.PureGrammarParser;
import org.finos.legend.engine.language.pure.grammar.to.PureGrammarComposer;
import org.finos.legend.engine.language.pure.grammar.to.PureGrammarComposerContext;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.section.ImportAwareCodeSection;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.section.Section;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.section.SectionIndex;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.serialization.EntityTextSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class PureEntitySerializer implements EntityTextSerializer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PureEntitySerializer.class);

    private final PureToEntityConverter pureToEntityConverter = new PureToEntityConverter();
    private final PureGrammarParser pureParser = PureGrammarParser.newInstance();

    private final EntityToPureConverter entityToPureConverter = new EntityToPureConverter();
    private final PureGrammarComposer pureComposer = PureGrammarComposer.newInstance(PureGrammarComposerContext.Builder.newInstance()
            .withRenderStyle(PureGrammarComposerContext.RenderStyle.PRETTY)
            .build());

    @Override
    public String getName()
    {
        return "pure";
    }

    @Override
    public String getDefaultFileExtension()
    {
        return "pure";
    }

    @Override
    public boolean canSerialize(Entity entity)
    {
        if (!this.pureToEntityConverter.isSupportedClassifier(entity.getClassifierPath()))
        {
            return false;
        }

        Optional<PackageableElement> element = this.entityToPureConverter.fromEntityIfPossible(entity);
        if (!element.isPresent())
        {
            return false;
        }

        String serialized;
        try
        {
            serialized = serializeToString(element.get());
        }
        catch (Exception e)
        {
            LOGGER.warn("Unable to serialize entity \"{}\" with serializer \"{}\"", entity.getPath(), getName(), e);
            return false;
        }

        try
        {
            deserialize(serialized);
            return true;
        }
        catch (Exception e)
        {
            LOGGER.error("Unable to deserialize entity \"{}\" with serializer \"{}\" after serializing it", entity.getPath(), getName(), e);
            return false;
        }
    }

    @Override
    public void serialize(Entity entity, OutputStream stream) throws IOException
    {
        byte[] serialized = serializeToBytes(entity);
        stream.write(serialized);
    }

    @Override
    public byte[] serializeToBytes(Entity entity)
    {
        String serialized = serializeToString(entity);
        return serialized.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void serialize(Entity entity, Writer writer) throws IOException
    {
        String serialized = serializeToString(entity);
        writer.append(serialized);
    }

    @Override
    public String serializeToString(Entity entity)
    {
        PackageableElement element = this.entityToPureConverter.fromEntity(entity);
        return serializeToString(element);
    }

    private String serializeToString(PackageableElement element)
    {
        PureModelContextData pureModelContextData = PureModelContextData.newPureModelContextData(null, null, Collections.singletonList(element));
        return this.pureComposer.renderPureModelContextData(pureModelContextData);
    }

    @Override
    public Entity deserialize(Reader reader) throws IOException
    {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[8192];
        int read;
        while ((read = reader.read(buffer)) != -1)
        {
            builder.append(buffer, 0, read);
        }
        return deserialize(builder.toString());
    }

    @Override
    public Entity deserialize(String content)
    {
        PackageableElement element = deserializeToElement(content);
        return this.pureToEntityConverter.toEntity(element);
    }

    private PackageableElement deserializeToElement(String content)
    {
        PureModelContextData pureModelContextData = this.pureParser.parseModel(content);
        List<PackageableElement> elements = pureModelContextData.getElements();
        switch (elements.size())
        {
            case 0:
            {
                throw new RuntimeException("No element found");
            }
            case 1:
            {
                if (elements.get(0) instanceof SectionIndex)
                {
                    throw new RuntimeException("No element found");
                }
                return elements.get(0);
            }
            case 2:
            {
                if (elements.get(0) instanceof SectionIndex)
                {
                    validateSectionIndex((SectionIndex) elements.get(0));
                    if (elements.get(1) instanceof SectionIndex)
                    {
                        throw new RuntimeException("No element found");
                    }
                    return elements.get(1);
                }
                if (elements.get(1) instanceof SectionIndex)
                {
                    validateSectionIndex((SectionIndex) elements.get(1));
                    return elements.get(0);
                }
                throw new RuntimeException("Expected one element, found 2");
            }
            default:
            {
                int sectionIndexCount = Iterate.count(elements, e -> e instanceof SectionIndex);
                String message = (sectionIndexCount > 1) ?
                        ("Expected at most one SectionIndex, found " + sectionIndexCount) :
                        ("Expected one element, found " + (elements.size() - sectionIndexCount));
                throw new RuntimeException(message);
            }
        }
    }

    private void validateSectionIndex(SectionIndex sectionIndex)
    {
        if (sectionIndex.sections != null)
        {
            if (ListIterate.anySatisfy(sectionIndex.sections, this::hasImports))
            {
                throw new RuntimeException("Imports in Pure files are not currently supported");
            }
            int countWithElements = ListIterate.count(sectionIndex.sections, this::hasElements);
            if (countWithElements > 1)
            {
                throw new RuntimeException("Multi-section Pure files are not currently supported; found " + countWithElements + " sections with elements");
            }
        }
    }

    private boolean hasImports(Section section)
    {
        if (!(section instanceof ImportAwareCodeSection))
        {
            return false;
        }

        List<String> imports = ((ImportAwareCodeSection) section).imports;
        return (imports != null) && !imports.isEmpty();
    }

    private boolean hasElements(Section section)
    {
        return (section.elements != null) && !section.elements.isEmpty();
    }
}
