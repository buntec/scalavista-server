#!/usr/bin/env bash

sbt "+assembly"

cp -v ./target/**/*.jar ./launcher/jars

cur_dir=`pwd`

# create symlink to launcher script
ln -sfv "$cur_dir/launcher/scalavista" /usr/local/bin/scalavista

pip3 install requests --upgrade
pip3 install crayons --upgrade
