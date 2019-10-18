#!/usr/bin/env bash

RELEASE=0.5.0

declare -a REPOSITORY_ARRAY=(
		 "codewind" 
		 "codewind-appsody-extension" 
		 "codewind-che-plugin" 
		 "codewind-eclipse" 
		 "codewind-filewatchers" 
		 "codewind-installer"  # LICENSE.md
		 "codewind-java-profiler" 
		 "codewind-node-profiler" 
		 "codewind-openapi-eclipse" 
		 "codewind-openapi-vscode" 
		 "codewind-vscode" 
		 "codewind-odo-extension"
		 
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
	git checkout $RELEASE
	git checkout -b updateLicenseNotice_$RELEASE
	rm LICENSE.md LICENSE NOTICE.md
	cp ../legal/LICENSE .
	cp ../legal/NOTICE.md .
	git add LICENSE
	git commit -a -m "Update EPL-2.0 license with correct format"
	git push my_fork
	#git push origin $RELEASE
	cd ..
done
