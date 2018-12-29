#!/usr/bin/env bash

# assemble server jars
sbt clean "+assembly"

# copy jars to launcher folder
rm -rfv ./launcher/jars
mkdir ./launcher/jars
cp -v ./target/**/*.jar ./launcher/jars

# create symlink to launcher script
cur_dir=`pwd`
ln -sfv "$cur_dir/launcher/scalavista" /usr/local/bin/scalavista

# make sure python3 requirements are satisfied
pip3 install colorama --upgrade
pip3 install requests --upgrade
pip3 install crayons --upgrade
