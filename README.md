# nf-kuberun

Nextflow plugin that launches a pipeline in a Kubernetes cluster, with the Nextflow driver itself running in a pod. It replaces the `nextflow kuberun` command, which was removed from Nextflow in 27.04.

> [!WARNING]
> This plugin is provided for backwards compatibility and is no longer actively developed. Consider [launching with Fusion](https://docs.seqera.io/nextflow/reference/executor/kubernetes#launch-with-fusion) instead.

## Usage

```bash
nextflow plugin nf-kuberun:run <pipeline> [script args] [--option=value ...]
```

For example:

```bash
nextflow plugin nf-kuberun:run nextflow-io/hello --namespace=my-ns --volume-mount=my-pvc:/mnt/data
```

The plugin depends on the `nf-k8s` plugin, which is installed automatically. Configure the cluster connection and storage with the [`k8s`](https://docs.seqera.io/nextflow/reference/config/k8s) config scope.

The pipeline must be a remote Git project or the absolute path of a project already deployed in the cluster. Local scripts are not supported. Use `login` as the pipeline name to launch an interactive driver pod and attach to it with `kubectl`.

## Options

Options must be written as `--name=value`, after the pipeline name and any script arguments. Options that take multiple values accept a comma-separated list, since repeating an option keeps only the last value. Boolean options require an explicit `true` or `false` value.

Any option not listed below is passed to the pipeline as a parameter. Use the `--params.<name>=<value>` form to pass a pipeline parameter whose name matches one of the options below.

### Kubernetes options

| Option | Description |
|---|---|
| `--namespace` | Kubernetes namespace to use |
| `--volume-mount` | Comma-separated volume claim mounts, e.g. `my-pvc:/mnt/path`. The first claim is used as the shared storage. |
| `--head-image` | Container image for the driver pod |
| `--head-cpus` | Number of CPUs requested for the driver pod |
| `--head-memory` | Amount of memory requested for the driver pod |
| `--head-prescript` | Script to run in the driver pod before `nextflow run` |
| `--remote-config` | Comma-separated config files, located in the cluster, to use for the run |
| `--remote-profile` | Config profile to use from the remote config |
| `--background` | Exit after launching the driver pod instead of printing its output (`true` or `false`) |

### Launcher options

| Option | Description |
|---|---|
| `--config` | Comma-separated local config files to add to the run configuration |
| `--trace` | Comma-separated packages to trace in the driver |
| `--debug` | Comma-separated packages to debug in the driver |
| `--jvm.<name>` | Java system property to set in the driver |

### Run options

These options have the same meaning as the corresponding options of `nextflow run`:

`--name`, `--cache`, `--resume`, `--pool-size`, `--poll-interval`, `--queue-size`, `--work-dir`, `--params-file`, `--process.<name>`, `--env.<name>`, `--revision`, `--latest`, `--main-script`, `--hub`, `--user`, `--profile`, `--entry`, `--plugins`, `--with-trace`, `--with-timeline`, `--with-dag`, `--dump-hashes`, `--dump-channels`

Use `true` as the value of `--resume`, `--with-trace`, `--with-timeline`, `--with-dag`, and `--dump-hashes` to enable them with their default value.

The `--profile` option selects a profile from the local config. Use `--remote-profile` to select a profile in the driver pod.

## Migrating from `kuberun`

Replace `nextflow kuberun` with `nextflow plugin nf-kuberun:run`, and change each option to the `--name=value` form above. For example:

```bash
# before
nextflow -c my.config kuberun nextflow-io/hello -v my-pvc:/mnt -r master --foo bar
# after
nextflow plugin nf-kuberun:run nextflow-io/hello --config=my.config --volume-mount=my-pvc:/mnt --revision=master --foo=bar
```

Short option names, such as `-v`, `-n`, `-r` and `-w`, are not supported. Write `--namespace`, `--volume-mount` and so on instead.

## Development

Build and run the unit tests:

```bash
make test
```

Install the plugin into your local Nextflow plugins directory:

```bash
make install
```
