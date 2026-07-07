# nf-command-archiver

## Overview
`nf-command-archiver` is a Nextflow plugin that collects the per-task `.command.sh` and `.command.log` files from a run and bundles them into a single gzipped tar archive when the workflow finishes. 

It gives you a portable record of exactly what ran and what each task logged, which is useful for debugging failed runs or archiving alignment jobs.
