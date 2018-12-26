# scalavista-server

A minimalistic Scala language-server based on [akka-http](https://github.com/akka/akka-http) and 
[Scala](https://github.com/scala/scala)'s presentation compiler. 

Written as a back-end for the [neovim-scalavista](https://github.com/buntec/neovim-scalavista) 
Neovim plugin and the [nscode-scalavista](https://github.com/buntec/vscode-scalavista) 
Visual Studio Code extension.

It exposes a few simple json endpoints and could be used for any other editor with a sufficiently rich api.

## Building

To cross-build the Scala 2.11 and 2.12 executable jars:
```
sbt +assembly 
```

## Disclaimer 

This project is in alpha stage and should be considered unstable. 
