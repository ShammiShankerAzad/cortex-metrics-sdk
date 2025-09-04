#!/bin/bash

# Universal Cortex Metrics SDK - Project Generator
# This script creates all the Java files and project structure

PROJECT_NAME="universal-cortex-metrics-sdk"
MAIN_PACKAGE="src/main/java/com/example/cortex"
RESOURCES="src/main/resources"

echo "Creating Universal Cortex Metrics SDK project..."

# Create project directory
mkdir -p $PROJECT_NAME
cd $PROJECT_NAME

# Create directory structure
echo "Creating directory structure..."
mkdir -p $MAIN_PACKAGE/{config,format/prometheus,registry,publisher,client,buffer,service}
mkdir -p $RESOURCES
mkdir -p src/test/java

echo "Project structure created at: $(pwd)"
echo ""
echo "Directory structure:"
echo "├── $MAIN_PACKAGE/"
echo "│   ├── config/"
echo "│   ├── format/prometheus/"
echo "│   ├── registry/"
echo "│   ├── publisher/"
echo "│   ├── client/"
echo "│   ├── buffer/"
echo "│   └── service/"
echo "└── $RESOURCES/"
echo ""

# Instructions for next steps
echo "NEXT STEPS:"
echo "==========="
echo "1. Copy the individual .java files I'm creating into the appropriate directories"
echo "2. Copy the configuration files (pom.xml, application.yml, etc.)"
echo "3. Run: mvn clean compile"
echo "4. Run: mvn package"
echo ""
echo "The Java files will be created as separate files that you can download."

echo ""
echo "Project setup completed! Check for the individual .java files I'm generating."