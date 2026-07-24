# AGENTS.md

Authoritative agent and contributor guide for `nf-command-archiver`.

## What this plugin does

`nf-command-archiver` is a Nextflow plugin that collects the per-task `.command.sh`
and `.command.log` files produced during a run and bundles them into a single
gzipped tar archive when the workflow finishes. The result is a portable, self
contained record of exactly what each task ran and what it logged.

It works as a **trace observer**. The plugin does not add processes or channels to
your workflow and does not change pipeline outputs. It listens to task lifecycle
events, remembers each finished task's work directory, then reads the requested
command files out of those directories at flow completion and streams them into the
archive.

Archive entries mirror the Nextflow `work/` hash layout so every file traces back to
its task. Given a work directory such as `work/a1/b2c3d4e5.../`, the archive contains
entries like:

```
a1/b2c3d4e5.../.command.sh
a1/b2c3d4e5.../.command.log
```

## Where it fits in the RNA Cloud platform

This plugin is used by the RNA Cloud alignment pipeline (`rna_cloud_alignment_pipeline`)
for debugging and audit. When a run finishes on the host or on NCI Gadi, the archive
gives operators a single file that captures the generated shell script and stdout/stderr
log for each task, without needing to keep or trawl the full `work/` tree. That is useful
for post mortem debugging of failed alignment jobs and for keeping an audit trail of what
executed.

Note on ownership: unlike most RNA Cloud repositories, this plugin lives on a personal
GitHub org at `PeterKneale/nf-command-archiver`, not under `RNA-Cloud/*`. It is a general
purpose Nextflow plugin that the alignment pipeline happens to consume, so keep it generic
and do not bake in pipeline specific assumptions.

## Stack and requirements

- **Language:** Groovy on the JVM (the `src/` tree is `.groovy`, not Java). The task
  domain is often described as "Java / Gradle" because it is a JVM plugin built with
  Gradle, but the source is Groovy.
- **Build tool:** Gradle via the committed wrapper (`./gradlew`), Gradle 9.6.1 (see
  `gradle/wrapper/gradle-wrapper.properties`).
- **Gradle plugin:** `io.nextflow.nextflow-plugin` `1.0.0-beta.15`. This is the official
  Nextflow plugin development plugin. It generates the `META-INF/extensions.idx` and
  `META-INF/spec.json` from `build.gradle`, so there is no hand written extensions index.
- **JDK:** Java 21 (Temurin) is used by CI and the dev container. Nextflow requires Java 17
  or newer.
- **Minimum Nextflow:** `25.10.0`, declared in `build.gradle` as `nextflowVersion`. The
  plugin relies on the `TraceObserverV2` / `TraceObserverFactoryV2` API, which needs
  Nextflow `25.04.0` or newer.
- **Runtime dependency:** `org.apache.commons:commons-compress:1.27.1`, used for the tar and
  gzip streams. It is bundled into the plugin zip at build time.
- **License:** MIT (see `LICENSE`).

## Repository layout

```
build.gradle                      # plugin config: version, nextflowVersion, provider, className, extensionPoints, deps
settings.gradle                   # rootProject.name = 'nf-command-archiver'
gradlew, gradle/wrapper/          # committed Gradle wrapper (9.6.1)
Makefile                          # thin wrappers over ./gradlew targets
README.md                         # short user facing overview
LICENSE                           # MIT
.github/workflows/build.yml       # CI: assemble, test, install, run validation pipeline
.devcontainer/                    # VS Code dev container (JDK 21 + Nextflow)
src/main/groovy/com/peterkneale/commandarchiver/
    CommandArchiverPlugin.groovy    # PF4J plugin entry point
    CommandArchiverFactory.groovy   # extension point: reads config, builds the observer
    CommandArchiverObserver.groovy  # the trace observer that captures files and writes the archive
src/test/groovy/com/peterkneale/commandarchiver/
    CommandArchiverObserverTest.groovy  # Spock unit test
validation/                       # a tiny end to end pipeline to exercise the plugin
    main.nf
    nextflow.config
```

