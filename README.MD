# scalavista-server

A minimalistic Scala language server based on [akka-http](https://github.com/akka/akka-http) and
Scala's presentation compiler.

Written as a back-end for the [neovim-scalavista](https://github.com/buntec/neovim-scalavista)
Neovim plugin and the [vscode-scalavista](https://github.com/buntec/vscode-scalavista)
Visual Studio Code extension.

(If anyone wants to contribute similar plugins for e.g. Atom or Sublime Text, that would be great!)

See `src/main/scala/org/scalavista/Server.scala` for all API endpoints.

## Prerequisites

- Java (version 8 or higher).

## Install

End users can rely on the Visual Studio Code and Neovim plugins to download
the latest version of the server jars for them.

To download them manually go to the [release page](https://github.com/buntec/scalavista-server/releases).


## Usage

The Neovim and Visual Studio Code plugins will launch
a server as a subprocess upon activation.

To start the server manually, do this from the root of your project:
```
java -jar /path/to/scalavista-server-*.jar [options]
```

Use `--help` to see a list of options.

For an optimal experience create a file called `scalavista.json` at the root of your project.
This is a plain json file with the following fields (all optional):

1. `dependencies` - a list of dependencies (Scala or Java), e.g., `["org.scalatest:scalatest_2.13:3.0.8", "junit:junit:4.10"]`; if this field is missing
then any jar in a `lib` subfolder of where the server was launched (your project root) will be added to the classpath (i.e., manual dependency management).
1. `scalaBinaryVersion` - one of `"2.11"`, `"2.12"` or `"2.13"`; defaults to `"2.13"` if missing.
1. `sources` - a list of Scala or Java source files; if this field is missing then all `*.scala` and `*.java` files
 in the directory where the server was launched (the root of your project) and any of its subdirectories (recursively) will be loaded.
1. `scalacOptions` - a list of scalac compiler options, e.g., `["-deprecation", "-feature"]`; uses sensible defaults if missing.
1. `classpath` - a list of items (e.g., jars) to be added to the classpath.

You can use the [sbt-scalavista](https://github.com/buntec/sbt-scalavista)
plugin to generate a `scalavista.json` file for your project.

## Disclaimer

This is work in progress. Contributions and bug reports are most welcome!
