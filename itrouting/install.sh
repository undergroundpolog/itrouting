#install java
apt-get install openjdk-7-jdk openjdk-7-jre openjdk-7-doc

#install tomcat7
apt-get install tomcat7 tomcat7-admin tomcat7-common tomcat7-docs tomcat7-examples tomcat7-user

sed -i 's/Xmx[0-9]*[mMgG]/Xmx10g/' /etc/default/tomcat7
service tomcat7 restart

cd config/makewar/
CATALINA_HOME=/var/lib/tomcat7 ./deploy.sh
