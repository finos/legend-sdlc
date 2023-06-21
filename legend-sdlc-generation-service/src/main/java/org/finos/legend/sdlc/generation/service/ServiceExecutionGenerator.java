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
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import javax.lang.model.SourceVersion;

public class ServiceExecutionGenerator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceExecutionGenerator.class);

    private final MutableList<Service> services;
    private final PureModel pureModel;
    private final String packagePrefix;
    private final Path javaSourceOutputDirectory;
    private final Path resourceOutputDirectory;
    private final JsonMapper objectMapper;
    private final String clientVersion;
    private final Iterable<? extends PlanGeneratorExtension> planGeneratorExtensions;
    private final ForkJoinPool executorService;

    private ServiceExecutionGenerator(MutableList<Service> services, PureModel pureModel, String packagePrefix, Path javaSourceOutputDirectory, Path resourceOutputDirectory, JsonMapper jsonMapper, Iterable<? extends PlanGeneratorExtension> planGeneratorExtensions, String clientVersion, ForkJoinPool executorService)
    {
        this.services = services;
        this.pureModel = pureModel;
        this.packagePrefix = packagePrefix;
        this.javaSourceOutputDirectory = javaSourceOutputDirectory;
        this.resourceOutputDirectory = resourceOutputDirectory;
        this.objectMapper = (jsonMapper == null) ? getDefaultJsonMapper() : jsonMapper;
        this.clientVersion = clientVersion;
        this.planGeneratorExtensions = (planGeneratorExtensions == null) ? Lists.immutable.empty() : planGeneratorExtensions;
        this.executorService = executorService;
    }

    @Deprecated
    public ServiceExecutionGenerator(Service service, PureModel pureModel, String packagePrefix, Path javaSourceOutputDirectory, Path resourceOutputDirectory, JsonMapper jsonMapper)
    {
        this(Lists.mutable.with(validateService(service)), pureModel, canonicalizePackagePrefix(packagePrefix), javaSourceOutputDirectory, resourceOutputDirectory, jsonMapper, null, null, null);
    }

    public void generate()
    {
        LOGGER.info("Starting generation of {} services", this.services.size());
        ExecClassNamesAndEnumerations execClassNamesAndEnums;
        if (this.executorService == null)
        {
            execClassNamesAndEnums = this.services.injectInto(null, (accumulator, service) -> ExecClassNamesAndEnumerations.merge(accumulator, generateSingle(service)));
        }
        else
        {
            execClassNamesAndEnums = this.executorService.submit(() -> this.services.parallelStream()
                            .map(this::generateSingle)
                            .reduce(ExecClassNamesAndEnumerations::merge))
                    .join()
                    .orElse(null);
        }
        if (execClassNamesAndEnums != null)
        {
            if (execClassNamesAndEnums.enumerations.notEmpty())
            {
                LOGGER.debug("Starting writing {} enumerations", execClassNamesAndEnums.enumerations.size());
                if ((this.executorService != null) && (execClassNamesAndEnums.enumerations.size() > 1))
                {
                    this.executorService.submit(() -> execClassNamesAndEnums.enumerations.parallelStream()
                            .map(e -> EnumerationClassGenerator.newGenerator(this.packagePrefix).withEnumeration(e).generate())
                            .forEach(this::writeJavaClass))
                            .join();
                }
                else
                {
                    execClassNamesAndEnums.enumerations.forEach(e -> writeJavaClass(EnumerationClassGenerator.newGenerator(this.packagePrefix).withEnumeration(e).generate()));
                }
                LOGGER.debug("Finished writing enumerations");
            }
            if (execClassNamesAndEnums.executionClassNames.notEmpty())
            {
                writeServiceProviderConfigFile(execClassNamesAndEnums.executionClassNames.sortThis());
            }
        }
        LOGGER.info("Finished generation of {} services", this.services.size());
    }

    private ExecClassNamesAndEnumerations generateSingle(Service service)
    {
        return new Single(service).generate();
    }

    private void writeExecutionPlan(String servicePath, ExecutionPlan plan)
    {
        Path filePath = getExecutionPlanResourcePath(servicePath);
        LOGGER.debug("Writing execution plan for {} to {}", servicePath, filePath);
        try
        {
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
                throw e;
            }
        }
        catch (IOException e)
        {
            LOGGER.error("Error writing {}", filePath, e);
            throw new UncheckedIOException(e);
        }
        catch (Exception e)
        {
            LOGGER.error("Error writing {}", filePath, e);
            throw e;
        }
        LOGGER.debug("Finished writing execution plan for {} to {}", servicePath, filePath);
    }

    private void writeJavaClass(GeneratedJavaCode generatedJavaClass)
    {
        Path filePath = this.javaSourceOutputDirectory.resolve(getJavaSourceFileRelativePath(generatedJavaClass.getClassName()));
        LOGGER.debug("Writing Java class to {}", filePath);
        try
        {
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
                throw e;
            }
        }
        catch (IOException e)
        {
            LOGGER.error("Error writing {}", filePath, e);
            throw new UncheckedIOException(e);
        }
        catch (Exception e)
        {
            LOGGER.error("Error writing {}", filePath, e);
            throw e;
        }
        LOGGER.debug("Finished writing {}", filePath);
    }

    private void writeServiceProviderConfigFile(ListIterable<String> serviceClassNames)
    {
        Path filePath = getServiceRunnerProviderConfigurationFilePath();
        LOGGER.debug("Writing service provider configuration {}: {}", filePath, serviceClassNames);
        try
        {
            Files.createDirectories(filePath.getParent());
            try
            {
                Files.write(filePath, serviceClassNames.makeString("", "\n", "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
                LOGGER.debug("Finished writing service provider configuration {}", filePath);
            }
            catch (FileAlreadyExistsException e)
            {
                LOGGER.debug("{} exists, updating", filePath);
                MutableSet<String> allServiceClassNames = Sets.mutable.withAll(serviceClassNames);
                try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8))
                {
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        allServiceClassNames.add(line.trim());
                    }
                }
                Files.write(filePath, allServiceClassNames.toSortedList().makeString("", "\n", "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
                LOGGER.debug("Finished updating service provider configuration {}", filePath);
            }
        }
        catch (IOException e)
        {
            LOGGER.error("Error writing service provider configuration {}", filePath, e);
            throw new UncheckedIOException(e);
        }
        catch (Exception e)
        {
            LOGGER.error("Error writing service provider configuration {}", filePath, e);
            throw e;
        }
    }

    private Path getExecutionPlanResourcePath(String servicePath)
    {
        return this.resourceOutputDirectory.resolve(getExecutionPlanRelativePath(servicePath, this.resourceOutputDirectory.getFileSystem().getSeparator()));
    }

    private Path getServiceRunnerProviderConfigurationFilePath()
    {
        String separator = this.resourceOutputDirectory.getFileSystem().getSeparator();
        String relativePath = "META-INF" + separator + "services" + separator + ServiceRunner.class.getCanonicalName();
        return this.resourceOutputDirectory.resolve(relativePath);
    }

    private String getExecutionPlanResourceName(String servicePath)
    {
        return getExecutionPlanRelativePath(servicePath, "/");
    }

    private String getExecutionPlanRelativePath(String servicePath, String separator)
    {
        StringBuilder builder = new StringBuilder("plans").append(separator);
        if (hasPackagePrefix())
        {
            appendReplacingDelimiter(builder, this.packagePrefix, ".", separator).append(separator);
        }
        return appendReplacingDelimiter(builder, servicePath, EntityPaths.PACKAGE_SEPARATOR, separator).append(".json").toString();
    }

    private String getJavaSourceFileRelativePath(String javaClassName)
    {
        return javaClassName.replace(".", this.javaSourceOutputDirectory.getFileSystem().getSeparator()) + ".java";
    }

    private String getPlanId(String servicePath)
    {
        StringBuilder builder = new StringBuilder();
        if (hasPackagePrefix())
        {
            appendReplacingDelimiter(builder, this.packagePrefix, ".", "_").append('_');
        }
        appendReplacingDelimiter(builder, servicePath, EntityPaths.PACKAGE_SEPARATOR, "_");
        return JavaSourceHelper.toValidJavaIdentifier(builder.toString(), '$', true);
    }

    private boolean hasPackagePrefix()
    {
        return this.packagePrefix != null;
    }

    private String getPackagePrefix()
    {
        return this.packagePrefix;
    }

    private PureModel getPureModel()
    {
        return this.pureModel;
    }

    private String getClientVersion()
    {
        return this.clientVersion;
    }

    private Path getJavaSourceOutputDirectory()
    {
        return this.javaSourceOutputDirectory;
    }

    private class Single
    {
        private final Service service;
        private final MutableList<Root_meta_pure_extension_Extension> extensions = Lists.mutable.empty();
        private final MutableList<PlanTransformer> transformers = Lists.mutable.empty();

        private Single(Service service)
        {
            this.service = service;
            ServiceExecutionGenerator.this.planGeneratorExtensions.forEach(ext ->
            {
                this.extensions.addAllIterable(ext.getExtraExtensions(getPureModel()));
                this.transformers.addAllIterable(ext.getExtraPlanTransformers());
            });
        }

        ExecClassNamesAndEnumerations generate()
        {
            long start = System.nanoTime();
            String servicePath = this.service.getPath();
            LOGGER.info("Starting generation for {}", servicePath);

            // Validate service parameter types and collect enumerations to generate
            ListIterable<Enumeration<? extends Enum>> enumerations = validateServiceParameterTypes();

            // Generate plan
            ExecutionPlan plan = generateExecutionPlan(servicePath);

            // Write any Java classes from the plan, then remove them from the plan
            LOGGER.debug("Writing Java source files from plan for {}", servicePath);
            JavaSourceHelper.writeJavaSourceFiles(getJavaSourceOutputDirectory(), plan);
            LOGGER.debug("Finished writing Java source files from plan for {}", servicePath);
            JavaSourceHelper.removeJavaImplementationClasses(plan);

            // Generate execution class for service
            LOGGER.debug("Starting generating main service execution class for {}", servicePath);
            GeneratedJavaCode generatedJavaClass = ServiceExecutionClassGenerator.newGenerator(getPackagePrefix())
                    .withPlanResourceName(getExecutionPlanResourceName(servicePath))
                    .withService(this.service)
                    .generate();
            LOGGER.debug("Finished generating main service execution class for {}", servicePath);

            // Write plan resource and execution class
            LOGGER.debug("Starting writing execution plan for {}", servicePath);
            writeExecutionPlan(servicePath, plan);
            LOGGER.debug("Finished writing execution plan for {}", servicePath);
            LOGGER.debug("Starting writing main service execution class for {}: {}", servicePath, generatedJavaClass.getClassName());
            writeJavaClass(generatedJavaClass);
            LOGGER.debug("Finished writing main service execution class for {}: {}", servicePath, generatedJavaClass.getClassName());

            ExecClassNamesAndEnumerations execClassNamesAndEnums = new ExecClassNamesAndEnumerations(generatedJavaClass.getClassName(), enumerations);
            long end = System.nanoTime();
            if (LOGGER.isInfoEnabled())
            {
                LOGGER.info("Finished generation for {} ({}s)", servicePath, String.format("%.9f", (end - start) / 1_000_000_000.0));
            }
            return execClassNamesAndEnums;
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
                        enumeration = getPureModel().getEnumeration(type, var.sourceInformation);
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
            String planId = getPlanId(servicePath);
            LOGGER.debug("Plan id: {}", planId);
            ExecutionPlan plan = ServicePlanGenerator.generateServiceExecutionPlan(this.service, null, getPureModel(), getClientVersion(), PlanPlatform.JAVA, planId, this.extensions, this.transformers);
            LOGGER.debug("Finished generating execution plan for {}", servicePath);
            return plan;
        }
    }

    private static class ExecClassNamesAndEnumerations
    {
        private final MutableList<String> executionClassNames;
        private final MutableSet<Enumeration<? extends Enum>> enumerations;

        private ExecClassNamesAndEnumerations(MutableList<String> executionClassNames, MutableSet<Enumeration<? extends Enum>> enumerations)
        {
            this.executionClassNames = executionClassNames;
            this.enumerations = enumerations;
        }

        private ExecClassNamesAndEnumerations(String executionClassName, Iterable<? extends Enumeration<? extends Enum>> enumerations)
        {
            this(Lists.mutable.with(executionClassName), Sets.mutable.withAll(enumerations));
        }

        static ExecClassNamesAndEnumerations merge(ExecClassNamesAndEnumerations left, ExecClassNamesAndEnumerations right)
        {
            if (left == null)
            {
                return right;
            }
            if (right == null)
            {
                return left;
            }

            if ((left.executionClassNames.size() >= right.executionClassNames.size()) && (left.enumerations.size() >= right.enumerations.size()))
            {
                left.executionClassNames.addAll(right.executionClassNames);
                left.enumerations.addAll(right.enumerations);
                return left;
            }
            if ((right.executionClassNames.size() >= left.executionClassNames.size()) && (right.enumerations.size() >= left.enumerations.size()))
            {
                right.executionClassNames.addAll(left.executionClassNames);
                right.enumerations.addAll(left.enumerations);
                return right;
            }

            MutableList<String> newExecutionClassNames = (left.executionClassNames.size() >= right.executionClassNames.size()) ?
                    left.executionClassNames.withAll(right.executionClassNames) :
                    right.executionClassNames.withAll(left.executionClassNames);
            MutableSet<Enumeration<? extends Enum>> newEnumerations = (left.enumerations.size() >= right.enumerations.size()) ?
                    left.enumerations.withAll(right.enumerations) :
                    right.enumerations.withAll(left.enumerations);
            return new ExecClassNamesAndEnumerations(newExecutionClassNames, newEnumerations);
        }
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

    private static Service validateService(Service service)
    {
        Objects.requireNonNull(service, "service may not be null");
        if ((service.name == null) || service.name.isEmpty())
        {
            throw new IllegalArgumentException("Service must have non-empty name");
        }
        if ((service._package == null) || service._package.isEmpty())
        {
            throw new IllegalArgumentException("Invalid service path (missing package): " + service.name);
        }
        return service;
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
        private final MutableList<Service> services = Lists.mutable.empty();
        private PureModel pureModel;
        private String packagePrefix;
        private Path javaSourceOutputDirectory;
        private Path resourceOutputDirectory;
        private JsonMapper jsonMapper;
        private final MutableList<PlanGeneratorExtension> planGeneratorExtensions = Lists.mutable.empty();
        private String clientVersion;
        private ForkJoinPool executorService;

        private Builder()
        {
        }

        public Builder withService(Service service)
        {
            this.services.add(validateService(service));
            return this;
        }

        public Builder withServices(Iterable<? extends Service> services)
        {
            services.forEach(this::withService);
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

        public Builder withPlanGeneratorExtension(PlanGeneratorExtension extension)
        {
            this.planGeneratorExtensions.add(Objects.requireNonNull(extension));
            return this;
        }

        public Builder withPlanGeneratorExtensions(Iterable<? extends PlanGeneratorExtension> extensions)
        {
            extensions.forEach(this::withPlanGeneratorExtension);
            return this;
        }

        public Builder withClientVersion(String clientVersion)
        {
            this.clientVersion = clientVersion;
            return this;
        }

        public Builder withExecutorService(ForkJoinPool executorService)
        {
            this.executorService = executorService;
            return this;
        }

        public ServiceExecutionGenerator build()
        {
            return new ServiceExecutionGenerator(
                    this.services,
                    Objects.requireNonNull(this.pureModel, "PureModel may not be null"),
                    this.packagePrefix,
                    Objects.requireNonNull(this.javaSourceOutputDirectory, "Java source output directory may not be null"),
                    Objects.requireNonNull(this.resourceOutputDirectory, "resource output directory may not be null"),
                    this.jsonMapper,
                    this.planGeneratorExtensions,
                    this.clientVersion,
                    this.executorService);
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
    @SuppressWarnings("unchecked")
    public static ServiceExecutionGenerator newGenerator(Service service, PureModel pureModel, String packagePrefix, Path javaSourceOutputDirectory, Path resourceOutputDirectory, JsonMapper jsonMapper, RichIterable<? extends Root_meta_pure_extension_Extension> extensions, Iterable<? extends PlanTransformer> transformers, String clientVersion)
    {
        MutableList<PlanTransformer> transformersList = (transformers instanceof MutableList) ? (MutableList<PlanTransformer>) transformers : Lists.mutable.withAll(transformers);
        return newBuilder()
                .withService(service)
                .withPureModel(pureModel)
                .withPackagePrefix(packagePrefix)
                .withOutputDirectories(javaSourceOutputDirectory, resourceOutputDirectory)
                .withJsonMapper(jsonMapper)
                .withPlanGeneratorExtension(new PlanGeneratorExtension()
                {
                    @Override
                    public MutableList<PlanTransformer> getExtraPlanTransformers()
                    {
                        return transformersList;
                    }

                    @Override
                    public RichIterable<? extends Root_meta_pure_extension_Extension> getExtraExtensions(PureModel pureModel)
                    {
                        return extensions;
                    }
                })
                .withClientVersion(clientVersion)
                .build();
    }
}
