#!/bin/bash
git pull ; mvn package
java -jar target/BuildCleaner-0.0.1-SNAPSHOT.jar jgw pulse-tools.canlab.ibm.com /home/jgw/Node
