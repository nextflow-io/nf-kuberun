/*
 * Copyright 2013-2026, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.kuberun

import nextflow.exception.AbortOperationException
import org.pf4j.PluginWrapper
import spock.lang.Specification

class KubeRunPluginTest extends Specification {

    def 'should reject unknown command' () {
        given:
        def plugin = new KubeRunPlugin(Mock(PluginWrapper))

        when:
        plugin.exec('nf-kuberun', 'foo', [])
        then:
        def e = thrown(AbortOperationException)
        e.message.startsWith("Invalid command 'nf-kuberun:foo'")
    }

    def 'should require a pipeline' () {
        given:
        def plugin = new KubeRunPlugin(Mock(PluginWrapper))

        when:
        plugin.exec('nf-kuberun', 'run', ['--namespace', 'x'])
        then:
        def e = thrown(AbortOperationException)
        e.message == 'No project name was specified'
    }

    def 'should launch the driver and return its exit status' () {
        given:
        def plugin = Spy(new KubeRunPlugin(Mock(PluginWrapper)))

        when:
        def ret = plugin.exec('nf-kuberun', 'run', ['nextflow-io/hello', '--name', 'my-run'])
        then:
        1 * plugin.run({ KubeRunOptions it -> it.pipeline == 'nextflow-io/hello' && it.runName == 'my-run' }) >> 3
        ret == 3
    }
}
