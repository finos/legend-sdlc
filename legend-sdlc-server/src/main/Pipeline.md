### Project Structure and Pipeline Configuration

## Project Structure

The GitLab project under Legend SDLC supervision has the following structure in project.json file located
in root project directory:

```json
{
  "artifactId": "project",
  "groupId": "org.finos.model",
  "projectDependencies": [],
  "projectId": "PREFIX-1",
  "platformConfigurations": [],
  "projectStructureVersion": {
    "version": 2,
    "extensionVersion": 3
  },
  "projectType": "MANAGED"
}
```
which have the following meaning:
- projectId - project id in gitlab, with SDLC prefix (projectIdPrefix of gitLab config yaml server configuration)
- groupId - group of generated artifacts
- artifactId - parent artifact name, and prefix of module artifacts (entities, versioned-entities, file-generation and service-execution)
- projectStructureVersion
  - version - defines directory layout, supported artifact types, entities serializers, pom.xml templates, etc. 
  - extensionVersion - optional - defines additional files generated (requires extensionProvider of projectStructure config yaml server configuration) 
- projectType - MANAGED (default) or EMBEDDED

# Project Types
Default project type is MANAGED, and it means that during project version update or modification of project dependencies, pom.xml files are going to be generated.

If extensionVersion is defined extensionProvider is going to be used to generate additional files - for example the .gitlab-ci.yml file.

EMBEDDED project type means that expected directory layout, and entities serializers are used as specified as in version, but no pom.xml files are generated for modules.

In case of EMBEDDED project types, cannot specify extensionVersion - which means that additional files like .gitlab-ci.yml are not going to be also generated, even if SDLC Server has extensionProvider configuration.

Another constrain of EMBEDDED projects is restricting SDLC server release capability.

**Project Conversion:**

Projects can be converted through Workspace configuration.

* MANAGED to EMBEDDED
  * automatically drop extensions
  * if extensions are specified in conversion request give error
* EMBEDDED to MANAGED
  * automatically add latest extensions if not specified
  * if extension are specified allow them if valid

## Pipeline Configuration
By default, for MANAGED projects SDLC server will maintain pom.xml files, however to have an automatic generation of pipeline (.gitlab-ci.yml file) extensionProvider need to be provided.
Please create own version of FinosGitlabProjectStructureExtensionProvider for this propose.
