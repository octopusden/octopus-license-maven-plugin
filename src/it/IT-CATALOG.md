# Invoker IT Catalog

## Legend

| CI Status | Meaning |
|-----------|---------|
| ✅ runs | Included in `all-integration-test` invoker execution |
| 🔄 offline | Included in the separate `integration-test-offline` execution (Maven `-o` flag) |
| ❌ live-url | Excluded — requires a live remote URL that is not available in GitHub CI |
| ❌ no-xml | Excluded — asserts `licenses.xml` which the Octopus fork no longer generates |
| ❌ local-path | Excluded — dependency declares a `${project.basedir}/...` local-file license URL, not supported via the registry |

---

## `add-third-party` goal

| IT | CI | Description |
|----|----|-------------|
| `add-third-party-no-deps` | ✅ | Project with zero dependencies produces a THIRD-PARTY.txt with the "no dependencies" message |
| `add-third-party-no-encoding` | ✅ | Missing `<project.build.sourceEncoding>` logs a platform-encoding warning but still produces a valid THIRD-PARTY.txt |
| `add-third-party-with-deps` | ✅ | Standard project with compile dependencies writes correct Apache 2.0 entries into a custom-named THIRD-PARTY file and its META-INF copy |
| `add-third-party-excluded-included` | ✅ | `excludedScopes`, `excludedGroups`, and `includedScopes` filters correctly include/exclude dependency groups |
| `add-third-party-global-db` | ✅ | License resolved from a global-db artifact is written into a child module's THIRD-PARTY.txt |
| `add-third-party-merge-licenses` | ✅ | Multiple license name variants (e.g. "Apache License 2.0", "Apache Public License 2.0") are merged to a single canonical name |
| `add-third-party-missing-pom` | ✅ | Dependency whose POM is absent is listed as "Unknown license" instead of failing the build |

---

## `aggregate-add-third-party` goal

| IT | CI | Description |
|----|----|-------------|
| `aggregate-add-third-party-simple` | ✅ | Basic multi-module aggregation produces one root THIRD-PARTY.txt; child modules produce no individual files |
| `aggregate-add-third-party-bad-license` | ✅ | Child module with no license declaration is listed as "Unknown license" in the aggregated THIRD-PARTY.txt |
| `aggregate-add-third-party-exclude-transitive-deps` | ✅ | Transitive dependencies are excluded from the aggregated THIRD-PARTY.txt when `excludeTransitiveDependencies` is set |
| `aggregate-add-third-party-global-db` | ✅ | License resolved via a global-db artifact (spring-oxm) is correctly emitted in the aggregated THIRD-PARTY.txt |
| `aggregate-add-third-party-multimodule-filters` | ✅ | Per-module include/exclude group filters in a multi-module build produce the expected subset of dependencies |
| `MLICENSE-2` | ✅ | `add-third-party` generates a `THIRD-PARTY.properties` missing-file per child module so the user can fill in unknown licenses |
| `MLICENSE-21` | ✅ | A license defined in a sibling child's `THIRD-PARTY.properties` is propagated to the consuming child's THIRD-PARTY.txt |
| `MLICENSE-23` | ✅ | Regression for MLICENSE-23 (since MLICENSE-64 the original bug no longer applies); goal runs without error using `useMissingFile` |
| `MLICENSE-25` | ✅ | `acceptPomPackaging=false` skips POM-type sub-modules; `acceptPomPackaging=true` with a separate filename includes them |
| `MLICENSE-28` | ✅ | Aggregated THIRD-PARTY.txt is produced only at root level, not duplicated in child output directories |
| `MLICENSE-36` | ✅ | POM-packaged project with no resolvable dependencies outputs the "no dependencies" message without failing |
| `MLICENSE-53` | ✅ | `includedLicenses` and `excludedLicenses` (both pipe-separated string and list-element forms) filter THIRD-PARTY.txt correctly |
| `MLICENSE-71` | ✅ | Missing license entries with `useMissingFile=true` and `useRepositoryMissingFiles=false` cause the build to fail |
| `PR-64-aggregate-unknown-license` | ✅ | License defined inline in the project POM's `<licenses>` section (not in any registry) is labelled correctly in the aggregated THIRD-PARTY.txt |
| `ISSUE-59` | ✅ | "Public Domain" SPDX identifier is correctly written into THIRD-PARTY.txt for `aopalliance:aopalliance:1.0` |

---

## Whitelist / blacklist enforcement

| IT | CI | Description |
|----|----|-------------|
| `dual-licensed-both-whitelisted` | ✅ | Dependency with two licenses both on the whitelist → build succeeds |
| `dual-licensed-one-whitelisted` | ✅ | Dependency with two licenses where at least one is whitelisted → build succeeds |
| `dual-licensed-none-whitelisted` | ✅ | Dependency with two licenses neither of which is whitelisted → build fails |
| `dual-licensed-blacklist` | ✅ | Dependency with one license on the blacklist → build fails |
| `MLICENSE-37-whitelist-success` | ✅ | License present on the `includedLicenses` whitelist → build succeeds |
| `MLICENSE-37-whitelist-fail` | ✅ | License absent from the `includedLicenses` whitelist → build fails |
| `MLICENSE-37-blacklist-success` | ✅ | License not on the `excludedLicenses` blacklist → build succeeds |
| `MLICENSE-37-blacklist-fail` | ✅ | License on the `excludedLicenses` blacklist → build fails |

