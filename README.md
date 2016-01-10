ixa-pipe-time
=============

NAF wrapper that detecs time expressions based on HeidelTime

1. In the ixa-pipe-time create the lib directory

2.- Download the HeidelTimeStandalone jar file from
https://code.google.com/p/heideltime/

NB: heideltime-1.7 and 1.8 sometimes print out errors to stdout rather than stderr.
This will be corrected in the next release. 
We have an alternative jar file that does not have this error. Please contact us (antske.fokkens@vu.nl) if you need this jar.


If you download the heideltime-standalone-1.7 zip file, you will find two files that you need:
- de.unihd.dbs.heideltime.standalone.jar
- config.props => you will need this file to correctly execute the new time module

move the jar file to the lib directory

3.- Download a copy of JVnTextPro from http://ixa2.si.ehu.es/~jibalari/jvntextpro-2.0.jar

move the jar file to the lib directory

4.- Download the following script https://github.com/carchrae/install-to-project-repo/blob/master/install-to-project-repo.py

5.- Execute the script within the ixa-pipe-time directory

=> It will create the repo directory and two dependencies that you don't need to copy in the pom.xml file. It is necessary to run the scrip to correctly create the repo directory. Don't copy the anything in the pom file. 

8.- Download the mappings file: http://ixa2.si.ehu.es/~jibalari/eagles-to-treetager.csv

9.- Create the jar file for the time module
    mvn clean install

10.- Test the module

cat pos.naf | java -jar ${dirToJAR}/ixa.pipe.time.jar -m ${dirToFile}/eagles-to-treetager.csv -c ${dirToFile}/config.props

License
=======

Apache v2. See LICENSE file for details.
