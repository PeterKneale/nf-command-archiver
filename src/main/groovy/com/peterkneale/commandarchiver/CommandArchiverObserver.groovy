package com.peterkneale.commandarchiver

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

import groovy.util.logging.Slf4j
import nextflow.trace.TraceObserverV2
import nextflow.trace.event.TaskEvent

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream

/**
 * Captures per-task command files (.command.sh, .command.log by default) from a run
 * and writes them into a single gzipped tar archive when the workflow completes.
 */
@Slf4j
class CommandArchiverObserver implements TraceObserverV2 {

    private final Path outputPath
    private final List<String> targetFiles
    private final boolean includeCached
    private final String processFilter

    // populated as tasks finish; a concurrent set because task events can fire from multiple threads
    private final Set<Path> workDirs = ConcurrentHashMap.newKeySet()

    CommandArchiverObserver(Path outputPath, List<String> targetFiles, boolean includeCached, String processFilter) {
        this.outputPath = outputPath
        this.targetFiles = targetFiles
        this.includeCached = includeCached
        this.processFilter = processFilter
    }

    @Override
    void onTaskComplete(TaskEvent event) {
        record(event)
    }

    @Override
    void onTaskCached(TaskEvent event) {
        // cached tasks still have their original work dir with the command files intact
        if( includeCached )
            record(event)
    }

    private void record(TaskEvent event) {
        // NOTE: verify these accessors against your Nextflow version's TaskEvent source.
        // event.handler -> TaskHandler, .task -> TaskRun, .workDir -> Path
        final task = event.handler.task
        if( processFilter && !(task.processor.name ==~ processFilter) )
            return
        workDirs << task.workDir
    }

    @Override
    void onFlowComplete() {
        if( workDirs.isEmpty() ) {
            log.warn "[command-archiver] no task work directories captured; nothing to archive"
            return
        }
        try {
            final count = writeArchive()
            log.info "[command-archiver] archived ${count} files from ${workDirs.size()} tasks into ${outputPath}"
        }
        catch( Exception e ) {
            log.error "[command-archiver] failed writing ${outputPath}: ${e.message}", e
        }
    }

    private int writeArchive() {
        final target = outputPath.toAbsolutePath()
        if( target.parent != null )
            Files.createDirectories(target.parent)

        int written = 0
        Files.newOutputStream(target).withCloseable { os ->
            new GzipCompressorOutputStream(os).withCloseable { gz ->
                new TarArchiveOutputStream(gz).withCloseable { tar ->
                    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    for( Path dir : workDirs ) {
                        // mirror the work/xx/xxxxxx layout so entries trace back to the task
                        final prefix = dir.parent.fileName.toString() + '/' + dir.fileName.toString()
                        for( String name : targetFiles ) {
                            final f = dir.resolve(name)
                            if( Files.exists(f) ) {
                                addEntry(tar, f, prefix + '/' + name)
                                written++
                            }
                        }
                    }
                }
            }
        }
        return written
    }

    private static void addEntry(TarArchiveOutputStream tar, Path file, String entryName) {
        final entry = new TarArchiveEntry(entryName)
        entry.setSize(Files.size(file))
        tar.putArchiveEntry(entry)
        Files.copy(file, tar)
        tar.closeArchiveEntry()
    }
}
