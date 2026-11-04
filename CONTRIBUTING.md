# nf-kuberun

Contributions are welcome. Fork [this repository](https://github.com/nextflow-io/nf-kuberun) and open a pull request to propose changes. Consider submitting an [issue](https://github.com/nextflow-io/nf-kuberun/issues/new) to discuss any proposed changes with the maintainers before submitting a pull request.

## Development

Run the unit tests:

```bash
make test
```

Build and install the plugin to your local Nextflow installation:

```bash
make install
```

Install the `nf-k8s` plugin. Nextflow 26.10 does not download the dependencies of a locally installed plugin:

```bash
nextflow plugin install nf-k8s
```

Launch a pipeline on the Kubernetes cluster of your current `kubectl` context:

```bash
nextflow plugin nf-kuberun@<version>:run nextflow-io/hello --volume-mount=<pvc>:/mnt
```

Without a cluster, the command fails with `Unable to lookup Kubernetes cluster configuration`, which confirms that the plugin loads.

## Publishing

Follow these steps to package, upload, and publish the plugin:

1. Update the [version file](./VERSION).

2. Update the [changelog](./CHANGELOG.md) with the release version and date.

3. Set the `NPR_API_KEY` environment variable to your [Nextflow registry](https://registry.nextflow.io) access token.

4. Run `make release` to build and publish the plugin.

5. Make a [GitHub release](https://github.com/nextflow-io/nf-kuberun/releases).
