---
title: Format
sidebar_position: 15
---

Scala CLI supports formatting your code using [Scalafmt](https://scalameta.org/scalafmt/):

```bash
scala-cli fmt
```

Under the hood, `scala-cli` downloads and runs Scalafmt on your code.

If you’re setting up a continuous integration (CI) server, `scala-cli` also has you covered.
You can check formatting correctness using a `--check` flag:

```bash
scala-cli fmt --check
```

### Dialects

Scala CLI also supports dialects that are passed to the formatter.
This value is only used if there is no `.scalafmt.conf` file.
However, if it exists, then all configuration should be placed there.
For a list of all possible values, consult the [official Scala Dialects documentation](https://scalameta.org/scalafmt/docs/configuration.html#scala-dialects):

```bash
scala-cli fmt --dialect scala212
```

### Scalafmt version

At this time, Scala CLI reads a `scalafmt` version from `.scalafmt.conf` files. If the version is missing, Scala CLI throws an error, stating that users should declare an explicit Scalafmt version. Since Scalafmt `3.5.0`, this parameter is mandatory.

To configure the Scalafmt version, add the following to `.scalafmt.conf`. For example, to set the version to `3.5.0`, add the following line:

```
version = "3.5.0"
```