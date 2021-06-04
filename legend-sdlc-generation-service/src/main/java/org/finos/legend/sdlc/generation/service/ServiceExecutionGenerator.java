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

package org.finos.legend.sdlc.generation.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.dsl.service.generation.ServicePlanGenerator;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.plan.platform.PlanPlatform;
import org.finos.legend.engine.plan.platform.java.JavaSourceHelper;
import org.finos.legend.engine.protocol.pure.v1.PureProtocolObjectMapperFactory;
import org.finos.legend.engine.protocol.pure.v1.model.executionPlan.ExecutionPlan;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.pure.generated.Root_meta_pure_router_extension_RouterExtension;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import javax.lang.model.SourceVersion;

public class ServiceExecutionGenerator
{
    private final Service service;
    private final PureModel pureModel;
    private final String packagePrefix;
    private final Path javaSourceOutputDirectory;
    private final Path resourceOutputDirectory;
    private final JsonMapper objectMapper;
    private final String clientVersion;
    private final RichIterable<? extends Root_meta_pure_router_extension_RouterExtension> extensions;
    private final MutableList<PlanTransformer> transformers;

    private ServiceExecutionGenerator(Service service, PureModel pureModel, String packagePrefix, Path javaSourceOutputDirectory, Path resourceOutputDirectory, JsonMapper jsonMapper, RichIterable<? extends Root_meta_pure_router_extension_RouterExtension> extensions, MutableList<PlanTransformer> transformers, String clientVersion)
    {
        this.service = service;
        this.pureModel = pureModel;
        this.packagePrefix = canonicalizePackagePrefix(packagePrefix);
        this.javaSourceOutputDirectory = javaSourceOutputDirectory;
        this.resourceOutputDirectory = resourceOutputDirectory;
        this.objectMapper = (jsonMapper == null) ? getDefaultJsonMapper() : jsonMapper;
        this.clientVersion = clientVersion;
        this.extensions = extensions;
        this.transformers = transformers;
    }

    public ServiceExecutionGenerator(Service service, PureModel pureModel, String packagePrefix, Path javaSourceOutputDirectory, Path resourceOutputDirectory, JsonMapper jsonMapper)
    {
        this(service, pureModel, packagePrefix, javaSourceOutputDirectory, resourceOutputDirectory, jsonMapper, Lists.mutable.empty(), Lists.mutable.empty(), null);
    }

    public void generate() throws IOException
    {
        if ((this.service._package == null) || this.service._package.isEmpty())
        {
            throw new RuntimeException("Invalid service path (missing package): " + this.service.name);
        }

        // Generate plan
        ExecutionPlan plan = ServicePlanGenerator.generateServiceExecutionPlan(this.service, null, this.pureModel, this.clientVersion, PlanPlatform.JAVA, getPlanId(), this.extensions,  this.transformers);

        // Write any Java classes from the plan, then remove them from the plan
        JavaSourceHelper.writeJavaSourceFiles(this.javaSourceOutputDirectory, plan);
        JavaSourceHelper.removeJavaImplementationClasses(plan);

        // Write plan resource
        Path planResourcePath = getExecutionPlanResourcePath();
        Files.createDirectories(planResourcePath.getParent());
        try (Writer writer = Files.newBufferedWriter(planResourcePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW))
        {
            this.objectMapper.writeValue(writer, plan);
        }

        // Generate execution plan for service
        ServiceExecutionClassGenerator.GeneratedJavaClass generatedJavaClass = ServiceExecutionClassGenerator.newGenerator(this.service, this.packagePrefix, getExecutionPlanResourceName()).generate();
        Path javaClassPath = this.javaSourceOutputDirectory.resolve(getJavaSourceFileRelativePath(generatedJavaClass.getName()));
        Files.createDirectories(javaClassPath.getParent());
        try (Writer writer = Files.newBufferedWriter(javaClassPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW))
        {
            writer.write(generatedJavaClass.getCode());
        }
    }

    public static ServiceExecutionGenerator newGenerator(Service service, PureModel pureModel, String packagePrefix, Path javaSourceOutputDirectory, Path resourceOutputDirectory)
    {
        return newGenerator(service, pureModel, packagePrefix, javaSourceOutputDirectory, resourceOutputDirectory, null, Lists.mutable.empty(), Lists.mutable.empty(), null);
    }

    public static ServiceExecutionGenerator newGenerator(Service service, PureModel pureModel, String packagePrefix, Path javaSourceOutputDirectory, Path resourceOutputDirectory, JsonMapper jsonMapper, RichIterable<? extends Root_meta_pure_router_extension_RouterExtension> extensions, MutableList<PlanTransformer> transformers, String clientVersion)
    {
        return new ServiceExecutionGenerator(service, pureModel, packagePrefix, javaSourceOutputDirectory, resourceOutputDirectory, jsonMapper, extensions, transformers, clientVersion);
    }

    private Path getExecutionPlanResourcePath()
    {
        return this.resourceOutputDirectory.resolve(getExecutionPlanRelativePath(this.resourceOutputDirectory.getFileSystem().getSeparator()));
    }

    private String getExecutionPlanResourceName()
    {
        return getExecutionPlanRelativePath("/");
    }

    private String getExecutionPlanRelativePath(String separator)
    {
        StringBuilder builder = new StringBuilder("plans").append(separator);
        if (hasPackagePrefix())
        {
            appendReplacingDelimiter(builder, this.packagePrefix, ".", separator).append(separator);
        }
        return appendReplacingDelimiter(builder, this.service._package, "::", separator).append(separator).append(this.service.name).append(".json").toString();
    }

    private String getJavaSourceFileRelativePath(String javaClassName)
    {
        StringBuilder builder = new StringBuilder(javaClassName.length() + 5);
        appendReplacingDelimiter(builder, javaClassName, ".", this.javaSourceOutputDirectory.getFileSystem().getSeparator()).append(".java");
        return builder.toString();
    }

    private String getPlanId()
    {
        StringBuilder builder = new StringBuilder();
        if (hasPackagePrefix())
        {
            appendReplacingDelimiter(builder, this.packagePrefix, ".", "_").append('_');
        }
        appendReplacingDelimiter(builder, this.service._package, "::", "_").append('_').append(this.service.name);
        return JavaSourceHelper.toValidJavaIdentifier(builder.toString(), '$', true);
    }

    private boolean hasPackagePrefix()
    {
        return this.packagePrefix != null;
    }

    private static StringBuilder appendReplacingDelimiter(StringBuilder builder, String string, String delimiter, String replacement)
    {
        int index = string.indexOf(delimiter);
        if (index == -1)
        {
            return builder.append(string);
        }
        builder.append(string, 0, index).append(replacement);

        int start = index + delimiter.length();
        while ((index = string.indexOf(delimiter, start)) != -1)
        {
            builder.append(string, start, index).append(replacement);
            start = index + delimiter.length();
        }
        return builder.append(string, start, string.length());
    }

    private static JsonMapper getDefaultJsonMapper()
    {
        return PureProtocolObjectMapperFactory.withPureProtocolExtensions(JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
                .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build());
    }

    private static String canonicalizePackagePrefix(String packagePrefix)
    {
        if ((packagePrefix == null) || packagePrefix.isEmpty())
        {
            return null;
        }
        if (!SourceVersion.isName(packagePrefix))
        {
            throw new IllegalArgumentException("Invalid package prefix: \"" + packagePrefix + "\"");
        }
        return packagePrefix;
    }
}
