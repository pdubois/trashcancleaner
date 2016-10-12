#!/bin/bash
# Downloads the spring-loaded lib if not existing and runs repository AMP
springloadedfile=~/.m2/repository/org/springframework/springloaded/1.2.0.RELEASE/springloaded-1.2.0.RELEASE.jar

if [ ! -f $springloadedfile ]; then
mvn validate -Psetup
fi

MAVEN_OPTS="-javaagent:$springloadedfile -noverify -Xms256m -Xmx2G -XX:PermSize=300m -Djava.net.preferIPv4Stack=true -Xdebug -Xrunjdwp:transport=dt_socket,address=5000,server=y,suspend=n" 
mvn integration-test -Dskiptest=true -Pamp-to-war
