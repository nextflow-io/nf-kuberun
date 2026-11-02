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

import groovy.transform.MapConstructor

import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths

import com.google.common.hash.Hashing
import groovy.util.logging.Slf4j
import nextflow.Const
import nextflow.SysEnv
import nextflow.config.ConfigBuilder
import nextflow.exception.AbortOperationException
import nextflow.file.FileHelper
import nextflow.k8s.K8sConfig
import nextflow.k8s.client.K8sClient
import nextflow.k8s.client.K8sResponseException
import nextflow.k8s.model.PodEnv
import nextflow.k8s.model.PodMountConfig
import nextflow.k8s.model.PodSpecBuilder
import nextflow.k8s.model.ResourceType
import nextflow.scm.AssetManager
import nextflow.scm.ProviderConfig
import nextflow.util.ConfigHelper
import nextflow.util.Escape
/**
 * Configure and submit the execution of pod running the Nextflow main application
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@MapConstructor(includeFields = true)
class K8sDriverLauncher {

    /**
     * Either a Pod or Job
     */
    private ResourceType resourceType = ResourceType.Pod

    /**
     * Workflow project to launch
     */
    private String pipelineName

    /**
     * Command options
     */
    private KubeRunOptions options = new KubeRunOptions()

    /**
     * Kubernetes client
     */
    private K8sClient k8sClient

    /**
     * Nextflow resolved config object
     */
    private ConfigObject config

    /**
     * Name of the config map used to propagate the nextflow
     * setting in the container
     */
    private String configMapName

    /**
     * Kubernetes specific config settings
     */
    private K8sConfig k8sConfig

    private String paramsFile

    private boolean interactive

    /**
     * Workflow script positional parameters
     */
    private List<String> args

    /**
     * Launcher entry point. Set-up the environment and create a pod that run the Nextflow
     * application (which in turns executed each task as a pod)
     *
     * @param name Workflow project entry name
     * @param args Workflow script positional parameters
     */
    void run(String name, List<String> args) {
        this.args = args
        this.pipelineName = name
        this.interactive = name == 'login'
        if( options.background && interactive )
            throw new AbortOperationException("Option --background conflicts with interactive mode")
        this.config = makeConfig(pipelineName)
        this.k8sConfig = makeK8sConfig(config.toMap())
        this.k8sClient = makeK8sClient(k8sConfig)
        this.k8sConfig.checkStorageAndPaths(k8sClient)
        createK8sConfigMap()
        createK8sLauncherPod()
        waitPodStart()
        // login into container session
        if( interactive )
            launchLogin()
        // dump pod output
        else if( !options.background )
            printK8sPodOutput()
        else
            log.debug "Nextflow driver launched in background mode -- pod: $options.runName"
        waitPodEnd()
    }

    int shutdown() {
        if( options.background )
            return 0
        // fetch the container exit status
        final exitCode = waitPodTermination()
        // cleanup the config map if OK
        def deleteOnSuccessByDefault = exitCode==0
        if( k8sConfig.getCleanup(deleteOnSuccessByDefault)  ) {
            deleteConfigMap()
        }
        return exitCode
    }

    protected void waitPodEnd() {
        if( options.background )
            return
        final currentState = k8sConfig.useJobResource() ? k8sClient.jobState(options.runName) : k8sClient.podState(options.runName)
        if (currentState && currentState?.running instanceof Map) {
            final name = options.runName
            println "${resourceType} running: $name ... waiting for ${resourceType.lower()} to stop running"
            try {
                while( true ) {
                    sleep 10000
                    final state = k8sConfig.useJobResource() ? k8sClient.jobState(name) : k8sClient.podState(name)
                    if ( state && !(state?.running instanceof Map) )  {
                        println "${resourceType} $name has changed from running state $state"
                        break
                    }
                }
            }
            catch( Exception e ) {
                log.warn "Caught exception while waiting for ${resourceType.lower()} to stop running"
            }
        }
    }

    protected boolean isWaitTimedOut(long time) {
        System.currentTimeMillis()-time > 90_000
    }

    protected int waitPodTermination() {
        log.debug "Wait for ${resourceType.lower()} termination name=$options.runName"
        final rnd = new Random()
        final time = System.currentTimeMillis()
        Map state = null
        try {
            while( true ) {
                sleep rnd.nextInt(500)
                state = k8sConfig.useJobResource() ? k8sClient.jobState(options.runName) : k8sClient.podState(options.runName)
                if( state?.terminated instanceof Map  )
                    return state.terminated.exitCode as int

                else if( isWaitTimedOut(time) )
                    throw new IllegalStateException("Timeout waiting for ${resourceType.lower()} terminated state=$state")
            }
        }
        catch( Exception e ) {
            log.warn "Unable to fetch ${resourceType.lower()} exit status -- ${resourceType.lower()}=$options.runName state=$state"
            return 127
        }
    }

    protected void deleteConfigMap() {
        try {
            k8sClient.configDelete(configMapName)
            log.debug "Deleted K8s configMap with name: $configMapName"
        }
        catch ( Exception e ) {
            log.warn "Unable to delete configMap: $configMapName", e
        }
    }

    protected void waitPodStart() {
        final name = options.runName
        print "${resourceType} submitted: $name .. waiting to start"
        while( true ) {
            sleep 1000
            final state = k8sConfig.useJobResource() ? k8sClient.jobState(name) : k8sClient.podState(name)
            if( state && !state.containsKey('waiting')  ) {
                break
            }
        }
        print "\33[2K\r"
        println "${resourceType} started: $name"
    }

    /**
     * Wait for the driver pod creation and prints the log to the
     * console standard output
     */
    protected void printK8sPodOutput() {
        if ( k8sConfig.useJobResource() )
            k8sClient.jobLog(options.runName, follow:true).eachLine { println it }
        else
            k8sClient.podLog(options.runName, follow:true).eachLine { println it }
    }

    protected ConfigObject loadConfig( String pipelineName ) {
        final builder = new ConfigBuilder()
                .setShowClosures(true)
                .setProfile(options.profile)
        return builder.build(options.env, configFiles(pipelineName))
    }

    /**
     * Resolve the local config files in the same order as the `run` command:
     * the user home config, the launch directory config, the pipeline config,
     * then any user-specified config files.
     */
    protected List<Path> configFiles( String pipelineName ) {
        final List<Path> result = []

        final home = Const.APP_HOME_DIR.resolve('config')
        if( home.exists() )
            result << home

        final local = Path.of(SysEnv.get('NXF_CONFIG_FILE') ?: 'nextflow.config').toAbsolutePath().normalize()
        if( local.exists() )
            result << local

        if( !interactive && !pipelineName.startsWith('/') && !options.remoteProfile && !options.remoteConfig ) {
            final pipelineConfig = new AssetManager(pipelineName, options.revision, options.mainScript, options.toHubOptions()).getConfigFile()
            if( pipelineConfig )
                result << pipelineConfig
        }

        for( final it : options.config ) {
            final file = Path.of(it).toAbsolutePath().normalize()
            if( !file.exists() )
                throw new AbortOperationException("The specified configuration file does not exist: $file -- check the name or choose another file")
            result << file
        }

        return result
    }

    protected K8sConfig makeK8sConfig(Map config) {
        config.k8s instanceof Map ? new K8sConfig(config.k8s as Map) : new K8sConfig()
    }

    protected makeK8sClient( K8sConfig k8sConfig ) {
        new K8sClient(k8sConfig.getClient())
    }

    /**
     * Retrieve the workflow configuration and merge with the current local one.
     *
     * @param pipelineName Workflow project name
     * @return A {@link Map} modeling the execution configuration settings
     */
    protected ConfigObject makeConfig(String pipelineName) {

        def file = new File(pipelineName)
        if( !interactive && file.exists() ) {
            def message = "The k8s executor cannot run local ${file.directory ? 'project' : 'script'}: $pipelineName"
            message += " -- provide the absolute path of a project available in the Kubernetes cluster or the URL of a project hosted in a Git repository"
            throw new AbortOperationException(message)
        }

        def config = loadConfig(pipelineName)

        // normalize pod entries
        def k8s = config.k8s

        if( !k8s.isSet('pod') )
            k8s.pod = []
        else if( k8s.pod instanceof Map ) {
            k8s.pod = [ k8s.pod ]
        }
        else if( !(k8s.pod instanceof List) )
            throw new IllegalArgumentException("Illegal k8s.pod configuratun value: ${k8s.pod}")

        // -- use the volume claims specified in the command line
        //   to populate the pod config
        for( int i=0; i<options.volMounts.size(); i++ ){
            def entry = options.volMounts.get(i)
            def parts = entry.tokenize(':')
            def name = parts[0]
            def path = parts[1]
            if( i==0 ) {
                k8s.storageClaimName = name
                k8s.storageMountPath = path
            }
            else {
                k8s.pod.add( [volumeClaim: name, mountPath: path] )
            }
        }

        if( options.namespace )
            k8s.namespace = options.namespace

        // -- set k8s executor
        config.process.executor = 'k8s'

        // -- strip default work dir
        if( config.workDir == 'work' )
            config.remove('workDir')

        // -- check work dir
        if( options.workDir )
            k8s.workDir = options.workDir
        else if( !k8s.isSet('workDir') && config.workDir )
            k8s.workDir = config.workDir

        if( options.plugins ) {
            final List<String> plugins = config.plugins ?: []
            plugins.addAll( options.plugins.tokenize(',') )
            config.plugins = plugins
        }

        // -- some cleanup
        if( !k8s.pod )
            k8s.remove('pod')

        if( !k8s.storageClaimName )
            k8s.remove('storageClaimName')
        if( !k8s.storageMountPath )
            k8s.remove('storageMountPath')

        if( !config.libDir )
            config.remove('libDir')

        log.trace "K8s config object:\n${ConfigHelper.toCanonicalString(config).indent('  ')}"
        return config
    }


    private static void addOption(List<String> result, String name, value) {
        if( value != null && value != '' && value != false )
            result << "$name ${Escape.wildcards(String.valueOf(value))}".toString()
    }

    private static void addFlagOption(List<String> result, String name, String value) {
        if( value == 'true' )
            result << "$name -".toString()
        else
            addOption(result, name, value)
    }

    private static void addOptions(List<String> result, String prefix, Map<String,String> values) {
        for( final entry : values )
            result << "$prefix${entry.key} ${Escape.wildcards(String.valueOf(entry.value))}".toString()
    }

    /**
     * @return The nextflow driver command line
     */
    protected String getLaunchCli() {
        assert pipelineName

        if( interactive ) {
            return "tail -f /dev/null"
        }

        if( options.process.containsKey('executor') )
            throw new AbortOperationException("Option `--process.executor` not supported with Kubernetes deployment")

        final List<String> result = []
        // -- configure NF command line
        result << "nextflow"

        if( options.trace )
            result << "-trace ${options.trace}".toString()
        if( options.debug )
            result << "-debug ${options.debug}".toString()
        options.jvmOpts.each { k,v -> result << "-D$k=$v".toString() }

        result << "run"
        result << pipelineName

        if( options.runName )
            result << '-name' << options.runName

        if( options.cacheable == false )
            result << '-cache false'
        if( options.resume )
            result << "-resume ${options.resume == 'true' ? 'last' : options.resume}".toString()
        addOption(result, '-ps', options.poolSize)
        addOption(result, '-pi', options.pollInterval)
        addOption(result, '-qs', options.queueSize)
        addOption(result, '-r', options.revision)
        if( options.latest )
            result << '-latest'
        addOption(result, '-main-script', options.mainScript)
        addOption(result, '-hub', options.hubProvider)
        addFlagOption(result, '-with-trace', options.withTrace)
        addFlagOption(result, '-with-timeline', options.withTimeline)
        addFlagOption(result, '-with-dag', options.withDag)
        addFlagOption(result, '-dump-hashes', options.dumpHashes)
        addOption(result, '-dump-channels', options.dumpChannels)
        addOptions(result, '-e.', options.env)
        addOptions(result, '-process.', options.process)
        addOptions(result, '--', options.params)
        addOption(result, '-entry', options.entryName)

        if( paramsFile )
            result << "-params-file $paramsFile".toString()

        for( final it : options.remoteConfig )
            result << "-config $it".toString()

        if( options.remoteProfile )
            result << "-profile ${options.remoteProfile}".toString()

        if( args )
            result.addAll(args)

        return result.join(' ')
    }

    /**
     * @return A {@link Map} modeling driver pod specification
     */
    protected Map makeLauncherSpec() {
        assert options.runName
        assert k8sClient

        // -- setup config file
        String cmd = "source /etc/nextflow/init.sh; ${getLaunchCli()}"

        // create the launcher pod
        PodSpecBuilder builder = new PodSpecBuilder()
            .withPodName(options.runName)
            .withImageName(options.headImage ?: k8sConfig.getNextflowImageName())
            .withCommand(['/bin/bash', '-c', cmd])
            .withLabels([ app: 'nextflow', runName: options.runName ])
            .withNamespace(k8sClient.config.namespace)
            .withServiceAccount(k8sClient.config.serviceAccount)
            .withPodOptions(k8sConfig.getPodOptions())
            .withEnv( PodEnv.value('NXF_WORK', k8sConfig.getWorkDir()) )
            .withEnv( PodEnv.value('NXF_ASSETS', k8sConfig.getProjectDir()) )
            .withEnv( PodEnv.value('NXF_EXECUTOR', 'k8s'))
            .withEnv( PodEnv.value('NXF_ANSI_LOG', 'false'))
            .withMemory(options.headMemory?:"")
            .withCpus(options.headCpus)
            .withCpuLimits(k8sConfig.cpuLimitsEnabled())

        if ( k8sConfig.useJobResource()) {
            this.resourceType = ResourceType.Job
            return builder.buildAsJob()
        }
        else {
            return builder.build()
        }

        // note: do *not* set the work directory because it may need to be created  by the init script
    }

    /**
     * Creates and executes the nextflow driver pod
     * @return A {@link nextflow.k8s.client.K8sResponseJson} response object
     */
    protected createK8sLauncherPod() {
        final spec = makeLauncherSpec()
        if ( k8sConfig.useJobResource() ) {
            k8sClient.jobCreate(spec, yamlDebugPath())
        } else {
            k8sClient.podCreate(spec, yamlDebugPath())
        }
    }

    protected Path yamlDebugPath() {
        boolean debug = config.k8s.debug?.yaml?.toString() == 'true'
        final result = debug ? Paths.get(".nextflow.${resourceType.lower()}.yaml") : null
        if( result )
            log.info "Launcher ${resourceType.lower()} spec file: $result"
        return result
    }

    protected Path getScmFile() {
        ProviderConfig.getScmConfigPath()
    }

    String getRunName() {
        return options.runName
    }

    KubeRunOptions getOptions() {
        return options
    }

    protected String getPipelineName() {
        return pipelineName
    }

    protected boolean getInteractive() {
        return interactive
    }

    protected ConfigObject getConfig() {
        return config
    }

    protected K8sConfig getK8sConfig() {
        return k8sConfig
    }

    protected K8sClient getK8sClient() {
        return k8sClient
    }

    /**
     * Creates a K8s ConfigMap to share the nextflow configuration in the K8s cluster
     */
    protected void createK8sConfigMap() {
        Map<String,String> configMap = [:]

        final launchDir = k8sConfig.getLaunchDir()
        // init file
        String initScript = ''
        initScript += "mkdir -p '$launchDir'; if [ -d '$launchDir' ]; then cd '$launchDir'; else echo 'Cannot create directory: $launchDir'; exit 1; fi; "
        initScript += '[ -f /etc/nextflow/scm ] && ln -s /etc/nextflow/scm $NXF_HOME/scm; '
        initScript += '[ -f /etc/nextflow/nextflow.config ] && cp /etc/nextflow/nextflow.config $PWD/nextflow.config; '
        if( options.headPreScript )
            initScript += "[ -f '$options.headPreScript' ] && '$options.headPreScript'; "
        configMap['init.sh'] = initScript

        // nextflow config file
        if( this.config ) {
            configMap['nextflow.config'] = ConfigHelper.toCanonicalString( this.config )
        }

        // scm config file
        final scmFile = getScmFile()
        if( scmFile.exists() ) {
            configMap['scm']  = scmFile.text
        }

        // params file
        if( options.paramsFile ) {
            final file = FileHelper.asPath(options.paramsFile)
            if( !file.exists() ) throw new NoSuchFileException("Params file does not exist: $file")
            configMap[ file.getName() ] = file.text
            paramsFile = "/etc/nextflow/$file.name"
        }

        // create the config map
        configMapName = makeConfigMapName(configMap)
        tryCreateConfigMap(configMapName, configMap)
        log.debug "Created K8s configMap with name: $configMapName"
        k8sConfig.getPodOptions().getMountConfigMaps().add( new PodMountConfig(configMapName, '/etc/nextflow') )
    }

    protected void tryCreateConfigMap(String name, Map data) {
        try {
            k8sClient.configCreate(name, data)
        }
        catch( K8sResponseException e ) {
            if( e.response.reason != 'AlreadyExists' )
                throw e
        }
    }

    protected String makeConfigMapName( Map configMap ) {
        "nf-config-${hash(configMap.values())}"
    }

    protected String hash(Collection<String> text) {
        def hasher = Hashing .murmur3_32() .newHasher()
        def itr = text.iterator()
        while( itr.hasNext() ) {
            hasher.putUnencodedChars(itr.next())
        }

        return hasher.hash().toString()
    }

    protected void launchLogin() {
        def launchDir = k8sConfig.getLaunchDir()
        def cmd = "kubectl -n ${k8sClient.config.namespace} exec -it $options.runName -- /bin/bash -c 'cd $launchDir; exec bash --login -i'"
        def proc = new ProcessBuilder().command('bash','-c',cmd).inheritIO().start()
        def result = proc.waitFor()
        if( result == 0 ) {
            if ( k8sConfig.useJobResource() )
                k8sClient.jobDelete(options.runName)
            else
                k8sClient.podDelete(options.runName)
        }
    }
}
