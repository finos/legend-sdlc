package org.finos.legend.sdlc.entities;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.language.pure.grammar.from.PureGrammarParser;
import org.finos.legend.engine.protocol.pure.v1.PureProtocolObjectMapperFactory;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Association;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Class;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Enumeration;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Profile;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.section.SectionIndex;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.serialization.EntityTextSerializer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Map;

public class PureDomainDeserializer implements EntityTextSerializer
{
    private final PureGrammarParser pureParser = PureGrammarParser.newInstance();
    private final ObjectMapper jsonMapper = PureProtocolObjectMapperFactory.getNewObjectMapper();

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
        return false;
    }

    @Override
    public void serialize(Entity entity, Writer writer)
    {
        throw new UnsupportedOperationException();
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
    public Entity deserialize(String content) throws IOException
    {
        PureModelContextData pureModelContextData;
        try
        {
            pureModelContextData = this.pureParser.parseModel(content);
        }
        catch (EngineException e)
        {
            throw new RuntimeException(EngineException.buildPrettyErrorMessage(e.getMessage(), e.getSourceInformation(), e.getErrorType()), e);
        }
        List<PackageableElement> elements = pureModelContextData.getElements();
        if ((elements.size() != 2) || Iterate.noneSatisfy(elements, e -> e instanceof SectionIndex))
        {
            throw new RuntimeException("Unexpected parsing result (element count: " + elements.size() + ", SectionIndex present: " + Iterate.anySatisfy(elements, e -> e instanceof SectionIndex) + ")");
        }
        PackageableElement element = elements.get((elements.get(0) instanceof SectionIndex) ? 1 : 0);
        String classifierPath = getClassifierPath(element);
        String intermediateJson = this.jsonMapper.writeValueAsString(element);
        Map<String, Object> entityContent =  this.jsonMapper.readValue(intermediateJson, this.jsonMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        return Entity.newEntity(element.getPath(), classifierPath, entityContent);
    }

    private String getClassifierPath(PackageableElement element)
    {
        if (element instanceof Class)
        {
            return "meta::pure::metamodel::type::Class";
        }
        if (element instanceof Association)
        {
            return "meta::pure::metamodel::relationship::Association";
        }
        if (element instanceof Enumeration)
        {
            return "meta::pure::metamodel::type::Enumeration";
        }
        if (element instanceof Profile)
        {
            return "meta::pure::metamodel::extension::Profile";
        }
        throw new RuntimeException("Unsupported element: " + element);
    }
}
