#!/bin/bash

echo "Running code analysis (lein kibit)"
lein kibit
echo "Running tests"
lein test
