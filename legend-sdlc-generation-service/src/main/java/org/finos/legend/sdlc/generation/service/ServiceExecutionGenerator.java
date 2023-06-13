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
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.dsl.service.execution.ServiceRunner;
import org.finos.legend.engine.language.pure.dsl.service.generation.ServicePlanGenerator;
import org.finos.legend.engine.plan.generation.extension.PlanGeneratorExtension;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.plan.platform.PlanPlatform;
import org.finos.legend.engine.plan.platform.java.JavaSourceHelper;
import org.finos.legend.engine.protocol.pure.v1.PureProtocolObjectMapperFactory;
import org.finos.legend.engine.protocol.pure.v1.model.executionPlan.ExecutionPlan;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.PureExecution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;
import org.finos.legend.pure.generated.Root_meta_pure_extension_Extension;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Enum;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Enumeration;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.finos.legend.sdlc.generation.GeneratedJavaCode;
import org.finos.legend.sdlc.tools.entity.EntityPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import javax.lang.model.SourceVersion;

public class ServiceExecutionGenerator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceExecutionGenerator.class);

    private final Service service;
    private final PureModel pureModel;
    private final String packagePrefix;
    private final Path javaSourceOutputDirectory;
    private final Path resourceOutputDirectory;
    private final JsonMapper objectMapper;
    private final String clientVersion;
    private final RichIterable<? extends Root_meta_pure_extension_Extension> extensions;
    private final Iterable<? extends PlanTransformer> transformers;
    private final Object enumWriteLock;
    private final Object serviceProviderWriteLock;

    private ServiceExecutionGenerator(Service service, PureModel pureModel, String packagePrefix, Path javaSourceOutputDirectory, Path resourceOutputDirectory, JsonMapper jsonMapper, RichIterable<? extends Root_meta_pure_extension_Extension> extensions, Iterable<? extends PlanTransformer> transformers, String clientVersion, Object enumWriteLock, Object serviceProviderWriteLock)
    {
        this.service = service;
        this.pureModel = pureModel;
        this.packagePrefix = packagePrefix;
        this.javaSourceOutputDirectory = javaSourceOutputDirectory;
        this.resourceOutputDirectory = resourceOutputDirectory;
        this.objectMapper = (jsonMapper == null) ? getDefaultJsonMapper() : jsonMapper;
        this.clientVersion = clientVersion;
        this.extensions = (extensions == null) ? Lists.fixedSize.empty() : extensions;
        this.transformers = (transformers == null) ? Lists.fixedSize.empty() : transformers;
        this.enumWriteLock = (enumWriteLock == null) ? new Object() : enumWriteLock;
        this.serviceProviderWriteLock = (serviceProviderWriteLock == null) ? new Object() : serviceProviderWriteLock;
    }

    @Deprecated
    public ServiceExecutionGenerator(Service service, PureModel pureModel, String packagePrefix, Path javaSourceOutputDirectory, Path resourceOutputDirectory, JsonMapper jsonMapper)
    {
        this(service, pureModel, canonicalizePackagePrefix(packagePrefix), javaSourceOutputDirectory, resourceOutputDirectory, jsonMapper, Lists.fixedSize.empty(), Lists.fixedSize.empty(), null, null, null);
    }

    public void generate() throws IOException
    {
        if ((this.service._package == null) || this.service._package.isEmpty())
        {
            throw new RuntimeException("Invalid service path (missing package): " + this.service.name);
        }

        String servicePath = this.service.getPath();
        LOGGER.debug("Starting generation for {}", servicePath);

        // Validate service parameter types and collect enumerations to generate
        ListIterable<Enumeration<? extends Enum>> enumerations = validateServiceParameterTypes();

        // Generate plan
        ExecutionPlan plan = generateExecutionPlan(servicePath);

        // Write any Java classes from the plan, then remove them from the plan
        LOGGER.debug("Writing Java source files from plan for {}", servicePath);
        JavaSourceHelper.writeJavaSourceFiles(this.javaSourceOutputDirectory, plan);
        LOGGER.debug("Finished writing Java source files from plan for {}", servicePath);
        JavaSourceHelper.removeJavaImplementationClasses(plan);

        // Write plan resource
        writeExecutionPlan(servicePath, plan);

        // Generate Enum Classes if service takes enums as parameters
        if (enumerations.notEmpty())
        {
            LOGGER.debug("{} enumerations to write", enumerations.size());
            synchronized (this.enumWriteLock)
            {
                LOGGER.debug("Starting writing enumerations");
                for (Enumeration<? extends Enum> enumeration : enumerations)
                {
                    GeneratedJavaCode generatedJavaClass = EnumerationClassGenerator.newGenerator(this.packagePrefix)
                            .withEnumeration(enumeration)
                            .generate();
                    writeJavaClass(generatedJavaClass);
                }
                LOGGER.debug("Finished writing enumerations");
            }
        }

        // Generate execution plan for service
        LOGGER.debug("Starting writing main service execution class for {}", servicePath);
        GeneratedJavaCode generatedJavaClass = ServiceExecutionClassGenerator.newGenerator(this.packagePrefix)
                .withPlanResourceName(getExecutionPlanResourceName())
                .withService(this.service)
                .generate();
        writeJavaClass(generatedJavaClass);
        LOGGER.debug("Finished writing main service execution class for {}", servicePath);

        // Append the class reference to ServiceRunner provider-configuration file
        synchronized (this.serviceProviderWriteLock)
        {
            updateServiceProviderConfigFile(generatedJavaClass.getClassName());
        }

        LOGGER.debug("Finished generation for {}", servicePath);
    }

    private ListIterable<Enumeration<? extends Enum>> validateServiceParameterTypes()
    {
        if (!(this.service.execution instanceof PureExecution))
        {
            throw new RuntimeException("Only services with PureExecution are supported");
        }

        MutableList<Enumeration<? extends Enum>> enumerations = Lists.mutable.empty();
        MutableSet<String> enumerationPaths = Sets.mutable.empty();
        ((PureExecution) this.service.execution).func.parameters.forEach(var ->
        {
            String type = var._class;
            if (!PrimitiveUtilities.isPrimitiveTypeName(type) && !enumerationPaths.contains(type))
            {
                Enumeration<? extends Enum> enumeration;
                try
                {
                    enumeration = this.pureModel.getEnumeration(type, var.sourceInformation);
                }
                catch (EngineException e)
                {
                    throw new RuntimeException("Invalid type for parameter '" + var.name + "': " + type, e);
                }
                enumerations.add(enumeration);
                enumerationPaths.add(type);
            }
        });
        return enumerations;
    }

    private ExecutionPlan generateExecutionPlan(String servicePath)
    {
        LOGGER.debug("Starting generating execution plan for {}", servicePath);
        String planId = getPlanId();
        LOGGER.debug("Plan id: {}", planId);
        ExecutionPlan plan = ServicePlanGenerator.generateServiceExecutionPlan(this.service, null, this.pureModel, this.clientVersion, PlanPlatform.JAVA, planId, this.extensions, this.transformers);
        LOGGER.debug("Finished generating execution plan for {}", servicePath);
        return plan;
    }

    private void writeExecutionPlan(String servicePath, ExecutionPlan plan) throws IOException
    {
        Path filePath = getExecutionPlanResourcePath();
        LOGGER.debug("Writing execution plan for {} to {}", servicePath, filePath);
        Files.createDirectories(filePath.getParent());
        try (Writer writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW))
        {
            this.objectMapper.writeValue(writer, plan);
        }
        catch (FileAlreadyExistsException e)
        {
            try
            {
                if (Arrays.equals(this.objectMapper.writeValueAsBytes(plan), Files.readAllBytes(filePath)))
                {
                    // It's ok if the file already exists, as long as it has the content we want
                    LOGGER.debug("{} already exists, but content is as expected", filePath);
                    return;
                }
            }
            catch (Exception suppress)
            {
                e.addSuppressed(suppress);
            }
            LOGGER.error("Error writing {}", filePath, e);
            throw e;
        }
        catch (Exception e)
        {
            LOGGER.error("Error writing {}", filePath, e);
            throw e;
        }
        LOGGER.debug("Finished writing execution plan for {} to {}", servicePath, filePath);
    }

    private void writeJavaClass(GeneratedJavaCode generatedJavaClass) throws IOException
    {
        Path filePath = this.javaSourceOutputDirectory.resolve(getJavaSourceFileRelativePath(generatedJavaClass.getClassName()));
        LOGGER.debug("Writing Java class to {}", filePath);
        Files.createDirectories(filePath.getParent());
        try (Writer writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW))
        {
            writer.write(generatedJavaClass.getText());
        }
        catch (FileAlreadyExistsException e)
        {
            try
            {
                if (Arrays.equals(generatedJavaClass.getText().getBytes(StandardCharsets.UTF_8), Files.readAllBytes(filePath)))
                {
                    // It's ok if the file already exists, as long as it has the content we want
                    LOGGER.debug("{} already exists, but content is as expected", filePath);
                    return;
                }
            }
            catch (Exception suppress)
            {
                e.addSuppressed(suppress);
            }
            LOGGER.error("Error writing {}", filePath, e);
            throw e;
        }
        catch (Exception e)
        {
            LOGGER.error("Error writing {}", filePath, e);
            throw e;
        }
        LOGGER.debug("Finished writing {}", filePath);
    }

    private void updateServiceProviderConfigFile(String serviceClassName) throws IOException
    {
        Path filePath = getServiceRunnerProviderConfigurationFilePath();
        LOGGER.debug("Updating service provider configuration {} with {}", filePath, serviceClassName);
        if (Files.exists(filePath))
        {
            // If the file exists, read all the class names, add the new one, sort them, and write them back
            LOGGER.debug("{} exists, adding {}", filePath, serviceClassName);
            MutableList<String> lines = Lists.mutable.empty();
            try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (serviceClassName.equals(line))
                    {
                        LOGGER.debug("Service provider configuration {} already contains {}", filePath, serviceClassName);
                        // service class name is already present, no need to rewrite file
                        return;
                    }
                    if (!line.isEmpty())
                    {
                        lines.add(line);
                    }
                }
            }
            lines.add(serviceClassName);
            byte[] bytes = lines.sortThis().makeString("", "\n", "\n").getBytes(StandardCharsets.UTF_8);
            Files.write(filePath, bytes, StandardOpenOption.TRUNCATE_EXISTING);
        }
        else
        {
            LOGGER.debug("{} does not exist, creating with {}", filePath, serviceClassName);
            // If the file does not exist, write a new file with just one class name
            byte[] bytes = serviceClassName.concat("\n").getBytes(StandardCharsets.UTF_8);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, bytes);
        }
        LOGGER.debug("Finished updating service provider configuration with {}", serviceClassName);
    }

    private Path getExecutionPlanResourcePath()
    {
        return this.resourceOutputDirectory.resolve(getExecutionPlanRelativePath(this.resourceOutputDirectory.getFileSystem().getSeparator()));
    }

    private Path getServiceRunnerProviderConfigurationFilePath()
    {
        String separator = this.resourceOutputDirectory.getFileSystem().getSeparator();
        String relativePath = "META-INF" + separator + "services" + separator + ServiceRunner.class.getCanonicalName();
        return this.resourceOutputDirectory.resolve(relativePath);
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
        return appendReplacingDelimiter(builder, this.service._package, EntityPaths.PACKAGE_SEPARATOR, separator).append(separator).append(this.service.name).append(".json").toString();
    }

    private String getJavaSourceFileRelativePath(String javaClassName)
    {
        return javaClassName.replace(".", this.javaSourceOutputDirectory.getFileSystem().getSeparator()) + ".java";
    }

    private String getPlanId()
    {
        StringBuilder builder = new StringBuilder();
        if (hasPackagePrefix())
        {
            appendReplacingDelimiter(builder, this.packagePrefix, ".", "_").append('_');
        }
        appendReplacingDelimiter(builder, this.service._package, EntityPaths.PACKAGE_SEPARATOR, "_").append('_').append(this.service.name);
        return JavaSourceHelper.toValidJavaIdentifier(builder.toString(), '$', true);
    }

    private boolean hasPackagePrefix()
    {
        return this.packagePrefix != null;
    }

    private static StringBuilder appendReplacingDelimiter(StringBuilder builder, String string, String delimiter, String replacement)
    {
        if (replacement.equals(delimiter))
        {
            return builder.append(string);
        }

        int index = string.indexOf(delimiter);
        if (index == -1)
        {
            return builder.append(string);
        }

        if (replacement.length() >= delimiter.length())
        {
            builder.ensureCapacity(builder.length() + string.length());
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

    public static Builder newBuilder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private Service service;
        private PureModel pureModel;
        private String packagePrefix;
        private Path javaSourceOutputDirectory;
        private Path resourceOutputDirectory;
        private JsonMapper jsonMapper;
        private RichIterable<? extends Root_meta_pure_extension_Extension> extensions;
        private Iterable<? extends PlanTransformer> transformers;
        private String clientVersion;
        private Object enumWriteLock;
        private Object serviceProviderWriteLock;

        private Builder()
        {
        }

        public Builder withService(Service service)
        {
            this.service = service;
            return this;
        }

        public Builder withPureModel(PureModel pureModel)
        {
            this.pureModel = pureModel;
            return this;
        }

        public Builder withPackagePrefix(String packagePrefix)
        {
            this.packagePrefix = canonicalizePackagePrefix(packagePrefix);
            return this;
        }

        public Builder withJavaSourceOutputDirectory(Path directory)
        {
            this.javaSourceOutputDirectory = directory;
            return this;
        }

        public Builder withResourceOutputDirectory(Path directory)
        {
            this.resourceOutputDirectory = directory;
            return this;
        }

        public Builder withOutputDirectories(Path javaSourceOutputDirectory, Path resourceOutputDirectory)
        {
            return withJavaSourceOutputDirectory(javaSourceOutputDirectory)
                    .withResourceOutputDirectory(resourceOutputDirectory);
        }

        public Builder withJsonMapper(JsonMapper jsonMapper)
        {
            this.jsonMapper = jsonMapper;
            return this;
        }

        public Builder withRouterExtensions(RichIterable<? extends Root_meta_pure_extension_Extension> extensions)
        {
            this.extensions = extensions;
            return this;
        }

        public Builder withPlanTransformers(Iterable<? extends PlanTransformer> transformers)
        {
            this.transformers = transformers;
            return this;
        }

        public Builder withPlanGeneratorExtensions(Iterable<? extends PlanGeneratorExtension> extensions)
        {
            if (this.pureModel == null)
            {
                throw new IllegalStateException("PureModel must be set before calling this");
            }
            MutableList<Root_meta_pure_extension_Extension> routerExtensions = Lists.mutable.empty();
            MutableList<PlanTransformer> planTransformers = Lists.mutable.empty();
            extensions.forEach(ext ->
            {
                routerExtensions.addAllIterable(ext.getExtraExtensions(this.pureModel));
                planTransformers.addAllIterable(ext.getExtraPlanTransformers());
            });
            return withRouterExtensions(routerExtensions).withPlanTransformers(planTransformers);
        }

        public Builder withClientVersion(String clientVersion)
        {
            this.clientVersion = clientVersion;
            return this;
        }

        public Builder withEnumWriteLock(Object enumWriteLock)
        {
            this.enumWriteLock = enumWriteLock;
            return this;
        }

        public Builder withServiceProviderWriteLock(Object serviceProviderWriteLock)
        {
            this.serviceProviderWriteLock = serviceProviderWriteLock;
            return this;
        }

        public ServiceExecutionGenerator build()
        {
            return new ServiceExecutionGenerator(
                    Objects.requireNonNull(this.service, "Service may not be null"),
                    Objects.requireNonNull(this.pureModel, "PureModel may not be null"),
                    this.packagePrefix,
                    Objects.requireNonNull(this.javaSourceOutputDirectory, "Java source output directory may not be null"),
                    Objects.requireNonNull(this.resourceOutputDirectory, "resource output directory may not be null"),
                    this.jsonMapper,
                    this.extensions,
                    this.transformers,
                    this.clientVersion,
                    this.enumWriteLock,
                    this.serviceProviderWriteLock);
        }
    }

    @Deprecated
    public static ServiceExecutionGenerator newGenerator(Service service, PureModel pureModel, String packagePrefix, Path javaSourceOutputDirectory, Path resourceOutputDirectory)
    {
        return newBuilder()
                .withService(service)
                .withPureModel(pureModel)
                .withPackagePrefix(packagePrefix)
                .withOutputDirectories(javaSourceOutputDirectory, resourceOutputDirectory)
                .build();
    }

    @Deprecated
    public static ServiceExecutionGenerator newGenerator(Service service, PureModel pureModel, String packagePrefix, Path javaSourceOutputDirectory, Path resourceOutputDirectory, JsonMapper jsonMapper, RichIterable<? extends Root_meta_pure_extension_Extension> extensions, Iterable<? extends PlanTransformer> transformers, String clientVersion)
    {
        return newBuilder()
                .withService(service)
                .withPureModel(pureModel)
                .withPackagePrefix(packagePrefix)
                .withOutputDirectories(javaSourceOutputDirectory, resourceOutputDirectory)
                .withJsonMapper(jsonMapper)
                .withRouterExtensions(extensions)
                .withPlanTransformers(transformers)
                .withClientVersion(clientVersion)
                .build();
    }
}