Everything ships under the package `com.peterkneale.commandarchiver`.

There is a stray nested `nf-command-archiver/` directory at the repo root that only holds
a `.DS_Store`. Ignore it. Do not add files to it.

## Main classes

- **`CommandArchiverPlugin`** extends `nextflow.plugin.BasePlugin`. It is the PF4J entry
  point named by `className` in `build.gradle`. It contains no logic beyond the wrapper
  constructor.
- **`CommandArchiverFactory`** implements `nextflow.trace.TraceObserverFactoryV2`. It is the
  single extension point listed in `build.gradle`. Its `create(Session)` reads the
  `commandArchiver` config block. If `enabled` is false it returns an empty list so the
  plugin is inert. Otherwise it resolves the output path to an absolute path and returns one
  `CommandArchiverObserver`.
- **`CommandArchiverObserver`** implements `nextflow.trace.TraceObserverV2`. It holds a
  thread safe set of work directories (`ConcurrentHashMap.newKeySet()`) because task events
  can fire from multiple threads. `onTaskComplete` always records the task, `onTaskCached`
  records it only when `includeCached` is true. Recording applies the optional
  `processFilter` regex against the process name and then stores `task.workDir`.
  `onFlowComplete` streams every requested file that exists into a
  `GzipCompressorOutputStream` wrapped in a `TarArchiveOutputStream`
  (`LONGFILE_POSIX` mode), logging a summary. Failures during archive writing are caught
  and logged, they do not crash the run.

## Configuration

Options live under the `commandArchiver` block and are read with `session.config.navigate`,
so they are all optional and fall back to defaults.

| Key | Type | Default | Meaning |
|-----|------|---------|---------|
| `enabled` | boolean | `false` | Master switch. When false the plugin does nothing. |
| `output` | string | `command-archive.tar.gz` | Archive path. Resolved to an absolute path. Parent dirs are created. |
| `files` | list of string | `['.command.sh', '.command.log']` | File names captured from each task work dir. |
| `includeCached` | boolean | `true` | Also archive cached tasks. Their work dir still holds the command files. |
| `processFilter` | string (regex) | none | Full match regex on the process name. Omit to capture every process. |

Example (see `validation/nextflow.config`):

```groovy
plugins {
    id 'nf-command-archiver@0.1.0'
}

commandArchiver {
    enabled       = true
    output        = "${launchDir}/command-archive.tar.gz"
    files         = ['.command.sh', '.command.log']
    includeCached = true
    processFilter = '.*ALIGN.*'   // optional; omit to capture every process
}
```

The `processFilter` uses Groovy's `==~` operator, which is a **full match** against the
process name. Use `.*ALIGN.*` rather than `ALIGN` to match a name that merely contains
`ALIGN`.

## Build, test, install and release

Prefer the Makefile targets. They are thin wrappers over `./gradlew`, so either form works.
Run from the repo root.

| Task | Makefile | Gradle |
|------|----------|--------|
| Build the plugin zip and jar | `make assemble` | `./gradlew assemble` |
| Run unit tests | `make test` | `./gradlew test` |
| Install into the local `~/.nextflow/plugins` dir | `make install` | `./gradlew install` |
| Publish to the Nextflow Registry | `make release` | `./gradlew releasePlugin` |
| Clean build, work and `.nextflow*` artifacts | `make clean` | `./gradlew clean` (Makefile also removes `work/` and `.nextflow*`) |

`assemble`, `install` and `releasePlugin` are provided by the `io.nextflow.nextflow-plugin`
Gradle plugin, not hand written. `assemble` produces `build/libs/nf-command-archiver-<version>.jar`
and `build/distributions/nf-command-archiver-<version>.zip`.

Tests use the **Spock** framework (`spock.lang.Specification`). The existing test constructs
the observer and asserts that `onFlowComplete` with no tasks does not throw. It does not spin
up a real Nextflow session.

### Manual end to end check

