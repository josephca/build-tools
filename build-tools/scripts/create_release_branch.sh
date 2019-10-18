#!/usr/bin/env bash

RELEASE=0.5.0

declare -a REPOSITORY_ARRAY=(
			     "codewind-filewatchers" 
                             "codewind-che-plugin" 
                             "codewind" 
                             "codewind-installer" 
                             # n/a "codewind-java-profiler" 
                             # n/a "codewind-node-profiler" 
                             "codewind-eclipse" 
                             "codewind-vscode" 
                             # "codewind-openapi-eclipse" 
                             "codewind-openapi-vscode" 
                             "codewind-docs" 
                             "codewind-appsody-extension" 
                             "codewind-odo-extension" 
                             )
  
  
for i in "${REPOSITORY_ARRAY[@]}"
do
	DIR=`pwd`;
    echo "==========================================="
    echo "Creating $i:$RELEASE"
    echo "==========================================="  
	rm -rf $i
	git clone git@github.com:eclipse/$i.git
	cd $i
	git checkout -b $RELEASE
	git push origin $RELEASE
	cd ..
done
