package com.peterkneale.commandarchiver

import java.nio.file.Paths
import spock.lang.Specification

class CommandArchiverObserverTest extends Specification {

    def 'observer can be constructed with defaults'() {
        when:
        def observer = new CommandArchiverObserver(
            Paths.get('command-archive.tar.gz'),
            ['.command.sh', '.command.log'],
            true,
            null
        )

        then:
        observer != null
    }

    def 'flow complete with no tasks does not throw'() {
        given:
        def observer = new CommandArchiverObserver(
            Paths.get('command-archive.tar.gz'),
            ['.command.sh'],
            true,
            null
        )

        when:
        observer.onFlowComplete()

        then:
        noExceptionThrown()
    }
}
