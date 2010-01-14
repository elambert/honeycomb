#!/bin/bash

# HACK: hate this but it's the only way to get the classpath correctly 
#       setup for the javadoc task :(
export CLASSPATH=$CLASSPATH:external/lib/dtf.jar:external/lib/dtdparser121.jar
export PATH=$PATH:../../build/dtf/dist/apache-ant-1.6.0/bin

ant $@ 