The `validation/` directory holds a minimal pipeline (`main.nf` defines an `ALIGN_SAMPLE`
process over three samples) wired to the plugin through `validation/nextflow.config`. To try
it against a real run:

1. `make install`
2. `cd validation && nextflow run main.nf`
3. Confirm `command-archive.tar.gz` appears in the launch dir with `xx/xxxxxx/.command.sh`
   style entries.

You can also install against any pipeline, for example
`nextflow run hello -plugins nf-command-archiver@0.1.0`.

## Enabling the plugin in a pipeline

Add the plugin id to the pipeline `nextflow.config` and turn it on with a `commandArchiver`
block, as shown above. Nextflow downloads the plugin from the Nextflow Registry the first
time the pipeline runs, so no local install is needed for consumers. During local plugin
development, `make install` places the built plugin in the local Nextflow plugins dir so a
pinned version resolves without a registry round trip.

## Publishing to the Nextflow Registry

Publishing is done with `./gradlew releasePlugin` (or `make release`). The
`io.nextflow.nextflow-plugin` Gradle plugin handles packaging and upload. Publishing needs
Nextflow Registry credentials on the release machine (a registry API token supplied through
the environment or Gradle properties as expected by the Nextflow Gradle plugin, not
committed to the repo). Bump `version` in `build.gradle` before releasing, and keep the
version referenced in `README.md`, `validation/nextflow.config` and any consumer pipeline
in step with it.

## CI

`.github/workflows/build.yml` runs on every push and pull request. It sets up JDK 21
(Temurin), runs `./gradlew assemble` then `./gradlew test`, then installs Nextflow, runs
`./gradlew install` and finally runs the `validation/` pipeline as a real end to end check.

Stale comment to be aware of: the workflow has a "Generate Gradle wrapper if missing" step
whose comment claims the project ships no committed wrapper. That is out of date. The
wrapper (`gradlew` plus `gradle/wrapper/`) is committed, so that step is a no op.

## Dev container

`.devcontainer/devcontainer.json` provisions a Debian Bookworm image with JDK 21 (Temurin)
and Gradle, installs Nextflow onto `PATH` and adds the Nextflow, Java and Gradle VS Code
extensions. Docker in Docker is present but commented out. Enable it only if you need to run
pipelines whose processes use containers.

## Conventions and gotchas

- **The source is Groovy.** Keep new plugin code in `src/main/groovy/...` under the
  `com.peterkneale.commandarchiver` package and tests under `src/test/groovy/...` using Spock.
- **Extension registration is generated.** The set of extension points comes from the
  `extensionPoints` list in `build.gradle`. Do not add a hand written
  `META-INF/extensions.idx`. If you add a new observer factory or extension, register it in
  `build.gradle`.
- **Task event accessors are version sensitive.** `CommandArchiverObserver.record` reaches
  through `event.handler.task`, then `task.workDir` and `task.processor.name`. These
  accessors depend on the Nextflow `TaskEvent` shape. If you raise or lower the supported
  Nextflow version, re-verify them against that version's `TraceObserverV2` and `TaskEvent`
  sources. The code carries a `NOTE` comment to this effect.
- **Never break the run.** Archive writing in `onFlowComplete` is wrapped in try/catch and
  only logs on failure. Preserve that behaviour so an archiving problem never fails an
  otherwise successful pipeline.
- **Missing files are skipped silently.** Only requested `files` that exist in a work dir are
  added. A task without a `.command.log` (for example) contributes only the files it has.
- **Empty run.** If no work directories were captured, the observer logs a warning and writes
  no archive rather than producing an empty file.
- **House style.** Do not use em dashes. Do not use Oxford commas. Do not put personal data
  such as email addresses or personal names into files. Public GitHub org or repo identifiers
  (for example `PeterKneale/nf-command-archiver`) and code identifiers (for example the
  `com.peterkneale` package) are fine.
- **Keep it generic.** This is a standalone plugin consumed by the RNA Cloud alignment
  pipeline, so avoid hard coding anything specific to that pipeline.
