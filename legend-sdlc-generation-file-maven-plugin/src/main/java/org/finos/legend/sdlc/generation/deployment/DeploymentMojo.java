// Copyright 2025 Goldman Sachs
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


package org.finos.legend.sdlc.generation.deployment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.collections.api.factory.Lists;
import org.finos.legend.engine.deployment.manager.DeploymentManager;
import org.finos.legend.engine.deployment.model.DeploymentExtension;
import org.finos.legend.engine.deployment.model.DeploymentExtensionLoader;
import org.finos.legend.engine.deployment.model.DeploymentResponse;
import org.finos.legend.engine.protocol.Protocol;
import org.finos.legend.engine.protocol.pure.PureClientVersions;
import org.finos.legend.engine.protocol.pure.m3.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.context.AlloySDLC;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.context.SDLC;
import org.finos.legend.engine.shared.core.ObjectMapperFactory;
import org.finos.legend.sdlc.generation.ReducedProjectConfiguration;
import org.finos.legend.sdlc.protocol.pure.v1.PureModelContextDataBuilder;
import org.finos.legend.sdlc.serialization.EntityLoader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Mojo(name = "run-deployment")
public class DeploymentMojo extends AbstractMojo
{

    private static String DEPLOY_PHASE = "Deploy";

    @Parameter
    private List<File> inclusions;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject mavenProject;

    @Parameter(property = "element")
    private String elementFilter;

    @Parameter(property = "deployment")
    private String deploymentKeyFilter;

    @Parameter(property = "deploymentPhase", defaultValue = "Deploy")
    private String deploymentPhase;

    @Parameter(property = "outputFile")
    private File outputFile;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {

        if (this.inclusions == null && this.inclusions.isEmpty())
        {
            throw new MojoExecutionException("Inclusion directory is required to run deployment");
        }
        getLog().info("include element path directories: " + this.inclusions.toString());
        getLog().info("Converting entities to Pure Model Context Data");
        PureModelContextDataBuilder pureModelContextDataBuilder = PureModelContextDataBuilder.newBuilder();
        try (EntityLoader allEntities = EntityLoader.newEntityLoader(inclusions.toArray(new File[0])))
        {
            pureModelContextDataBuilder.addEntitiesIfPossible(allEntities.getAllEntities());
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Error loading entities from model", e);
        }

        PureModelContextData model = pureModelContextDataBuilder.withSDLC(buildSDLCInfo()).withProtocol(buildProtocol()).build();
        List<DeploymentResponse> responses =  runPhase(model, new ArrayList<>(model.getElements()));
        String result;
        try
        {
            result = serializeData(responses);
        }
        catch (JsonProcessingException e)
        {
            throw new RuntimeException(e);
        }
        byte[] content = result.getBytes(StandardCharsets.UTF_8);
        if (outputFile != null)
        {
            Path filePath = this.outputFile.toPath();
            try
            {
                if (Files.exists(filePath))
                {
                    byte[] foundContent = Files.readAllBytes(filePath);
                    if (!Arrays.equals(content, foundContent))
                    {
                        throw new MojoExecutionException("Duplicate file paths found when serializing file generations outputs : '" + filePath + "'");
                    }
                    getLog().warn("Duplicate file paths found with the same content: " + filePath);
                }
                else
                {
                    Files.createDirectories(filePath.getParent());
                    Files.write(filePath, content);
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }

            getLog().info("Wrote deployment metadata to: " + outputFile);
        }
        else
        {
            getLog().info("Metadata: " + result);
        }
    }

    private String serializeData(List<DeploymentResponse> deploymentData) throws JsonProcessingException
    {
        return new ObjectMapper().writeValueAsString(deploymentData);
    }



    public List<DeploymentResponse> runPhase(PureModelContextData model, List<PackageableElement> elementList)
    {

        if (elementFilter != null && deploymentKeyFilter != null)
        {
            throw new RuntimeException("Plugin currently does not support filtering both on element and deployment key");
        }

        DeploymentManager deploymentManager = new DeploymentManager(model,  elementList);
        if (elementFilter != null)
        {
            getLog().info("Element filter set with element path: " + this.elementFilter);
            DeploymentResponse response = this.deploymentPhase.equals(DEPLOY_PHASE) ? deploymentManager.deployElement(elementFilter) : deploymentManager.validateElement(elementFilter);
            return Lists.mutable.with(response);
        }
        else if (deploymentKeyFilter != null)
        {
            DeploymentExtension extension = DeploymentExtensionLoader.extensions().stream().filter(e -> e.getKey().equals(deploymentKeyFilter)).findFirst().orElse(null);
            if (extension == null)
            {
                throw new RuntimeException("No deployment extension found for: " + deploymentKeyFilter);
            }
            if (this.deploymentPhase.equals(DEPLOY_PHASE))
            {
                return  extension.deployAll(model,  elementList);
            }
            else
            {
                if (extension.requiresValidation())
                {
                    return extension.validateAll(model,  elementList);
                }
                getLog().info("Extension " + extension.getLabel() + " does not require validation. Skipping");
                return Lists.mutable.empty();
            }
        }
        else
        {
            return this.deploymentPhase.equals(DEPLOY_PHASE) ? deploymentManager.deploy() : deploymentManager.validate();
        }

    }

    private Protocol buildProtocol()
    {
        return new Protocol("pure", PureClientVersions.production);
    }


    private SDLC buildSDLCInfo()
    {
        try
        {
            MavenProject rootMavenProject = findRootMavenProject();
            ObjectMapper mapper = ObjectMapperFactory.getNewStandardObjectMapper();
            Path baseDir = rootMavenProject.getBasedir().toPath();
            ReducedProjectConfiguration projectConfiguration = mapper.readValue(baseDir.resolve("project.json").toFile(), ReducedProjectConfiguration.class);
            AlloySDLC sdlcInfo = new AlloySDLC();
            sdlcInfo.groupId = projectConfiguration.getGroupId();
            sdlcInfo.artifactId = projectConfiguration.getArtifactId();
            sdlcInfo.version = this.mavenProject.getVersion();
            return sdlcInfo;
        }
        catch (Exception e)
        {
            getLog().warn("Unable to build SDLC info", e);
            return new AlloySDLC();
        }
    }

    private MavenProject findRootMavenProject()
    {
        MavenProject currentMavenProject = this.mavenProject;
        while (currentMavenProject.hasParent())
        {
            currentMavenProject = currentMavenProject.getParent();
        }

        return currentMavenProject;
    }

}
