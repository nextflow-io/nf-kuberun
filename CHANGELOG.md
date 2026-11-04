# Changelog

All notable changes to the nf-kuberun plugin will be documented here.

See [Keep a Changelog](http://keepachangelog.com/) for recommendations on how to structure this file.

## [0.1.0] - 2026-11-03

Initial release, migrated from the `kuberun` command of Nextflow. Differences from the `kuberun` command:

- Options are specified with a double dash (`--name=value`), and short option names (`-v`, `-n`, `-r`, ...) are no longer supported
- Launcher options before the `plugin` command (`-c`, `-C`, `-bg`) are ignored, use `--config` and `--background` instead
- `--namespace` now sets the Kubernetes namespace (`-n` had no effect)
- `--main-script` and `--hub` are forwarded to the driver pod
- `run` options are passed to the driver on its command line instead of being merged into the shipped config, so they are no longer resolved locally (e.g. `--resume` is not resolved against the local run history)
- `--resume=true` resumes the last run in the driver (`-resume last`) instead of passing `true` as a session id
- `--latest` is passed as a flag instead of leaving a stray `true` script argument
- Script arguments are passed as separate arguments instead of a single `[a, b]` argument
- Plugins given with `--plugins` are no longer duplicated in the config
- `--work-dir` overrides the `workDir` config setting in the driver, and `NXF_WORK` is used as a fallback
- `false` disables options that take an optional value (`--with-trace=false` no longer writes a file named `false`), and `--dump-channels=true` dumps all channels
- `--env.<name>` and `--process.<name>` are passed as `-e.<name>=<value>` and `-process.<name>=<value>`, which fixes `--env.<name>` breaking the driver command line
- The run name is not checked against the local run history
