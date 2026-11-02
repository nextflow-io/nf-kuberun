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
import spock.lang.Specification
import spock.lang.Unroll

class KubeRunOptionsTest extends Specification {

    def 'should parse positional args and options' () {
        when:
        def opts = KubeRunOptions.parse(['nextflow-io/hello', 'a', 'b', '--namespace', 'ns', '--volume-mount', 'pvc-1:/mnt/a,pvc-2:/mnt/b', '--alpha', '1'])
        then:
        opts.pipeline == 'nextflow-io/hello'
        opts.args == ['a', 'b']
        opts.namespace == 'ns'
        opts.volMounts == ['pvc-1:/mnt/a', 'pvc-2:/mnt/b']
        opts.params == [alpha: '1']
    }

    def 'should parse no pipeline' () {
        when:
        def opts = KubeRunOptions.parse(['--namespace', 'ns'])
        then:
        opts.pipeline == null
        opts.args == []
    }

    @Unroll
    def 'should map option #KEY' () {
        when:
        def opts = KubeRunOptions.parse(['hello', "--$KEY".toString(), VALUE])
        then:
        opts."$FIELD" == EXPECTED

        where:
        KEY                 | VALUE                 | FIELD             | EXPECTED
        'head-image'        | 'img:1'               | 'headImage'       | 'img:1'
        'head-cpus'         | '2'                   | 'headCpus'        | 2
        'head-memory'       | '1Gi'                 | 'headMemory'      | '1Gi'
        'head-prescript'    | '/pre.sh'             | 'headPreScript'   | '/pre.sh'
        'remote-config'     | '/a.config,/b.config' | 'remoteConfig'    | ['/a.config', '/b.config']
        'remote-profile'    | 'cluster'             | 'remoteProfile'   | 'cluster'
        'background'        | 'true'                | 'background'      | true
        'config'            | 'x.config,y.config'   | 'config'          | ['x.config', 'y.config']
        'trace'             | 'nextflow.k8s'        | 'trace'           | 'nextflow.k8s'
        'debug'             | 'nextflow.k8s'        | 'debug'           | 'nextflow.k8s'
        'jvm.foo'           | 'bar'                 | 'jvmOpts'         | [foo: 'bar']
        'name'              | 'my-run'              | 'runName'         | 'my-run'
        'cache'             | 'false'               | 'cacheable'       | false
        'resume'            | 'true'                | 'resume'          | 'true'
        'pool-size'         | '4'                   | 'poolSize'        | 4
        'poll-interval'     | '5s'                  | 'pollInterval'    | '5s'
        'queue-size'        | '9'                   | 'queueSize'       | 9
        'work-dir'          | '/mnt/work'           | 'workDir'         | '/mnt/work'
        'params-file'       | 'p.json'              | 'paramsFile'      | 'p.json'
        'process.memory'    | '4 GB'                | 'process'         | [memory: '4 GB']
        'env.FOO'           | 'bar'                 | 'env'             | [FOO: 'bar']
        'revision'          | 'dev'                 | 'revision'        | 'dev'
        'latest'            | 'true'                | 'latest'          | true
        'with-trace'        | 'true'                | 'withTrace'       | 'true'
        'with-timeline'     | 't.html'              | 'withTimeline'    | 't.html'
        'with-dag'          | 'true'                | 'withDag'         | 'true'
        'profile'           | 'test'                | 'profile'         | 'test'
        'dump-hashes'       | 'json'                | 'dumpHashes'      | 'json'
        'dump-channels'     | 'foo'                 | 'dumpChannels'    | 'foo'
        'entry'             | 'main'                | 'entryName'       | 'main'
        'main-script'       | 'x.nf'                | 'mainScript'      | 'x.nf'
        'plugins'           | 'nf-amazon'           | 'plugins'         | 'nf-amazon'
        'hub'               | 'gitlab'              | 'hubProvider'     | 'gitlab'
        'user'              | 'me'                  | 'hubUser'         | 'me'
        'params.name'       | 'x'                   | 'params'          | [name: 'x']
        'foo'               | 'x'                   | 'params'          | [foo: 'x']
    }

    @Unroll
    def 'should reject unsupported option #KEY' () {
        when:
        KubeRunOptions.parse(['hello', "--$KEY".toString(), 'x'])
        then:
        def e = thrown(AbortOperationException)
        e.message == "Option `--$KEY` not supported with Kubernetes deployment"

        where:
        KEY << ['with-docker', 'lib', 'stub-run', 'with-report', 'executor.queueSize', 'cluster.foo']
    }

    @Unroll
    def 'should reject invalid value for #KEY' () {
        when:
        KubeRunOptions.parse(['hello', "--$KEY".toString(), VALUE])
        then:
        thrown(AbortOperationException)

        where:
        KEY             | VALUE
        'head-cpus'     | 'two'
        'queue-size'    | '1.5'
        'background'    | 'yes'
        'cache'         | '1'
    }

    def 'should reject missing option value' () {
        when:
        KubeRunOptions.parse(['hello', '--namespace'])
        then:
        thrown(AbortOperationException)
    }

    @Unroll
    def 'should check run name #NAME' () {
        given:
        def opts = new KubeRunOptions(runName: NAME)

        when:
        opts.checkRunName()
        then:
        opts.runName == EXPECTED

        where:
        NAME            | EXPECTED
        'foo'           | 'foo'
        'foo-bar'       | 'foo-bar'
    }

    @Unroll
    def 'should reject run name #NAME' () {
        when:
        new KubeRunOptions(runName: NAME).checkRunName()
        then:
        thrown(AbortOperationException)

        where:
        NAME << ['Foo', 'foo_bar', '-foo', 'last', '1foo']
    }

    def 'should generate run name' () {
        given:
        def opts = new KubeRunOptions()

        when:
        opts.checkRunName()
        then:
        opts.runName
        !opts.runName.contains('_')
    }
}
