# Changelog

All notable changes to the nf-kuberun plugin will be documented here.

See [Keep a Changelog](http://keepachangelog.com/) for recommendations on how to structure this file.

## [0.1.0] - unreleased

- Initial release, migrated from the `kuberun` command of Nextflow
- Options are specified as `--name=value`
- The `--namespace` option now sets the Kubernetes namespace (it had no effect in the `kuberun` command)
- The `--main-script` and `--hub` options are forwarded to the driver pod
