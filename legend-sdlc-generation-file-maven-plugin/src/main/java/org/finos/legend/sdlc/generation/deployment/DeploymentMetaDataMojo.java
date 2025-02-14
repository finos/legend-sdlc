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
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.finos.legend.engine.deployment.model.DeploymentExtensionLoader;
import org.finos.legend.sdlc.serialization.EntityLoader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


@Mojo(name = "deployment-metadata")
public class DeploymentMetaDataMojo extends AbstractMojo
{

    @Parameter
    private List<File> inclusions;

    @Parameter(property = "outputType", defaultValue = "text")
    private String outputType;


    @Parameter(property = "includeElements", defaultValue = "false")
    private boolean includeElements;

    @Parameter(property = "outputFile")
    private File outputFile;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {

        DeploymentMetadata deploymentData = new DeploymentMetadata();
        DeploymentExtensionLoader.getExtensionsMetadata().forEach(res -> deploymentData.extensionMetadata.add(new DeploymentExtensionInfo(res.key, res.classifierPaths)));
        if (this.includeElements && !deploymentData.extensionMetadata.isEmpty())
        {
            if (this.inclusions == null)
            {
                throw new MojoExecutionException("Inclusions directory required when exporting deployable elements");
            }

            try
            {
                Map<String, List<String>> classifierToPathsMap = this.buildEntityClassiferMap();
                deploymentData.extensionMetadata.forEach(data -> data.setElements(extensionElements(classifierToPathsMap, data)));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        String result;
        try
        {
            result = serializeData(deploymentData);
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

            getLog().info("Wrote depployment metadata to: " + outputFile);
        }
        else
        {
            getLog().info("Metadata: " + result);
        }

    }

    private String serializeData(DeploymentMetadata deploymentData) throws JsonProcessingException
    {
        return new ObjectMapper().writeValueAsString(deploymentData);
    }

    private List<String> extensionElements(Map<String, List<String>> classifierToPathsMap, DeploymentExtensionInfo info)
    {
        return info.classifierPaths.stream().map(e -> classifierToPathsMap.getOrDefault(e, Lists.mutable.empty())).flatMap(Collection::stream).collect(Collectors.toList());
    }

    private Map<String, List<String>> buildEntityClassiferMap() throws Exception
    {
        Map<String, List<String>> classifierToElementPathMap = Maps.mutable.empty();
        try (EntityLoader directoriesLoader = EntityLoader.newEntityLoader(inclusions.toArray(new File[0])))
        {
             directoriesLoader.getAllEntities().forEach(entity ->
             {
                 if (classifierToElementPathMap.containsKey(entity.getClassifierPath()))
                 {
                     classifierToElementPathMap.get(entity.getClassifierPath()).add(entity.getPath());
                 }
                 else
                 {
                     classifierToElementPathMap.put(entity.getClassifierPath(), Lists.mutable.with(entity.getPath()));
                 }
             });
        }
        return classifierToElementPathMap;
    }

}
