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
@CompileStatic
class KubeRunOptions {

    static final private Pattern POD_NAME = ~/[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*/

    static final private Pattern RUN_NAME = Pattern.compile(/^[a-z](?:[a-z\d]|[-_](?=[a-z\d])){0,79}$/, Pattern.CASE_INSENSITIVE)

    /**
     * Options of the `run` command that cannot be used with kuberun
     */
    static final private Set<String> UNSUPPORTED = [
        'ansi', 'ansi-log', 'bucket-dir', 'deep', 'disable-jobs-cancellation', 'E', 'export-sys-env',
        'lib', 'offline', 'output-dir', 'output-format', 'preview', 'stdin', 'stub', 'stub-run', 'test',
        'with-agent-trace', 'with-apptainer', 'with-charliecloud', 'with-cloudcache', 'with-conda',
        'with-docker', 'with-fusion', 'with-mpi', 'with-notification', 'with-podman', 'with-report',
        'with-singularity', 'with-spack', 'with-tower', 'with-wave', 'with-weblog', 'without-conda',
        'without-docker', 'without-podman', 'without-spack'
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
                throw new AbortOperationException("Unexpected argument: $key -- options must be specified as --name=value after the pipeline name")
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
