# scalavista-server

A minimalistic Scala language-server based on [akka-http](https://github.com/akka/akka-http) and 
[Scala](https://github.com/scala/scala)'s presentation compiler. 

Written as a back-end for the [neovim-scalavista](https://github.com/buntec/neovim-scalavista) 
Neovim plugin and the [vscode-scalavista](https://github.com/buntec/vscode-scalavista) 
Visual Studio Code extension.

(If anyone wants to contribute similar plugins for Atom or Sublime Text, that would be great!)

## Install

Download the fat jar for your desired Scala version (only 2.11 and 2.12 are currently supported)
from the latest [release](https://github.com/buntec/scalavista-server/releases).

## Usage

Start a scalavista-server instance from the root of your project by executing 

```
java -jar /path/to/scalavista-server-*.jar [options]
```

(It may be more convenient to put this into a simple launcher script.)

Use `--help` to see a list of options.

If you want to work on multiple separate projects at the same time, 
you can use the `--port` option to run one server instance per project. 

For an optimal experience use the
[sbt-scalavista](https://github.com/buntec/sbt-scalavista) 
plugin to generate a `scalavista.json` file for your project.
This is a plain json file with the following fields:

1. `classpath` - a list of dependencies;
1. `scalaBinaryVersion` - "2.11" or "2.12";
1. `sources` - a list of Scala or Java source files;
1. `scalacOptions` - a list of scalac compiler options.

In the absence of a `scalavista.json` file, scalavista-server will pick up any
*.scala or *.java files in the directory where the server was launched or
any of its subdirectories. External dependencies are only picked up if they
reside in a `lib` subfolder of where the server was launched.


## Disclaimer 

This project is in alpha stage and should be considered unstable. 
