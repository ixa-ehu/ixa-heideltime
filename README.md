ixa-heideltime
==============

NAF wrapper that detecs time expressions based on HeidelTime

Prerequisits: Maven


1.- Download the HeidelTimeStandalone jar file from
https://code.google.com/p/heideltime/

An older version for which an old bug is repaired can be found in the lib/ already.

If you download the latest heideltime-standalone-x.x zip file, you will find two files that you need:
- de.unihd.dbs.heideltime.standalone.jar
- config.props => you will need this file to correctly execute the new time module

move the jar file to the lib directory

2.- Download a copy of JVnTextPro from http://ixa2.si.ehu.es/~jibalari/jvntextpro-2.0.jar

move the jar file to the lib directory

3.- Download the following script https://github.com/carchrae/install-to-project-repo/blob/master/install-to-project-repo.py

4.- Execute the script within the ixa-heideltime directory

=> It will create the repo directory and two dependencies that you don't need to copy in the pom.xml file. It is necessary to run the scrip to correctly create the repo directory. Don't copy the anything in the pom file. 

5.- If you want to work with Spanish or English, download the mappings file: http://ixa2.si.ehu.es/~jibalari/eagles-to-treetager.csv for processing Spanish or English. You will find the mappings file for Dutch (alpino-to-treetagger.csv) under lib/

6.- Create the jar file for the time module
    mvn clean install

10.- Test the module

cat pos.naf | java -jar ${dirToJAR}/ixa.pipe.time.jar -m ${dirToFile}/eagles-to-treetager.csv -c ${dirWithFile}/config.props

for Dutch:

cat pos.naf | java -jar ${dirToJAR}/ixa.pipe.time.jar -m ${dirToFile}/alpino-to-treetagger.csv -c ${dirWithFile}/config.props

License
=======

GPLv3. See LICENSE.txt for details.


Troubleshooting
===============

Heideltime versions 1.7 and 1.8 occasionally print out errors to std.out rather than std.error when applied to Dutch. 
The latest version of Heideltime (2.2 or later) should work without problems. A version of Heideltime 1.8 with the bug fixed is included under lib/ 
