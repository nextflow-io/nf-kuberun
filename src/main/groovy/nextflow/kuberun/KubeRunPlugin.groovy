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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.cli.PluginExecAware
import nextflow.exception.AbortOperationException
import nextflow.plugin.BasePlugin
import org.pf4j.PluginWrapper

/**
 * Plugin that launches a pipeline in a Kubernetes cluster,
 * running the Nextflow driver itself in a pod.
 *
 * Usage: nextflow plugin nf-kuberun:run <pipeline> [--option=value ...]
 */
@Slf4j
@CompileStatic
class KubeRunPlugin extends BasePlugin implements PluginExecAware {

    static final String CMD_RUN = 'run'

    KubeRunPlugin(PluginWrapper wrapper) {
        super(wrapper)
    }

    @Override
    int exec(String pluginId, String cmd, List<String> args) {
        if( cmd != CMD_RUN )
            throw new AbortOperationException("Invalid command '$pluginId:$cmd' -- usage: nextflow plugin nf-kuberun:run <pipeline> [--option=value ...]")
        return run(KubeRunOptions.parse(args ?: Collections.<String>emptyList()))
    }

    protected int run(KubeRunOptions options) {
        if( !options.pipeline )
            throw new AbortOperationException("No project name was specified")
        options.checkRunName()
        final driver = new K8sDriverLauncher(options: options)
        driver.run(options.pipeline, options.args)
        return driver.shutdown()
    }
}
