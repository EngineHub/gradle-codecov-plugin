gradle-codecov-plugin
=====================

![Current Version](https://img.shields.io/maven-metadata/v?metadataUrl=http%3A%2F%2Fmaven.enginehub.org%2Frepo%2Forg%2Fenginehub%2Fgradle%2Fgradle-codecov-plugin%2Fmaven-metadata.xml&style=flat-square)

Gradle plugin to download + run [codecov-exe].

Available from the EngineHub Maven repository: https://maven.enginehub.org/repo/

If you add that repository to the `pluginMangement` block, you can use the following
to apply the plugin:

```gradle
plugins {
    id("org.enginehub.codecov") version "<version>"
}
```

## Usage
`CodecovExtension` is registered under the name `codecov`, and has the following properties:

### `version`
The version of [codecov-exe] to use. A default version is provided.

### `token`
The upload token. Pulled from the environment variable `CODECOV_TOKEN` by default.

### `reportTask`
Must be provided. Points to the `JacocoReport` task who's XML report should be used.

You must enable the XML report manually.

[codecov-exe]: https://github.com/codecov/codecov-exe

