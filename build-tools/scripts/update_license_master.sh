#!/usr/bin/env bash

RELEASE=master
BRANCH=updateLicenseNoticeMaster

declare -a REPOSITORY_ARRAY=(
		 # done "codewind" 
		 # done "codewind-appsody-extension" 
		 # done "codewind-che-plugin" 
		 # done "codewind-eclipse" 
		 # done "codewind-filewatchers" 
		 # done "codewind-installer"  # LICENSE.md
		 # done "codewind-java-profiler" 
		 # done "codewind-node-profiler"  #LICENSE only
		 # done "codewind-openapi-eclipse" 
		 # done "codewind-openapi-vscode" 
		 # done "codewind-vscode" 
		 # done "codewind-odo-extension" #LICENSE only
		 
		 # ---------- Not applicable ----------
		 # "eclipse/codewind-docs
		 # "eclipse/codewind-chai-openapi-response-validator"
		 # "eclipse/codewind-wiki"
		 )
  
  
for i in "${REPOSITORY_ARRAY[@]}"
do
	DIR=`pwd`;
    
	echo "==========================================="
    echo "Copying new LICENSE and NOTICE for $i:$RELEASE"
    echo "==========================================="  
	rm -rf $i
	git clone git@github.com:eclipse/$i.git
	cd $i
	git remote add my_fork git@github.com:josephca/$i.git 
	git config remote.pushdefault my_fork
	#git checkout $RELEASE
	#git pull 
	
	git checkout -b $BRANCH
	rm -rf LICENSE.md LICENSE NOTICE.md
	cp ../legal/LICENSE .
	cp ../legal/NOTICE.md .
	git add LICENSE
	git commit -a -m "Update EPL-2.0 license with correct format and update NOTICE.md"
	git push my_fork $BRANCH
	cd ..
done
