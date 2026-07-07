# nf-command-archiver

## Summary

`nf-command-archiver` is a Nextflow plugin that collects the per-task `.command.sh` and `.command.log` files from a run and bundles them into a single gzipped tar archive when the workflow finishes. 

It gives you a portable record of exactly what ran and what each task logged, which is useful for debugging failed runs or archiving alignment jobs.

## Get Started

Enable the plugin in your pipeline `nextflow.config`:

```groovy
plugins {
    id 'nf-command-archiver@0.1.0'
}
```

Nextflow downloads the plugin from the Nextflow Registry the first time the pipeline runs.

## Examples

- Update your `nextflow.config` per the example section

```nextflow
commandArchiver {
    enabled = true
    output  = "${launchDir}/command-archive.tar.gz"
}
```

Then run your pipeline as normal. When it completes, `command-archive.tar.gz` appears in your launch directory, with entries laid out as `xx/xxxxxx/.command.sh` so each file traces back to its task. 
To restrict capture to specific processes, add processFilter = `'.*ALIGN.*'`.

## Plugin development

This project was created from the [Nextflow plugin template](https://www.nextflow.io/docs/latest/guides/gradle-plugin.html#gradle-plugin-create).

### Building

To build the plugin:

```bash
make assemble
```

### Testing with Nextflow

The plugin can be tested without a local Nextflow installation:

1. Build and install the plugin to your local Nextflow installation: `make install`
2. Run a pipeline with the plugin: `nextflow run hello -plugins nf-command-archiver@0.1.0`

## License

MIT License See the [`LICENSE`](LICENSE) file for details.
