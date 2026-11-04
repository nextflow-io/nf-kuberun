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

import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.exception.AbortOperationException
import nextflow.scm.HubOptions
import nextflow.util.HistoryFile

/**
 * Options of the `run` plugin command.
 *
 * The `plugin` command only forwards `--key=value` options, which it
 * appends as `--key value` pairs after the positional arguments.
 * Known options are mapped to the fields below; any other option is
 * treated as a pipeline parameter.
 */
@Slf4j
@CompileStatic
class KubeRunOptions {

    static final private Pattern POD_NAME = ~/[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*/

    static final private Pattern RUN_NAME = Pattern.compile(/^[a-z](?:[a-z\d]|[-_](?=[a-z\d])){0,79}$/, Pattern.CASE_INSENSITIVE)

    /**
     * Options of the `run` command that cannot be used with kuberun
     */
    static final private Set<String> UNSUPPORTED = [
        'E', 'export-sys-env', 'lib', 'stdin', 'test', 'with-apptainer', 'with-docker',
        'with-mpi', 'with-singularity', 'without-docker'
    ] as Set<String>

    /**
     * Options of the `run` command that have no effect with kuberun
     */
    static final private Set<String> IGNORED = [
        'ansi', 'ansi-log', 'without-podman'
    ] as Set<String>

    static final private List<String> UNSUPPORTED_PREFIXES = ['cluster.', 'executor.']

    // -- positional arguments

    String pipeline

    List<String> args = []

    // -- kuberun options

    String namespace

    List<String> volMounts = []

    String headImage

    int headCpus

    String headMemory

    String headPreScript

    List<String> remoteConfig = []

    String remoteProfile

    boolean background

    // -- launcher options

    List<String> config = []

    String trace

    String debug

    Map<String,String> jvmOpts = [:]

    // -- run options

    String runName

    Boolean cacheable

    String resume

    Integer poolSize

    String pollInterval

    Integer queueSize

    String workDir

    String paramsFile

    Map<String,String> process = [:]

    Map<String,String> env = [:]

    String revision

    boolean latest

    String withTrace

    String withTimeline

    String withDag

    String profile

    String dumpHashes

    String dumpChannels

    String entryName

    String mainScript

    String plugins

    String hubProvider

    String hubUser

    String outputDir

    String outputFormat

    String bucketDir

    String withCloudcache

    String withTower

    String withWave

    String withFusion

    String withReport

    String withNotification

    String withConda

    boolean withoutConda

    String withSpack

    boolean withoutSpack

    String withPodman

    String withCharliecloud

    boolean withAgentTrace

    boolean stubRun

    boolean preview

    boolean offline

    Integer deep

    boolean disableJobsCancellation

    Map<String,String> params = [:]

    static KubeRunOptions parse(List<String> args) {
        final result = new KubeRunOptions()
        int i = 0
        final positional = new ArrayList<String>()
        while( i < args.size() && !args[i].startsWith('--') )
            positional << args[i++]
        while( i < args.size() ) {
            final key = args[i++]
            if( !key.startsWith('--') || key.size() == 2 )
                throw new AbortOperationException("Unexpected argument: $key -- options must be specified as `--name=value`")
            if( i == args.size() )
                throw new AbortOperationException("Missing value for option: $key")
            result.set(key.substring(2), args[i++])
        }
        if( positional ) {
            result.pipeline = positional[0]
            result.args = positional.size() > 1 ? positional[1..-1] : []
        }
        return result
    }

    protected void set(String name, String value) {
        switch( name ) {
            case 'namespace':           namespace = value; break
            case 'volume-mount':        volMounts.addAll(tokenize(value)); break
            case 'head-image':          headImage = value; break
            case 'head-cpus':           headCpus = toInt(name, value); break
            case 'head-memory':         headMemory = value; break
            case 'head-prescript':      headPreScript = value; break
            case 'remote-config':       remoteConfig.addAll(tokenize(value)); break
            case 'remote-profile':      remoteProfile = value; break
            case 'background':          background = toBool(name, value); break
            case 'config':              config.addAll(tokenize(value)); break
            case 'trace':               trace = value; break
            case 'debug':               debug = value; break
            case 'name':                runName = value; break
            case 'cache':               cacheable = toBool(name, value); break
            case 'resume':              resume = value; break
            case 'pool-size':           poolSize = toInt(name, value); break
            case 'poll-interval':       pollInterval = value; break
            case 'queue-size':          queueSize = toInt(name, value); break
            case 'work-dir':            workDir = value; break
            case 'params-file':         paramsFile = value; break
            case 'revision':            revision = value; break
            case 'latest':              latest = toBool(name, value); break
            case 'with-trace':          withTrace = value; break
            case 'with-timeline':       withTimeline = value; break
            case 'with-dag':            withDag = value; break
            case 'profile':             profile = value; break
            case 'dump-hashes':         dumpHashes = value; break
            case 'dump-channels':       dumpChannels = value; break
            case 'entry':               entryName = value; break
            case 'main-script':         mainScript = value; break
            case 'plugins':             plugins = value; break
            case 'hub':                 hubProvider = value; break
            case 'user':                hubUser = value; break
            case 'output-dir':          outputDir = value; break
            case 'output-format':       outputFormat = value; break
            case 'bucket-dir':          bucketDir = value; break
            case 'with-cloudcache':     withCloudcache = value; break
            case 'with-tower':          withTower = value; break
            case 'with-wave':           withWave = value; break
            case 'with-fusion':         withFusion = String.valueOf(toBool(name, value)); break
            case 'with-report':         withReport = value; break
            case 'with-notification':   withNotification = value; break
            case 'with-conda':          withConda = value; break
            case 'without-conda':       withoutConda = toBool(name, value); break
            case 'with-spack':          withSpack = value; break
            case 'without-spack':       withoutSpack = toBool(name, value); break
            case 'with-podman':         withPodman = value; break
            case 'with-charliecloud':   withCharliecloud = value; break
            case 'with-agent-trace':    withAgentTrace = toBool(name, value); break
            case 'stub':
            case 'stub-run':            stubRun = toBool(name, value); break
            case 'preview':             preview = toBool(name, value); break
            case 'offline':             offline = toBool(name, value); break
            case 'deep':                deep = toInt(name, value); break
            case 'disable-jobs-cancellation': disableJobsCancellation = toBool(name, value); break
            case 'with-weblog':
                throw new AbortOperationException("Option `--with-weblog` is no longer supported -- use the `nf-weblog` plugin instead, see https://github.com/nextflow-io/nf-weblog")
            default:
                if( name.startsWith('process.') )
                    process.put(name.substring('process.'.size()), value)
                else if( name.startsWith('env.') )
                    env.put(name.substring('env.'.size()), value)
                else if( name.startsWith('jvm.') )
                    jvmOpts.put(name.substring('jvm.'.size()), value)
                else if( name.startsWith('params.') )
                    params.put(name.substring('params.'.size()), value)
                else if( name in UNSUPPORTED || UNSUPPORTED_PREFIXES.any { String it -> name.startsWith(it) } )
                    throw new AbortOperationException("Option `--$name` not supported with Kubernetes deployment")
                else if( name in IGNORED )
                    log.warn "Option `--$name` has no effect with Kubernetes deployment"
                else
                    params.put(name, value)
        }
    }

    HubOptions toHubOptions() {
        return new HubOptions(hubProvider, hubUser)
    }

    /**
     * Validate the run name, or generate one if not specified,
     * so that it can be used as the driver pod name.
     *
     * Unlike `run`, the name is not checked against the local history,
     * since the run is recorded in the history of the driver pod.
     */
    void checkRunName() {
        if( runName && !POD_NAME.matcher(runName).matches() )
            throw new AbortOperationException("Not a valid K8s pod name -- It can only contain lower case alphanumeric characters, '-' or '.', and must start and end with an alphanumeric character")
        if( runName == 'last' )
            throw new AbortOperationException("Not a valid run name: `last`")
        if( runName && !RUN_NAME.matcher(runName).matches() )
            throw new AbortOperationException("Not a valid run name: `$runName` -- It must match the pattern $RUN_NAME")
        if( !runName ) {
            if( HistoryFile.disabled() )
                throw new AbortOperationException("Missing workflow run name")
            runName = HistoryFile.DEFAULT.generateNextName()
        }
        runName = runName.replace('_','-')
    }

    private static List<String> tokenize(String value) {
        return value.tokenize(',')*.trim().findAll { String it -> it } as List<String>
    }

    private static int toInt(String name, String value) {
        if( !value.isInteger() )
            throw new AbortOperationException("Option `--$name` expects an integer value -- offending value: $value")
        return value as int
    }

    private static boolean toBool(String name, String value) {
        if( value !in ['true', 'false'] )
            throw new AbortOperationException("Option `--$name` expects `true` or `false` -- offending value: $value")
        return value == 'true'
    }
}