---

## `download-licenses` goal

| IT | CI | Description |
|----|----|-------------|
| `download-licenses-configured` | ❌ no-xml | `licensesConfigFile` with pre-configured license URLs downloads correct license texts; complex setup not fully ported from upstream |
| `download-licenses-basic` | ❌ live-url | Downloads actual license files from the internet and asserts downloaded files exist |
| `download-licenses-configured-alt-location` | ❌ no-xml | Custom `licensesOutputDirectory` and `licensesOutputFile` redirect downloads to custom paths; also asserts `licenses.xml` |
| `download-licenses-force` | ❌ live-url | Force re-download of already-cached license files |
| `download-licenses-proxy` | ❌ live-url | Downloads license files through an HTTP proxy |
| `MLICENSE-4` | ❌ no-xml | `download-licenses` in Maven offline mode (`-o`) using only locally cached artifacts; asserts `licenses.xml` |
| `MLICENSE-24` | ❌ local-path | Scope-based include/exclude filters; dependency uses `${project.basedir}/LICENSE.txt` as license URL |
| `ISSUE-40` | ❌ no-xml | Excluded-scope dependencies are absent from `licenses.xml` |
| `ISSUE-55` | ❌ no-xml | BSD license with special characters in its name is downloaded with a sanitised filename |
| `ISSUE-80` | ❌ no-xml | Byte-for-byte equality check of generated `licenses.xml` against a pre-committed expected file |
| `MLICENSE-72` | ❌ no-xml | Custom `licensesOutputDirectory` and `licensesOutputFile` overrides redirect downloaded files and index XML |
| `PR-32` | ❌ live-url | Downloads a license from a live remote URL; verifies filename, content, and `licenses.xml` reference |
| `PR-33` | ❌ live-url | Multi-module `download-licenses` downloads per-module license files with artifact-scoped filenames |

---

## `update-file-header` goal

| IT | CI | Description |
|----|----|-------------|
| `update-file-header-test-mojo` | ✅ | `update-file-header` inserts the correct copyright year range and organisation name into multiple source file types |
| `MLICENSE-3` | ✅ | `canUpdateCopyright=true` rewrites an existing copyright year; "do NOT update!" markers are preserved |
| `MLICENSE-27` | ✅ | Custom `processStartTag`, `processEndTag`, and `sectionDelimiter` markers are written alongside SVN keywords |
| `MLICENSE-30` | ✅ | Custom FreeMarker `descriptionTemplate` produces a header containing artifact description, organisation, and version |
| `MLICENSE-44` | ✅ | Custom FreeMarker header template embedding file metadata (name, part-of, description) is rendered into each source file |
| `MLICENSE-45` | ✅ | License header template and full license text loaded from a classpath JAR resource are applied to source files |
| `ISSUE-7` | ✅ | `useJavaNoReformatCommentStartTag=true` writes the `/*-` (non-reformatting) header style |
| `ISSUE-22` | ✅ | `update-file-header` inserts a license block into HTML files respecting the existing DOCTYPE declaration |
| `ISSUE-38` | ✅ | `update-file-header` adds a license comment to non-standard file types such as Dockerfile |
| `ISSUE-31` | ✅ | License header template loaded from a classpath resource is applied and the full license text is written to `LICENSE.txt` |
| `ISSUE-21` | ✅ | `remove-file-header` strips the license block (`#%L`) from source files without leaving any remnant |

---

## `update-project-license` goal

| IT | CI | Description |
|----|----|-------------|
| `update-project-license-test-mojo` | ✅ | `update-project-license` copies the configured license (GPL, not LGPL) to `LICENSE.txt` and the generated-sources location |
| `MLICENSE-5` | ✅ | `update-project-license` copies a custom HTML license to `LICENSE.html` at the project root and generated-sources location |
| `MLICENSE-55` | ✅ | Running `update-project-license` and `update-file-header` both bound to `process-sources` with `gpl_v3` completes without error |
| `MLICENSE-92` | ✅ | `update-project-license` with a custom FreeMarker template correctly interpolates project properties into `LICENSE.txt` |

---

## Site / reporting goals

| IT | CI | Description |
|----|----|-------------|
| `third-party-report` | ✅ | `third-party-report` site page lists dependency license data using the full canonical license name, not a short alias |
| `third-party-report-global-db` | ✅ | `third-party-report` site page in a global-db multi-module project shows the expected artifact link for a registry-resolved dependency |
| `MLICENSE-33` | ✅ | Running `add-third-party` and the site report in the same build produces both THIRD-PARTY.txt and `third-party-report.html` |

---

## Other goals

| IT | CI | Description |
|----|----|-------------|
| `jars-json-list-test-mojo` | ✅ | `jars-json-list` goal produces a JSON file listing all dependency JARs that matches the expected output |
| `maven-api-compat` | ✅ | Verifies Plexus DI wiring of `ProjectBuilder`, `ProjectDependenciesResolver`, `LegacySupport`, and Aether `RepositorySystem` on each Maven version |
| `MLICENSE-27` | ✅ | (see `update-file-header` above) |
