# tango

## Usage

1. Build clojurescript src
   
   ```
   $ lein cljsbuild once
   ```

2. Start a REPL with lein (or with your development environment)
   
   ```
   $ lein repl
   ```	

3. Start the server in the REPL with (go)

4. Browse to "localhost:1337" to load the client script

5. Import file /tango/test/small-example.xml

## Options

Running main give the option of specifing port number.
   
   ```
   $ lein run 80
   ```

## Developer info

A shell-script called "pre-commit" is included that will run tests and code analysis. The script will also generate the following :
 - "doc/uberdoc.html" that contains src with comments.
 - "specs/tango-hierarchy.png" that show a namespace graph.

To be able to run the script "graphviz" is needed (lein hiera).

## License

Copyright © 2014 Mårten Larsson, Stefan Karlsson, Thomas Didriksson

