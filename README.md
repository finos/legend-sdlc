[![FINOS - Incubating](https://cdn.jsdelivr.net/gh/finos/contrib-toolbox@master/images/badge-incubating.svg)](https://finosfoundation.atlassian.net/wiki/display/FINOS/Incubating)
![legend-build](https://github.com/finos/legend-sdlc/workflows/legend-build/badge.svg)


# legend-sdlc

The Legend SDLC Server provides a rich REST API allowing users to safely manage metadata. Most SDLCs are file- and text-centric, but the Legend SDLC is model-centric. That is, users interact with model entities rather than with files and folders.

To this end, the Legend SDLC enables:
* Users to develop with tools designed for editing models (rather than files or code)
* Users to view changes with tools designed for viewing model-level changes (rather than text changes)
* Clients to create their own tools for their own particular use cases

## Usage example

Start by creating a configuration file (which can be JSON or YAML) based on your particular environment. Once you have that, you can start the server is with a command such as this:

```
java -cp legend-sdlc-server-0.5.0-shaded.jar org.finos.legend.sdlc.server.MetadataSDLCServer server $CONFIG_DIR/config.yaml
```

Additional libraries may be included on the classpath to add functionality extensions. Additional JVM arguments may be required depending on your needs (such as specifying a krb5.conf if you are using Kerberos authentication).

## Development setup

This application uses Maven 3.6+ and JDK 8. Simply run `mvn install` to compile.

## Roadmap

Visit [alloy.finos.org/docs/roadmap](https://alloy.finos.org/docs/roadmap) to know more about the roadmap.

## Contributing

Visit Legend [Contribution Gude](https://github.com/finos/alloy/blob/master/.github/CONTRIBUTING.md) to learn how to contribute to Legend.


## License

Copyright 2020 Goldman Sachs

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)
