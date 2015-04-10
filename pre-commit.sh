#!/bin/bash

echo "Generating graph of namespaces (lein hiera)"
lein hiera 
echo "Running code analysis (lein kibit)"
lein kibit
echo "Running linter (lein eastwood)"
# excluding constant test due to not working well with logging
lein eastwood '{:exclude-linters [:constant-test]}'
echo "Running tests"
lein test
echo "Git status"
git status
