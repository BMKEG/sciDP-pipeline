#!/bin/bash

DIR=$1
NTHREADS=$2 
NXML2TXT_PATH=$3

if [ -z $1 ] ; 
then
  	echo "USAGE: DIR_PATH N-THREADS"
	
fi

PWD=`pwd`
echo $PWD
echo
LIB_DIR=$PWD/target

java -cp $LIB_DIR/sciDT-pipeline-0.1.1-SNAPSHOT-jar-with-dependencies.jar \
				edu.isi.bmkeg.sciDT.bin.SciDT_0_Nxml2SciDT \
                -inDir $DIR \
                -nThreads $NTHREADS \
                -nxml2textPath NXML2TXT_PATH
