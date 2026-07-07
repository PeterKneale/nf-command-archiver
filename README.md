# nf-command-archiver

## Overview
`nf-command-archiver` is a Nextflow plugin that collects the per-task `.command.sh` and `.command.log` files from a run and bundles them into a single gzipped tar archive when the workflow finishes. 

It gives you a portable record of exactly what ran and what each task logged, which is useful for debugging failed runs or archiving alignment jobs.

## Quick Start
Enable the plugin and turn on archiving in your nextflow.config:
```groovy
groovyplugins {
    id 'nf-command-archiver@0.1.0'
}

commandArchiver {
    enabled = true
    output  = "${launchDir}/command-archive.tar.gz"
}
```
Then run your pipeline as normal. When it completes, `command-archive.tar.gz` appears in your launch directory, with entries laid out as `xx/xxxxxx/.command.sh` so each file traces back to its task. 
To restrict capture to specific processes, add processFilter = `'.*ALIGN.*'`.
