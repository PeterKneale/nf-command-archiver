package com.peterkneale.commandarchiver

import java.nio.file.Path
import java.nio.file.Paths

import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.trace.TraceObserverV2
import nextflow.trace.TraceObserverFactoryV2

@Slf4j
class CommandArchiverFactory implements TraceObserverFactoryV2 {

    @Override
    Collection<TraceObserverV2> create(Session session) {
        final enabled = session.config.navigate('commandArchiver.enabled', false) as boolean
        if( !enabled )
            return Collections.<TraceObserverV2>emptyList()

        final output        = session.config.navigate('commandArchiver.output', 'command-archive.tar.gz') as String
        final files         = session.config.navigate('commandArchiver.files', ['.command.sh', '.command.log']) as List<String>
        final includeCached = session.config.navigate('commandArchiver.includeCached', true) as boolean
        final processFilter = session.config.navigate('commandArchiver.processFilter') as String

        final Path outputPath = Paths.get(output).toAbsolutePath()
        log.debug "[command-archiver] enabled; output=${outputPath} files=${files} includeCached=${includeCached} processFilter=${processFilter}"

        return List.<TraceObserverV2>of(
            new CommandArchiverObserver(outputPath, files, includeCached, processFilter)
        )
    }
}
