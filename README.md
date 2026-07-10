license-maven-plugin
====================

Fork of [mojohaus/license-maven-plugin](https://github.com/mojohaus/license-maven-plugin) with Octopus-specific extensions: Sonatype OSS Index and JFrog Xray license resolution, a Git-backed license registry, and version-range matching in override files.

## Compatibility

| Java | Maven 3.6.3 | Maven 3.8.x | Maven 3.9.x |
|------|-------------|-------------|-------------|
| 8    | ✅           | ✅           | ✅           |
| 11+  | ⚠️ (1)      | ⚠️ (1)      | ✅           |

**(1)** All goals work on Java 11+ with Maven < 3.9 **except** `update-file-header` when `licenseResolver=classpath://` is used. Maven 3.9 adds the required `--add-opens` JVM flags automatically; earlier versions do not.

**Minimum requirements:** Java 8, Maven 3.6.3.

---

## Overview

The plugin provides the following goals:

| Goal | Phase | Description |
|------|-------|-------------|
| `add-third-party` | `generate-resources` | Generates `THIRD-PARTY.txt` with all 3rd-party dependencies and their licenses |
| `aggregate-add-third-party` | `generate-resources` | Same as above but aggregates across all modules in a multi-module build |
| `download-licenses` | `package` | Downloads license text files (one per license) |
| `aggregate-download-licenses` | `package` | Same as above for multi-module builds |
| `jars-json-list` | `generate-resources` | Produces a JSON file listing all dependency JARs |
| `update-file-header` | — | Inserts or updates license headers in source files |
| `update-project-license` | `generate-resources` | Copies the project's license file to the output directory |
| `third-party-report` | — | Generates a Maven site report of dependency licenses |
| `check-file-header` | — | Verifies that source files contain the expected license header |
| `remove-file-header` | — | Removes license header blocks from source files |

By default, all output files are placed in `${project.build.directory}/generated-resources/licenses`.

---

## License registry

This fork resolves licenses from a Git repository (the **license registry**) rather than from static files.

The registry URL is **required** — the build fails with `IllegalArgumentException` if it is not set. Provide it as a JVM property or environment variable:

```
-Dlicense-registry.git-repository=<git-url>
```

or set the environment variable `license-registry.git-repository`.

Any valid Git URL is accepted (HTTPS, SSH, file://). The plugin performs a shallow clone (`--depth=1`) at the start of the build, so `git` must be on the `PATH`. The registry is cloned into a temporary directory and deleted after the build.

**Expected repository structure:**

```
licenses-whitelist.txt          # one license name per line — allowed licenses
licenses-hidden.txt             # licenses excluded from output
thirdparty-licenses.properties  # groupId--artifactId--version = license name overrides
licenses.properties             # default download URLs per license name
merges.txt                      # synonym groups; pipe-separated, one line per license
templates/                      # FreeMarker templates for THIRD-PARTY.txt
licenses/                       # license text files
```

---

## Fork-specific features

### Sonatype OSS Index integration

Enabled by default. The plugin queries the [Sonatype OSS Index](https://ossindex.sonatype.org/) API to resolve licenses for dependencies not found in the registry.

```
-Dlicense.useSonatypeProcessor=false   # disable
```

### JFrog Xray integration

Disabled by default. When enabled, the plugin queries a JFrog Artifactory/Xray instance for license information.

```
-Dlicense.useXrayProcessor=true
-DartifactoryUrl=https://your-artifactory-host
-DartifactoryAccessToken=<token>
```

### Version ranges in override files

`thirdparty-licenses.properties` in the registry supports Octopus releng version range syntax so a single entry can cover multiple versions:

```properties
# exact version
com.example--foo--1.2.3 = Apache-2.0

# version range (Octopus releng syntax)
com.example--bar--[2.0,3.0) = MIT
```

---

## Usage

### Plugin configuration

Add to your module's `pom.xml`:

```xml
<plugin>
    <groupId>org.octopusden.octopus</groupId>
    <artifactId>license-maven-plugin</artifactId>
    <version>${license-maven-plugin.version}</version>
    <configuration>
        <acceptPomPackaging>true</acceptPomPackaging>
        <excludedScopes>test,provided</excludedScopes>
        <failIfWarning>false</failIfWarning>
        <failOnMissing>${license.failOnMissing}</failOnMissing>
        <failOnBlacklist>${license.failOnBlacklist}</failOnBlacklist>
        <excludedGroups>YOUR_CORPORATE.*|javax.mail.*</excludedGroups>
        <useMissingFile>false</useMissingFile>
        <useRepositoryMissingFiles>false</useRepositoryMissingFiles>
        <licensesOutputDirectory>${license.output.directory}</licensesOutputDirectory>
        <outputDirectory>${license.output.directory}</outputDirectory>
        <skip>${license.skip}</skip>
    </configuration>
    <executions>
        <execution>
            <id>license-check</id>
            <phase>generate-resources</phase>
            <goals>
                <goal>add-third-party</goal>
                <goal>download-licenses</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

The plugin works only with artifacts declared as dependencies (including transitive). Enable it in the module that builds your distribution:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.octopusden.octopus</groupId>
            <artifactId>license-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

### Including generated files in the distribution

**maven-war-plugin:**

```xml
<plugin>
    <artifactId>maven-war-plugin</artifactId>
    <configuration>
        <webResources>
            <resource>
                <directory>${license.output.directory}</directory>
                <targetPath>${license.distribution.path}</targetPath>
                <filtering>false</filtering>
            </resource>
        </webResources>
    </configuration>
</plugin>
```

**maven-assembly-plugin** (in the assembly descriptor):

```xml
<fileSet>
    <includes>
        <include>**</include>
    </includes>
    <directory>${license.output.directory}</directory>
    <outputDirectory>${license.distribution.path}</outputDirectory>
</fileSet>
```

---

## Running the plugin

### During a build

By default the plugin is disabled. Enable it with:

```bash
mvn install -Dlicense.skip=false -Dlicense-registry.git-repository=<git-url>
```

Output lands in `${project.build.directory}/generated-resources/licenses`.

If running from an internal network without direct internet access, add a proxy:

```bash
-Dlicense.proxy=http://proxy.example.com:3128
```

### Running individual goals from the command line

**Generate THIRD-PARTY.txt — multi-module:**

```bash
mvn org.octopusden.octopus:license-maven-plugin:<VERSION>:aggregate-add-third-party \
  -Dlicense.acceptPomPackaging \
  -Dlicense.failOnBlacklist \
  -Dlicense.failOnMissing \
  -Dlicense-registry.git-repository=<git-url>
```

**Generate THIRD-PARTY.txt — single module:**

```bash
mvn org.octopusden.octopus:license-maven-plugin:<VERSION>:add-third-party \
  -Dlicense.acceptPomPackaging \
  -Dlicense.failOnBlacklist \
  -Dlicense.failOnMissing \
  -Dlicense-registry.git-repository=<git-url>
```

**Download license files — multi-module:**

```bash
mvn org.octopusden.octopus:license-maven-plugin:<VERSION>:aggregate-download-licenses \
  -Dlicense-registry.git-repository=<git-url>
```

**Download license files — single module:**

```bash
mvn org.octopusden.octopus:license-maven-plugin:<VERSION>:download-licenses \
  -Dlicense-registry.git-repository=<git-url>
```

---

## Key parameters

| Parameter | Property | Default | Description |
|-----------|----------|---------|-------------|
| `acceptPomPackaging` | `license.acceptPomPackaging` | `false` | Include POM-packaged modules |
| `failOnBlacklist` | `license.failOnBlacklist` | `false` | Fail on forbidden licenses |
| `failOnMissing` | `license.failOnMissing` | `false` | Fail on dependencies with no license |
| `skip` | `license.skip` | `true` | Disable the plugin entirely |
| `proxy` | `license.proxy` | — | HTTP proxy URL for license downloads |
| `useSonatypeProcessor` | `license.useSonatypeProcessor` | `true` | Query Sonatype OSS Index for licenses |
| `useXrayProcessor` | `license.useXrayProcessor` | `false` | Query JFrog Xray for licenses |
| `excludedScopes` | `license.excludedScopes` | — | Comma-separated scopes to skip |
| `excludedGroups` | `license.excludedGroups` | — | Regex of groupIds to exclude |
| `includedGroups` | `license.includedGroups` | — | Regex of groupIds to include |

---

## Interpreting build failures

### License cannot be resolved

```
[WARNING] License "Unknown license" used by 1 dependencies:
  - xdb6 (com.oracle:xdb6:10.2.0.4 - no url defined)
```

Determine the correct license and add an entry to `thirdparty-licenses.properties` in the registry, or replace the library.

### License is resolved but not whitelisted

```
[WARNING] License "ICU License" used by 1 dependencies:
  - ICU4J (com.ibm.icu:icu4j:57.1 - http://icu-project.org/)
```

Options:
1. If the name differs only in spelling from a known license, add a synonym line to `merges.txt`.
2. If the license is acceptable, add it to `licenses-whitelist.txt`.
3. Otherwise, replace the library.

---

## Registry file reference

| File | Purpose |
|------|---------|
| `licenses-whitelist.txt` | One license name per line — licenses that are allowed |
| `licenses-hidden.txt` | Licenses excluded from output |
| `thirdparty-licenses.properties` | `groupId--artifactId--version = license name` overrides; supports version ranges |
| `licenses.properties` | Default download URLs: `license name = URL` |
| `merges.txt` | Pipe-separated synonym groups, one line per license |
| `templates/` | FreeMarker templates for THIRD-PARTY.txt layout |
| `licenses/` | License text files referenced by download goals |
