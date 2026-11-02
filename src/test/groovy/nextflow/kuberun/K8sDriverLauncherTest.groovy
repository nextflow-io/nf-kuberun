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

import java.nio.file.Files

import nextflow.SysEnv
import nextflow.exception.AbortOperationException
import nextflow.k8s.K8sConfig
import nextflow.k8s.client.ClientConfig
import nextflow.k8s.client.K8sClient
import nextflow.k8s.model.PodMountConfig
import nextflow.k8s.model.PodOptions
import nextflow.k8s.model.PodSpecBuilder
import nextflow.k8s.model.PodVolumeClaim
import spock.lang.Specification
import spock.lang.Unroll
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class K8sDriverLauncherTest extends Specification {

    def setup() {
        PodSpecBuilder.VOLUMES.set(0)
    }

    def 'should execute run' () {
        given:
        def NAME = 'nxf-foo'
        def NF_CONFIG = [process:[executor:'k8s']]
        def K8S_CONFIG = Mock(K8sConfig)
        def K8S_CLIENT = Mock(K8sClient)
        def driver = Spy(K8sDriverLauncher)

        when:
        driver.run(NAME, ['a','b','c'])
        then:
        1 * driver.makeConfig(NAME) >> NF_CONFIG
        1 * driver.makeK8sConfig(NF_CONFIG) >> K8S_CONFIG
        1 * driver.makeK8sClient(K8S_CONFIG) >> K8S_CLIENT
        1 * K8S_CONFIG.checkStorageAndPaths(K8S_CLIENT)
        1 * driver.createK8sConfigMap() >> { }
        1 * driver.createK8sLauncherPod() >> null
        1 * driver.waitPodStart() >> { }
        1 * driver.printK8sPodOutput() >> { }

        driver.pipelineName == NAME
        driver.interactive == false
        driver.config == NF_CONFIG
        driver.k8sConfig == K8S_CONFIG
        driver.k8sClient == K8S_CLIENT
    }

    def 'should make k8s config' () {

        given:
        K8sConfig k8sConfig
        K8sDriverLauncher driver = Spy(K8sDriverLauncher)

        when:
        k8sConfig = driver.makeK8sConfig([:])
        then:
        k8sConfig != null

        when:
        k8sConfig = driver.makeK8sConfig(k8s: [storageClaimName: 'foo', storageMountPath: '/mnt'])
        then:
        k8sConfig.getStorageClaimName() == 'foo'
        k8sConfig.getStorageMountPath() == '/mnt'

    }


    @Unroll
    def 'should get cmd cli' () {

        given:
        def l = new K8sDriverLauncher(options: new KubeRunOptions(OPTS), pipelineName: 'foo')

        expect:
        l.getLaunchCli() == EXPECTED

        where:
        OPTS                                | EXPECTED
        [:]                                 | 'nextflow run foo'
        [cacheable: false]                  | 'nextflow run foo -cache false'
        [cacheable: true]                   | 'nextflow run foo'
        [resume: 'true']                    | 'nextflow run foo -resume last'
        [resume: 'abc']                     | 'nextflow run foo -resume abc'
        [poolSize: 10]                      | 'nextflow run foo -ps 10'
        [pollInterval: '5s']                | 'nextflow run foo -pi 5s'
        [queueSize: 9]                      | 'nextflow run foo -qs 9'
        [revision: 'xyz']                   | 'nextflow run foo -r xyz'
        [latest: true]                      | 'nextflow run foo -latest'
        [mainScript: 'x.nf']                | 'nextflow run foo -main-script x.nf'
        [hubProvider: 'gitlab']             | 'nextflow run foo -hub gitlab'
        [resume: 'false']                   | 'nextflow run foo'
        [withTrace: 'true']                 | 'nextflow run foo -with-trace'
        [withTrace: 'false']                | 'nextflow run foo'
        [withTrace: 'trace.txt']            | 'nextflow run foo -with-trace trace.txt'
        [withTimeline: 'true']              | 'nextflow run foo -with-timeline'
        [withTimeline: 'false']             | 'nextflow run foo'
        [withDag: 'true']                   | 'nextflow run foo -with-dag'
        [withDag: 'false']                  | 'nextflow run foo'
        [dumpHashes: 'true']                | 'nextflow run foo -dump-hashes'
        [dumpHashes: 'false']               | 'nextflow run foo'
        [dumpHashes: 'json']                | 'nextflow run foo -dump-hashes json'
        [dumpChannels: 'true']              | 'nextflow run foo -dump-channels'
        [dumpChannels: 'lala']              | 'nextflow run foo -dump-channels lala'
        [offline: true]                     | 'nextflow run foo -offline'
        [deep: 1]                           | 'nextflow run foo -deep 1'
        [outputDir: '/mnt/out']             | 'nextflow run foo -output-dir /mnt/out'
        [outputFormat: 'json']              | 'nextflow run foo -output-format json'
        [bucketDir: 's3://b/work']          | 'nextflow run foo -bucket-dir s3\\://b/work'
        [withCloudcache: 'true']            | 'nextflow run foo -with-cloudcache'
        [withCloudcache: 's3://b/cache']    | 'nextflow run foo -with-cloudcache s3\\://b/cache'
        [withTower: 'true']                 | 'nextflow run foo -with-tower'
        [withTower: 'https://x.io/api']     | 'nextflow run foo -with-tower https\\://x.io/api'
        [withWave: 'true']                  | 'nextflow run foo -with-wave'
        [withFusion: 'true']                | 'nextflow run foo -with-fusion true'
        [withFusion: 'false']               | 'nextflow run foo -with-fusion false'
        [withReport: 'true']                | 'nextflow run foo -with-report'
        [withReport: 'r.html']              | 'nextflow run foo -with-report r.html'
        [withAgentTrace: true]              | 'nextflow run foo -with-agent-trace'
        [withNotification: 'me@x.io']       | 'nextflow run foo -with-notification me@x.io'
        [withConda: 'true']                 | 'nextflow run foo -with-conda'
        [withConda: 'env.yml']              | 'nextflow run foo -with-conda env.yml'
        [withoutConda: true]                | 'nextflow run foo -without-conda'
        [withSpack: 'true']                 | 'nextflow run foo -with-spack'
        [withoutSpack: true]                | 'nextflow run foo -without-spack'
        [withPodman: 'true']                | 'nextflow run foo -with-podman'
        [withPodman: 'ubuntu']              | 'nextflow run foo -with-podman ubuntu'
        [withCharliecloud: 'true']          | 'nextflow run foo -with-charliecloud'
        [stubRun: true]                     | 'nextflow run foo'
        [preview: true]                     | 'nextflow run foo -preview'
        [disableJobsCancellation: true]     | 'nextflow run foo -disable-jobs-cancellation'
        [env: [XX:'hello', YY: 'world']]    | 'nextflow run foo -e.XX=hello -e.YY=world'
        [process: [mem: '100',cpus:'2']]    | 'nextflow run foo -process.mem=100 -process.cpus=2'
        [params: [alpha:'x', beta:'y']]     | 'nextflow run foo --alpha=x --beta=y'
        [params: [alpha: '/path/*.txt']]    | 'nextflow run foo --alpha=/path/\\*.txt'
        [entryName: 'lala']                 | 'nextflow run foo -entry lala'
        [remoteConfig: ['/a.config','/b.config']] | 'nextflow run foo -config /a.config -config /b.config'
        [remoteProfile: 'cluster']          | 'nextflow run foo -profile cluster'
        [profile: 'local']                  | 'nextflow run foo'
        [trace: 'nextflow.k8s', debug: 'nextflow.processor'] | 'nextflow -trace nextflow.k8s -debug nextflow.processor run foo'
        [jvmOpts: [foo: 'bar']]             | 'nextflow -Dfoo=bar run foo'
    }

    def 'should append script args' () {
        given:
        def l = new K8sDriverLauncher(options: new KubeRunOptions(params: [x: '1']), pipelineName: 'foo', args: ['a', 'b'])

        expect:
        l.getLaunchCli() == 'nextflow run foo --x=1 a b'
    }

    def 'should reject process executor option' () {
        given:
        def l = new K8sDriverLauncher(options: new KubeRunOptions(process: [executor: 'local']), pipelineName: 'foo')

        when:
        l.getLaunchCli()
        then:
        def e = thrown(AbortOperationException)
        e.message == 'Option `--process.executor` not supported with Kubernetes deployment'
    }

    def 'should set the run name' () {
        when:
        def l = new K8sDriverLauncher(options: new KubeRunOptions(runName: 'bar'), pipelineName: 'foo')
        then:
        l.getLaunchCli() == 'nextflow run foo -name bar'
    }

    def 'should put the run name after bare options and before script args' () {
        when:
        def l = new K8sDriverLauncher(options: new KubeRunOptions(runName: 'bar', withTrace: 'true', remoteProfile: 'x'), pipelineName: 'foo', args: ['a'])
        then:
        l.getLaunchCli() == 'nextflow run foo -with-trace -profile x -name bar a'
    }

    def 'should use interactive command for login' () {
        when:
        def l = new K8sDriverLauncher(pipelineName: 'login', interactive: true)
        then:
        l.getLaunchCli() == 'tail -f /dev/null'
    }

    def 'should create launcher spec pod' () {

        given:
        def pod = Mock(PodOptions)
        pod.getVolumeClaims() >> [ new PodVolumeClaim('pvc-1', '/mnt/path/data') ]
        pod.getMountConfigMaps() >> [ new PodMountConfig('cfg-2', '/mnt/path/cfg') ]

        def k8s = Mock(K8sConfig)
        k8s.getNextflowImageName() >> 'the-image'
        k8s.getLaunchDir() >> '/the/user/dir'
        k8s.getWorkDir() >> '/the/work/dir'
        k8s.getProjectDir() >> '/the/project/dir'
        k8s.getPodOptions() >> pod

        and:
        def driver = Spy(K8sDriverLauncher)
        driver.@options = new KubeRunOptions(runName: 'foo-boo')
        driver.@k8sClient = new K8sClient(new ClientConfig(namespace: 'foo', serviceAccount: 'bar'))
        driver.@k8sConfig = k8s

        when:
        def spec = driver.makeLauncherSpec()
        then:
        driver.getLaunchCli() >> 'nextflow run foo'

        spec == [
            apiVersion: 'v1',
            kind: 'Pod',
            metadata: [name:'foo-boo', namespace:'foo', labels:[app:'nextflow', runName:'foo-boo']],
            spec: [
                automountServiceAccountToken: false,
                restartPolicy: 'Never',
                containers: [
                    [
                        name: 'foo-boo',
                        image: 'the-image',
                        command: ['/bin/bash', '-c', "source /etc/nextflow/init.sh; nextflow run foo"],
                        env: [
                            [name:'NXF_WORK', value:'/the/work/dir'],
                            [name:'NXF_ASSETS', value:'/the/project/dir'],
                            [name:'NXF_EXECUTOR', value:'k8s'],
                            [name:'NXF_ANSI_LOG', value: 'false']
                        ],
                        volumeMounts: [
                            [name:'vol-1', mountPath:'/mnt/path/data'],
                            [name:'vol-2', mountPath:'/mnt/path/cfg']
                        ]
                    ]
                ],
                serviceAccountName: 'bar',
                volumes: [
                    [name:'vol-1', persistentVolumeClaim:[claimName:'pvc-1']],
                    [name:'vol-2', configMap:[name:'cfg-2']]
                ]
            ]
        ]

    }

    def 'should create launcher spec job' () {

        given:
        def pod = Mock(PodOptions)
        pod.getVolumeClaims() >> [ new PodVolumeClaim('pvc-1', '/mnt/path/data') ]
        pod.getMountConfigMaps() >> [ new PodMountConfig('cfg-2', '/mnt/path/cfg') ]

        def k8s = Mock(K8sConfig)
        k8s.getNextflowImageName() >> 'the-image'
        k8s.getLaunchDir() >> '/the/user/dir'
        k8s.getWorkDir() >> '/the/work/dir'
        k8s.getProjectDir() >> '/the/project/dir'
        k8s.getPodOptions() >> pod
        k8s.useJobResource() >> true

        and:
        def driver = Spy(K8sDriverLauncher)
        driver.@options = new KubeRunOptions(runName: 'foo-boo')
        driver.@k8sClient = new K8sClient(new ClientConfig(namespace: 'foo', serviceAccount: 'bar'))
        driver.@k8sConfig = k8s

        and:
        def metadata = [name: 'foo-boo', namespace: 'foo', labels: [app: 'nextflow', runName: 'foo-boo']]

        when:
        def spec = driver.makeLauncherSpec()
        then:
        driver.getLaunchCli() >> 'nextflow run foo'

        spec == [
            apiVersion: 'batch/v1',
            kind: 'Job',
            metadata: metadata,
            spec: [
                backoffLimit: 0,
                template: [
                    metadata: metadata,
                    spec: [
                        automountServiceAccountToken: false,
                        restartPolicy: 'Never',
                        containers: [
                            [
                                name: 'foo-boo',
                                image: 'the-image',
                                command: ['/bin/bash', '-c', "source /etc/nextflow/init.sh; nextflow run foo"],
                                env: [
                                    [name:'NXF_WORK', value:'/the/work/dir'],
                                    [name:'NXF_ASSETS', value:'/the/project/dir'],
                                    [name:'NXF_EXECUTOR', value:'k8s'],
                                    [name:'NXF_ANSI_LOG', value: 'false']
                                ],
                                volumeMounts: [
                                    [name:'vol-1', mountPath:'/mnt/path/data'],
                                    [name:'vol-2', mountPath:'/mnt/path/cfg']
                                ]
                            ]
                        ],
                        serviceAccountName: 'bar',
                        volumes: [
                            [name:'vol-1', persistentVolumeClaim:[claimName:'pvc-1']],
                            [name:'vol-2', configMap:[name:'cfg-2']]
                        ]
                    ]
                ]
            ]
        ]
    }

    def 'should use user provided pod image' () {

        given:
        def pod = Mock(PodOptions)
        pod.getVolumeClaims() >> [ new PodVolumeClaim('pvc-1', '/mnt/path/data') ]
        pod.getMountConfigMaps() >> [ new PodMountConfig('cfg-2', '/mnt/path/cfg') ]

        def k8s = Mock(K8sConfig)
        k8s.getLaunchDir() >> '/the/user/dir'
        k8s.getWorkDir() >> '/the/work/dir'
        k8s.getProjectDir() >> '/the/project/dir'
        k8s.getPodOptions() >> pod

        and:
        def driver = Spy(K8sDriverLauncher)
        driver.@options = new KubeRunOptions(runName: 'foo-boo', headImage: 'foo/bar')
        driver.@k8sClient = new K8sClient(new ClientConfig(namespace: 'foo', serviceAccount: 'bar'))
        driver.@k8sConfig = k8s

        when:
        def result = driver.makeLauncherSpec()
        then:
        driver.getLaunchCli() >> 'nextflow run foo'
        and:
        result.spec.containers[0].image == 'foo/bar'

    }

    def 'should use user provided head-cpu and head-memory request' () {
        given:
        def pod = Mock(PodOptions)
        pod.getVolumeClaims() >> [ new PodVolumeClaim('pvc-1', '/mnt/path/data') ]
        pod.getMountConfigMaps() >> [ new PodMountConfig('cfg-2', '/mnt/path/cfg') ]

        def k8s = Mock(K8sConfig)
        k8s.getNextflowImageName() >> 'the-image'
        k8s.getLaunchDir() >> '/the/user/dir'
        k8s.getWorkDir() >> '/the/work/dir'
        k8s.getProjectDir() >> '/the/project/dir'
        k8s.getPodOptions() >> pod

        and:
        def driver = Spy(K8sDriverLauncher)
        driver.@options = new KubeRunOptions(runName: 'foo-boo', headCpus: 2, headMemory: '200Mi')
        driver.@k8sClient = new K8sClient(new ClientConfig(namespace: 'foo', serviceAccount: 'bar'))
        driver.@k8sConfig = k8s

        when:
        def result = driver.makeLauncherSpec()
        then:
        driver.getLaunchCli() >> 'nextflow run foo'
        and:
        result.spec.containers[0].resources == [
            requests: [cpu: 2, memory: '200Mi'],
            limits: [memory: '200Mi']
        ]
    }

    def 'should use user provided head-cpu and head-memory limits' () {
        given:
        def pod = Mock(PodOptions)
        pod.getVolumeClaims() >> [ new PodVolumeClaim('pvc-1', '/mnt/path/data') ]
        pod.getMountConfigMaps() >> [ new PodMountConfig('cfg-2', '/mnt/path/cfg') ]

        def k8s = Mock(K8sConfig)
        k8s.getNextflowImageName() >> 'the-image'
        k8s.getLaunchDir() >> '/the/user/dir'
        k8s.getWorkDir() >> '/the/work/dir'
        k8s.getProjectDir() >> '/the/project/dir'
        k8s.getPodOptions() >> pod
        k8s.cpuLimitsEnabled() >> true

        and:
        def driver = Spy(K8sDriverLauncher)
        driver.@options = new KubeRunOptions(runName: 'foo-boo', headCpus: 2, headMemory: '200Mi')
        driver.@k8sClient = new K8sClient(new ClientConfig(namespace: 'foo', serviceAccount: 'bar'))
        driver.@k8sConfig = k8s

        when:
        def result = driver.makeLauncherSpec()
        then:
        driver.getLaunchCli() >> 'nextflow run foo'
        and:
        result.spec.containers[0].resources == [
            requests: [cpu: 2, memory: '200Mi'],
            limits: [cpu: 2, memory: '200Mi']
        ]
    }

    def 'should create config map' () {

        given:
        def folder = Files.createTempDirectory('foo')

        def params = folder.resolve('params.json')
        params.text = 'bla-bla'
        def driver = Spy(K8sDriverLauncher)
        def NXF_CONFIG = [foo: 'bar'].toConfigObject()

        def SCM_FILE = folder.resolve('scm')
        SCM_FILE.text = "hello = 'world'\n"


        def EXPECTED = [:]
        EXPECTED['init.sh'] == ''

        def POD_OPTIONS = new PodOptions()

        def K8S_CONFIG = Mock(K8sConfig)
        K8S_CONFIG.getLaunchDir() >> '/launch/dir'
        K8S_CONFIG.getPodOptions() >> POD_OPTIONS

        when:
        driver.@config = NXF_CONFIG
        driver.@k8sConfig = K8S_CONFIG
        driver.@options = new KubeRunOptions(paramsFile: params.toString())

        driver.createK8sConfigMap()
        then:
        1 * driver.getScmFile() >> SCM_FILE
        1 * driver.makeConfigMapName(_ as Map) >> 'nf-config-123'
        1 * driver.tryCreateConfigMap('nf-config-123', _ as Map) >> {  name, cfg ->
            assert cfg.'init.sh' == "mkdir -p '/launch/dir'; if [ -d '/launch/dir' ]; then cd '/launch/dir'; else echo 'Cannot create directory: /launch/dir'; exit 1; fi; [ -f /etc/nextflow/scm ] && ln -s /etc/nextflow/scm \$NXF_HOME/scm; [ -f /etc/nextflow/nextflow.config ] && cp /etc/nextflow/nextflow.config \$PWD/nextflow.config; "
            assert cfg.'nextflow.config' == "foo = 'bar'\n"
            assert cfg.'scm' == "hello = 'world'\n"
            assert cfg.'params.json' == 'bla-bla'
            return null
        }

        POD_OPTIONS.getMountConfigMaps() == [ new PodMountConfig('nf-config-123', '/etc/nextflow') ] as Set

        cleanup:
        folder?.deleteDir()
    }

    def 'should use the NXF_PARAMS_FILE params file' () {
        given:
        def folder = Files.createTempDirectory('foo')
        def params = folder.resolve('params.yml')
        params.text = 'x: 1'
        def POD_OPTIONS = new PodOptions()
        def K8S_CONFIG = Mock(K8sConfig)
        K8S_CONFIG.getLaunchDir() >> '/launch/dir'
        K8S_CONFIG.getPodOptions() >> POD_OPTIONS
        def driver = Spy(K8sDriverLauncher)
        driver.@config = new ConfigObject()
        driver.@k8sConfig = K8S_CONFIG
        driver.@options = new KubeRunOptions()
        driver.@pipelineName = 'foo'
        SysEnv.push(NXF_PARAMS_FILE: params.toString())

        when:
        driver.createK8sConfigMap()
        then:
        1 * driver.getScmFile() >> folder.resolve('scm')
        1 * driver.tryCreateConfigMap(_, _ as Map) >> { name, cfg ->
            assert cfg.'params.yml' == 'x: 1'
            return null
        }
        driver.getLaunchCli() == 'nextflow run foo -params-file /etc/nextflow/params.yml'

        cleanup:
        SysEnv.pop()
        folder?.deleteDir()
    }

    def 'should create config map with pre-script' () {

        given:
        def folder = Files.createTempDirectory('foo')

        def params = folder.resolve('params.json')
        params.text = 'bla-bla'
        def driver = Spy(K8sDriverLauncher)
        def NXF_CONFIG = [foo: 'bar'].toConfigObject()

        def SCM_FILE = folder.resolve('scm')
        SCM_FILE.text = "hello = 'world'\n"


        def EXPECTED = [:]
        EXPECTED['init.sh'] == ''

        def POD_OPTIONS = new PodOptions()

        def K8S_CONFIG = Mock(K8sConfig)
        K8S_CONFIG.getLaunchDir() >> '/launch/dir'
        K8S_CONFIG.getPodOptions() >> POD_OPTIONS

        when:
        driver.@config = NXF_CONFIG
        driver.@k8sConfig = K8S_CONFIG
        driver.@options = new KubeRunOptions(paramsFile: params.toString(), headPreScript: '/bin/foo.sh')

        driver.createK8sConfigMap()
        then:
        1 * driver.getScmFile() >> SCM_FILE
        1 * driver.makeConfigMapName(_ as Map) >> 'nf-config-123'
        1 * driver.tryCreateConfigMap('nf-config-123', _ as Map) >> {  name, cfg ->
            assert cfg.'init.sh' == "mkdir -p '/launch/dir'; if [ -d '/launch/dir' ]; then cd '/launch/dir'; else echo 'Cannot create directory: /launch/dir'; exit 1; fi; [ -f /etc/nextflow/scm ] && ln -s /etc/nextflow/scm \$NXF_HOME/scm; [ -f /etc/nextflow/nextflow.config ] && cp /etc/nextflow/nextflow.config \$PWD/nextflow.config; [ -f '/bin/foo.sh' ] && '/bin/foo.sh'; "
            assert cfg.'nextflow.config' == "foo = 'bar'\n"
            assert cfg.'scm' == "hello = 'world'\n"
            assert cfg.'params.json' == 'bla-bla'
            return null
        }

        POD_OPTIONS.getMountConfigMaps() == [ new PodMountConfig('nf-config-123', '/etc/nextflow') ] as Set

        cleanup:
        folder?.deleteDir()
    }


    def 'should make config' () {
        given:
        Map config
        def driver = Spy(K8sDriverLauncher)
        def NAME = 'somePipelineName'
        def CFG_EMPTY = new ConfigObject()
        def CFG_WITH_MOUNTS = new ConfigObject()
        CFG_WITH_MOUNTS.k8s.storageClaimName = 'pvc'
        CFG_WITH_MOUNTS.k8s.storageMountPath = '/foo'

        when:
        driver.@options = new KubeRunOptions()
        config = driver.makeConfig(NAME).toMap()
        then:
        1 *  driver.loadConfig(NAME) >> CFG_EMPTY
        config.process.executor == 'k8s'
        config.k8s.pod == null
        config.k8s.storageMountPath == null
        config.k8s.storageClaimName == null

        when:
        driver.@options = new KubeRunOptions()
        config = driver.makeConfig(NAME).toMap()
        then:
        1 *  driver.loadConfig(NAME) >> CFG_WITH_MOUNTS
        config.process.executor == 'k8s'
        config.k8s.storageClaimName == 'pvc'
        config.k8s.storageMountPath == '/foo'
        and:
        new K8sConfig(config.k8s).getStorageClaimName() == 'pvc'
        new K8sConfig(config.k8s).getStorageMountPath() == '/foo'
        new K8sConfig(config.k8s).getPodOptions() == new PodOptions([ [volumeClaim:'pvc', mountPath: '/foo'] ])

        when:
        driver.@options = new KubeRunOptions(volMounts: ['pvc-1:/this','pvc-2:/that'] )
        config = driver.makeConfig(NAME).toMap()
        then:
        1 *  driver.loadConfig(NAME) >> CFG_EMPTY
        config.process.executor == 'k8s'
        config.k8s.storageClaimName == 'pvc-1'
        config.k8s.storageMountPath == '/this'
        config.k8s.pod == [ [volumeClaim: 'pvc-2', mountPath: '/that'] ]
        and:
        new K8sConfig(config.k8s).getStorageClaimName() == 'pvc-1'
        new K8sConfig(config.k8s).getStorageMountPath() == '/this'
        new K8sConfig(config.k8s).getPodOptions() == new PodOptions([
                [volumeClaim:'pvc-1', mountPath: '/this'],
                [volumeClaim:'pvc-2', mountPath: '/that']
        ])


        when:
        driver.@options = new KubeRunOptions(volMounts: ['xyz:/this'] )
        config = driver.makeConfig(NAME).toMap()
        then:
        1 *  driver.loadConfig(NAME) >> CFG_WITH_MOUNTS
        config.process.executor == 'k8s'
        config.k8s.storageClaimName == 'xyz'
        config.k8s.storageMountPath == '/this'
        config.k8s.pod == null
        and:
        new K8sConfig(config.k8s).getStorageClaimName() == 'xyz'
        new K8sConfig(config.k8s).getStorageMountPath() == '/this'
        new K8sConfig(config.k8s).getPodOptions() == new PodOptions([
                [volumeClaim:'xyz', mountPath: '/this']
        ])


        when:
        driver.@options = new KubeRunOptions(volMounts: ['xyz', 'bar:/mnt/bar'] )
        config = driver.makeConfig(NAME).toMap()
        then:
        1 *  driver.loadConfig(NAME) >> CFG_WITH_MOUNTS
        config.process.executor == 'k8s'
        config.k8s.storageClaimName == 'xyz'
        config.k8s.storageMountPath == null
        config.k8s.pod == [ [volumeClaim: 'bar', mountPath: '/mnt/bar'] ]
        and:
        new K8sConfig(config.k8s).getStorageClaimName() == 'xyz'
        new K8sConfig(config.k8s).getStorageMountPath() == '/workspace'
        new K8sConfig(config.k8s).getPodOptions() == new PodOptions([
                [volumeClaim:'xyz', mountPath: '/workspace'],
                [volumeClaim:'bar', mountPath: '/mnt/bar']
        ])

    }

    def 'should add the plugin into the config' () {
        when:
        def l = new K8sDriverLauncher(options: new KubeRunOptions(plugins: 'nf-cws@1.0.0', runName: 'bar'))
        then:
        l.makeConfig( "/bar").get('plugins') == [ 'nf-cws@1.0.0' ]
    }

    def 'should set the namespace and work dir' () {
        given:
        def driver = Spy(K8sDriverLauncher)
        driver.@options = new KubeRunOptions(namespace: 'my-ns', workDir: '/mnt/work')

        when:
        def config = driver.makeConfig('foo').toMap()
        then:
        1 * driver.loadConfig('foo') >> new ConfigObject()
        config.k8s.namespace == 'my-ns'
        config.k8s.workDir == '/mnt/work'
    }

    def 'should set stub run in the config' () {
        given:
        def driver = Spy(K8sDriverLauncher)
        driver.@options = new KubeRunOptions(stubRun: true)

        when:
        def config = driver.makeConfig('foo').toMap()
        then:
        1 * driver.loadConfig('foo') >> new ConfigObject()
        config.stubRun == true
    }

    def 'should override the config work dir' () {
        given:
        def driver = Spy(K8sDriverLauncher)
        def CFG = new ConfigObject()
        CFG.workDir = '/from/config'

        when:
        driver.@options = new KubeRunOptions(workDir: '/from/cli')
        def config = driver.makeConfig('foo').toMap()
        then:
        1 * driver.loadConfig('foo') >> CFG
        config.workDir == '/from/cli'
        config.k8s.workDir == '/from/cli'
    }

    def 'should use the config work dir' () {
        given:
        def driver = Spy(K8sDriverLauncher)
        def CFG = new ConfigObject()
        CFG.workDir = '/from/config'
        SysEnv.push(NXF_WORK: '/from/env')

        when:
        driver.@options = new KubeRunOptions()
        def config = driver.makeConfig('foo').toMap()
        then:
        1 * driver.loadConfig('foo') >> CFG
        config.workDir == '/from/config'
        config.k8s.workDir == '/from/config'

        cleanup:
        SysEnv.pop()
    }

    def 'should use the NXF_WORK work dir' () {
        given:
        def driver = Spy(K8sDriverLauncher)
        SysEnv.push(NXF_WORK: WORK)

        when:
        driver.@options = new KubeRunOptions()
        def config = driver.makeConfig('foo').toMap()
        then:
        1 * driver.loadConfig('foo') >> new ConfigObject()
        config.workDir == EXPECTED
        config.k8s.workDir == EXPECTED

        cleanup:
        SysEnv.pop()

        where:
        WORK            | EXPECTED
        '/from/env'     | '/from/env'
        'work'          | null
    }

    def 'should resolve config files' () {
        given:
        def folder = Files.createTempDirectory('test')
        def home = Files.createDirectories(folder.resolve('home'))
        def launch = Files.createDirectories(folder.resolve('launch'))
        def homeConfig = home.resolve('config'); homeConfig.text = 'a = 1'
        def localConfig = launch.resolve('nextflow.config'); localConfig.text = 'b = 1'
        def pipelineConfig = folder.resolve('pipeline.config'); pipelineConfig.text = 'c = 1'
        def userConfig = launch.resolve('user.config'); userConfig.text = 'd = 1'
        and:
        def driver = Spy(K8sDriverLauncher)
        driver.getHomeDir() >> home
        driver.getCurrentDir() >> launch

        when:
        driver.@options = new KubeRunOptions(config: ['user.config'])
        def files = driver.configFiles('foo')
        then:
        1 * driver.getPipelineConfig('foo') >> pipelineConfig
        files == [homeConfig, localConfig, pipelineConfig, userConfig]

        when:
        driver.@options = new KubeRunOptions(config: ['user.config'], remoteProfile: 'x')
        files = driver.configFiles('foo')
        then:
        0 * driver.getPipelineConfig(_)
        files == [homeConfig, localConfig, userConfig]

        when:
        driver.@options = new KubeRunOptions()
        files = driver.configFiles('/abs/project')
        then:
        0 * driver.getPipelineConfig(_)
        files == [homeConfig, localConfig]

        when:
        driver.@options = new KubeRunOptions(config: ['missing.config'], remoteProfile: 'x')
        driver.configFiles('foo')
        then:
        thrown(AbortOperationException)

        cleanup:
        folder?.deleteDir()
    }

    def 'should use the NXF_CONFIG_FILE local config' () {
        given:
        def folder = Files.createTempDirectory('test')
        def custom = folder.resolve('custom.config'); custom.text = 'a = 1'
        folder.resolve('nextflow.config').text = 'b = 1'
        def driver = Spy(K8sDriverLauncher)
        driver.getHomeDir() >> folder.resolve('home')
        driver.getCurrentDir() >> folder
        SysEnv.push(NXF_CONFIG_FILE: 'custom.config')

        when:
        driver.@options = new KubeRunOptions(remoteProfile: 'x')
        def files = driver.configFiles('foo')
        then:
        files == [custom]

        cleanup:
        SysEnv.pop()
        folder?.deleteDir()
    }

    def 'should return pod exit status' () {
        given:
        def POD_NAME = 'pod-x'
        def client = Mock(K8sClient)
        def driver = Spy(K8sDriverLauncher)
        driver.@k8sClient = client
        driver.@options = new KubeRunOptions(runName: POD_NAME)
        driver.@k8sConfig = Mock(K8sConfig)

        when:
        def status = driver.waitPodTermination()
        then:
        1 * client.podState(POD_NAME) >> [terminated: [exitCode: 99]]
        1 * driver.k8sConfig.useJobResource() >> [:]
        then:
        status == 99

        when:
        status = driver.waitPodTermination()
        then:
        1 * client.podState(POD_NAME) >> [:]
        then:
        1 * client.podState(POD_NAME) >> [terminated: [exitCode: 99]]
        then:
        status == 99

        when:
        status = driver.waitPodTermination()
        then:
        1 * client.podState(POD_NAME) >> [:]
        1 * driver.isWaitTimedOut(_) >> true
        then:
        status == 127
    }


    def 'should delete configMap' () {
        given:
        def POD_NAME = 'pod-x'
        def config = Mock(K8sConfig)
        def driver = Spy(K8sDriverLauncher)
        driver.@k8sConfig = config
        driver.@options = new KubeRunOptions(runName: POD_NAME)

        when:
        driver.shutdown()
        then:
        1 * driver.waitPodTermination() >> 0
        then:
        1 * config.getCleanup(true) >> true
        1 * driver.deleteConfigMap() >> { }

        when:
        driver.shutdown()
        then:
        1 * driver.waitPodTermination() >> 1
        then:
        1 * config.getCleanup(false) >> true
        1 * driver.deleteConfigMap() >> { }

        when:
        driver.shutdown()
        then:
        1 * driver.waitPodTermination() >> 1
        then:
        1 * config.getCleanup(false) >> false
        0 * driver.deleteConfigMap() >> { }

    }
}
