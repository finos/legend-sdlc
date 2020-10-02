[![FINOS - Incubating](https://cdn.jsdelivr.net/gh/finos/contrib-toolbox@master/images/badge-incubating.svg)](https://finosfoundation.atlassian.net/wiki/display/FINOS/Incubating)
![website build](https://github.com/finos/legend-sdlc/workflows/Docusaurus-website-build/badge.svg)

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

1. Fork it (<https://github.com/finos/legend-sdlc/fork>)
2. Create your feature branch (`git checkout -b feature/fooBar`)
3. Read our [contribution guidelines](.github/CONTRIBUTING.md) and [Community Code of Conduct](https://www.finos.org/code-of-conduct)
4. Commit your changes (`git commit -am 'Add some fooBar'`)
5. Push to the branch (`git push origin feature/fooBar`)
6. Create a new Pull Request

_NOTE:_ Commits and pull requests to FINOS repositories will only be accepted from those contributors with an active, executed Individual Contributor License Agreement (ICLA) with FINOS OR who are covered under an existing and active Corporate Contribution License Agreement (CCLA) executed with FINOS. Commits from individuals not covered under an ICLA or CCLA will be flagged and blocked by the FINOS Clabot tool. Please note that some CCLAs require individuals/employees to be explicitly named on the CCLA.

*Need an ICLA? Unsure if you are covered under an existing CCLA? Email [help@finos.org](mailto:help@finos.org)*


## License

Copyright 2020 Goldman Sachs

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)
