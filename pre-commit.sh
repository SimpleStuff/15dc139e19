#!/bin/bash

#echo "Generating graph of namespaces (lein hiera)"
#lein hiera 
#echo "Checking dependencies versions"
#lein ancient
#echo "Running code analysis (lein kibit)"
#lein kibit
#echo "Running linter (lein eastwood)"
# excluding constant test due to not working well with logging
#lein eastwood '{:exclude-linters [:constant-test]}'
#echo "Running tests"
#lein test
#echo "Generating documentation (lein marg)"
#lein marg src test specs --dir "./doc"


# TODO - add arg to decide if docs should be regen or not
echo "Pre-commit quality check"
lein pre-commit
echo "Git status"
git status
