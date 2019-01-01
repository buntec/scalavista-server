# scalavista-server

A minimalistic Scala language-server based on [akka-http](https://github.com/akka/akka-http) and 
[Scala](https://github.com/scala/scala)'s presentation compiler. 

Written as a back-end for the [neovim-scalavista](https://github.com/buntec/neovim-scalavista) 
Neovim plugin and the [vscode-scalavista](https://github.com/buntec/vscode-scalavista) 
Visual Studio Code extension.

If anyone wants to contribute similar plugins for Atom or Sublime Text, that would be great!

## Install

Download and extract the zip archive from the latest release and add the `bin` subfolder to your `PATH`.

## Usage
#
Start a scalavista-server instance by executing `scalavista-launcher`
from the root of your Scala project, ideally with
a `scalavista.json` present.

If you want to work on multiple separate projects at the same time, 
you can use the `--port` option to run one server per project. 

For an optimal experience use the
[sbt-scalavista](https://github.com/buntec/sbt-scalavista) plugin
to generate a `scalavista.json` file for your project.
This is a simple json file with the following fields
(you can write it by hand in simple cases):

1. `classpath` - a list of dependencies
1. `scalaBinaryVersion` - 2.11 or 2.12
1. `sources` - a list of Scala source files
1. `scalacOptions` - a list of scalac compiler options

You can use scalavista-server without a `scalavista.json` with the
effect that external dependencies are not recognized and marked
as errors in your code. The exception are manually managed
dependencies in `./lib` which are automatically appended to the
classpath. All Scala source files in the root of the project or
any of its subdirectories are picked up.

Use `--help` to see a list of options.

## Disclaimer 

This project is in alpha stage and should be considered unstable. 
