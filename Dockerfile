FROM cloudbees/cloudbees-core-mm:2.222.1.1-alpine

LABEL maintainer "kmadel@cloudbees.com"

#skip setup wizard and disable CLI
ENV JVM_OPTS -Djenkins.CLI.disabled=true -server
ENV TZ="/usr/share/zoneinfo/America/New_York"

RUN mkdir -p /usr/share/jenkins/ref/init.groovy.d

#Jenkins system configuration via init groovy scripts - see https://wiki.jenkins-ci.org/display/JENKINS/Configuring+Jenkins+upon+start+up 
COPY ./init.groovy.d/* /usr/share/jenkins/ref/init.groovy.d/

#install suggested and additional plugins
ENV JENKINS_UC http://jenkins-updates.cloudbees.com

COPY ./jenkins_ref /usr/share/jenkins/ref
COPY jenkins-support /usr/local/bin/jenkins-support

COPY jenkins.sh /usr/share/jenkins/ref
COPY launch.sh /usr/share/jenkins/ref

USER root
RUN chmod +x /usr/share/jenkins/ref/launch.sh /usr/share/jenkins/ref/jenkins.sh
USER 1000

ENTRYPOINT ["tini", "--", "/usr/share/jenkins/ref/launch.sh"]
