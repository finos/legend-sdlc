[![FINOS - Incubating](https://cdn.jsdelivr.net/gh/finos/contrib-toolbox@master/images/badge-incubating.svg)](https://finosfoundation.atlassian.net/wiki/display/FINOS/Incubating)
![legend-build](https://github.com/finos/legend-sdlc/workflows/legend-build/badge.svg)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=legend-pure&metric=security_rating&token=69394360757d5e1356312ddfee658a6b205e2c97)](https://sonarcloud.io/dashboard?id=legend-pure)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=legend-pure&metric=bugs&token=69394360757d5e1356312ddfee658a6b205e2c97)](https://sonarcloud.io/dashboard?id=legend-pure)


# legend-sdlc

The Legend SDLC Server provides a rich REST API allowing users to safely manage metadata. Most SDLCs are file- and
text-centric, but the Legend SDLC is model-centric. That is, users interact with model entities rather than with files
and folders.

To this end, the Legend SDLC enables:
* Users to develop with tools designed for editing models (rather than files or code)
* Users to view changes with tools designed for viewing model-level changes (rather than text changes)
* Clients to create their own tools for their own particular use cases

## Usage example

Start by creating a configuration file based on your particular environment. This can be JSON or YAML. A
[sample configuration file](https://github.com/finos/legend-sdlc/blob/master/legend-sdlc-server/src/test/resources/config-sample.yaml)
is included to help you get started. You will need to supply some information, such as the host your server is running
on.

You will also need an instance of GitLab to connect to, such as [gitlab.com](https://gitlab.com). On GitLab you will
need to create an "Application", which is used for authorization so that the SDLC Server can act on behalf of users.
See [GitLab's documentation](https://docs.gitlab.com/ee/api/oauth2.html) for more details. Information about the GitLab
instance and your application will need to be included in the configuration file.

Once you have your configuration file, you can start the server is with a command such as this:

```
java -cp legend-sdlc-server-shaded.jar org.finos.legend.sdlc.server.LegendSDLCServer server $CONFIG_DIR/config.yaml
```

You may include additional libraries on the classpath to add functionality extensions.

## Development setup

This application uses Maven 3.6+ and JDK 14 to build. Simply run `mvn install` to compile.

## Roadmap

Visit our [roadmap](https://github.com/finos/legend#roadmap) to know more about the upcoming features.

## Contributing

Visit Legend [Contribution Guide](https://github.com/finos/legend/blob/master/CONTRIBUTING.md) to learn how to contribute to Legend.


## License

Copyright 2020 Goldman Sachs

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)
