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

Options are given with a double dash. `--name=value` is recommended. `--name value` and a bare `--name`, which means `--name=true`, also work. Be careful with a bare boolean option placed just before a script argument: `--latest foo` is read as `--latest=foo`, so write `--latest=true foo`.

Options that take multiple values accept a comma-separated list, since repeating an option keeps only the last value.

Launcher options given before the `plugin` command, such as `-c`, `-C` and `-bg`, are ignored. Use `--config` and `--background` instead.

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

`--name`, `--cache`, `--resume`, `--pool-size`, `--poll-interval`, `--queue-size`, `--work-dir`, `--params-file`, `--process.<name>`, `--env.<name>`, `--revision`, `--latest`, `--offline`, `--deep`, `--main-script`, `--hub`, `--user`, `--profile`, `--entry`, `--plugins`, `--output-dir`, `--output-format`, `--bucket-dir`, `--with-cloudcache`, `--with-tower`, `--with-wave`, `--with-fusion`, `--with-trace`, `--with-report`, `--with-timeline`, `--with-dag`, `--with-agent-trace`, `--with-notification`, `--with-conda`, `--without-conda`, `--with-spack`, `--without-spack`, `--with-podman`, `--with-charliecloud`, `--stub-run`, `--preview`, `--disable-jobs-cancellation`, `--dump-hashes`, `--dump-channels`

For options that take an optional value, such as `--resume`, `--with-trace`, `--with-tower`, `--with-conda` and `--dump-channels`, use `true` (or the bare option) to enable them with their default value, and `false` to leave them disabled.

The `--params-file` option defaults to `NXF_PARAMS_FILE`, and `--work-dir` defaults to the `workDir` config setting, then `NXF_WORK`.

The following `run` options are not supported, because the driver runs in a pod: `--lib`, `--test`, `--stdin`, `--with-docker`, `--without-docker`, `--with-singularity`, `--with-apptainer`, `--with-mpi`, `--export-sys-env`, `--executor.<name>`, `--cluster.<name>` and `--process.executor`. The `--ansi-log` and `--without-podman` options have no effect.

Unlike `nextflow run`, the run name is not checked against the local run history, since the run is recorded in the history of the driver pod.

The `--profile` option selects a profile from the local config. Use `--remote-profile` to select a profile in the driver pod.

## Migrating from `kuberun`

Replace `nextflow kuberun` with `nextflow plugin nf-kuberun:run`, change each option to the `--name=value` form above, and move launcher options such as `-c` after the command. For example:

```bash
# before
nextflow -c my.config kuberun nextflow-io/hello -v my-pvc:/mnt -r master --foo bar
# after
nextflow plugin nf-kuberun:run nextflow-io/hello --config=my.config --volume-mount=my-pvc:/mnt --revision=master --foo=bar
```

Short option names, such as `-v`, `-n`, `-r` and `-w`, are not supported, since they would hide pipeline parameters with the same name. Write `--namespace`, `--volume-mount` and so on instead. See the [changelog](CHANGELOG.md) for other differences from the `kuberun` command.

## Development

See [CONTRIBUTING.md](CONTRIBUTING.md) for the development and release process.

## License

This project is licensed under the [Apache License 2.0](./COPYING).
