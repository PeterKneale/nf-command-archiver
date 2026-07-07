# nf-command-archiver

A Nextflow plugin that captures per-task command files (`.command.sh` and
`.command.log` by default) from a pipeline run and writes them into a single
gzipped tar archive when the workflow completes.

## Summary

The plugin registers a `TraceObserverV2` that records each task's work directory
as tasks finish (including cached tasks), then on `onFlowComplete` walks those
directories, pulls the target files and streams them into a `.tar.gz`. Archive
entries mirror the `work/xx/xxxxxx/` layout so each file traces back to its task.

## Get Started

Enable the plugin in your pipeline `nextflow.config`:

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

## Important: this folder has no Gradle wrapper

To keep the download small this project does not include `gradlew` or
`gradle/wrapper/`. The `make` commands call `./gradlew`, so add the wrapper once
using either approach:

1. If you have Gradle installed: run `gradle wrapper` in this folder.
2. Otherwise scaffold a throwaway project with `nextflow plugin create`, then
   copy its `gradlew`, `gradlew.bat` and `gradle/` directory into this folder.

The rest of the project (sources, `build.gradle`, `settings.gradle`, `Makefile`)
is ready to build.

## Building and testing

```bash
make assemble   # compile and zip the plugin
make install    # install into your local $NXF_HOME/plugins
make test       # run unit tests
```

Then run the bundled validation pipeline:

```bash
cd validation
nextflow run main.nf
# command-archive.tar.gz appears in the launch directory
```

## Configuration options

| Option          | Default                  | Description                                             |
| --------------- | ------------------------ | ------------------------------------------------------- |
| `enabled`       | `false`                  | Master switch for the plugin                            |
| `output`        | `command-archive.tar.gz` | Output archive path                                     |
| `files`         | `.command.sh`, `.command.log` | File names to collect from each task work directory |
| `includeCached` | `true`                   | Also collect files from cached tasks                    |
| `processFilter` | (none)                   | Optional regex on process name to restrict which tasks are captured |

## Notes

- Requires Nextflow 25.04.0 or newer for the `TraceObserverV2` API.
- If `cleanup = true` or `-with-cleanup` is set, work directories may be removed
  before the archive is written, producing an empty or partial archive.
- Verify the `event.handler.task.workDir` accessors in `CommandArchiverObserver`
  against your Nextflow version's `TaskEvent` source if the build complains.
- `@CompileStatic` is intentionally left off the two classes for a forgiving
  first build. Add it back once the plugin compiles cleanly.

## License

Apache License 2.0.
