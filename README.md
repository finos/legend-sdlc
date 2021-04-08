[![FINOS - Incubating](https://cdn.jsdelivr.net/gh/finos/contrib-toolbox@master/images/badge-incubating.svg)](https://finosfoundation.atlassian.net/wiki/display/FINOS/Incubating)
![legend-build](https://github.com/finos/legend-sdlc/workflows/legend-build/badge.svg)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=legend-sdlc&metric=security_rating&token=69394360757d5e1356312ddfee658a6b205e2c97)](https://sonarcloud.io/dashboard?id=legend-sdlc)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=legend-sdlc&metric=bugs&token=69394360757d5e1356312ddfee658a6b205e2c97)](https://sonarcloud.io/dashboard?id=legend-sdlc)


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

You will also need an instance of GitLab to connect to, such as [gitlab.com](https://gitlab.com). On GitLab, you will
need to create an "Application", which is used for authorization so that the SDLC Server can act on behalf of users.
See [GitLab's documentation](https://docs.gitlab.com/ee/api/oauth2.html) for general information about creating an
application in GitLab. The application will need to have "api" scope and have [http://`SDLC_SERVER`/api/auth/callback](http://127.0.0.1:7070/api/auth/callback) as a
redirect URI, where `SDLC_SERVER` is the host and possibly port needed to connect to the SDLC Server. (For testing
purposes, you can use `127.0.0.1:7070`) This redirect URI will also need to appear in your configuration file.

If you are using the GitlabClient for authentication (see the pac4j section of the configuration), you will need a
GitLab application for that as well. It will need "openid" and "profile" scopes, and will require
[http://`SDLC_SERVER`/api/pac4j/login/callback](http://127.0.0.1:7070/api/pac4j/login/callback) as a redirect URI (where, again, `SDLC_SERVER` is the host and port needed to
connect to the SDLC Server). You can either create a new application for this, or you can add these scopes and redirect
URI to your existing application. We recommend you use a single application for both purposes, as it makes the
authentication and authorization process simpler and faster.

Once you have your configuration file, you can run the server with Java 8 or later. You can use a command such as this
to start the server:

```sh
java -cp $SHADED_JAR_PATH org.finos.legend.sdlc.server.LegendSDLCServer server $CONFIG_DIR/config.yaml
```

If you want to use the shaded JAR built by `mvn install` in this project, you can get it from `legend-sdlc-server/target/legend-sdlc-server-*-shaded.jar`.
You may also include additional libraries on the classpath to add functionality extensions.

## Development setup

This application uses Maven 3.6+ and JDK 11 to build. Simply run `mvn install` to compile.

## Roadmap

Visit our [roadmap](https://github.com/finos/legend#roadmap) to know more about the upcoming features.

## Contributing

Visit Legend [Contribution Guide](https://github.com/finos/legend/blob/master/CONTRIBUTING.md) to learn how to contribute to Legend.

## License

Copyright 2020 Goldman Sachs

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)
