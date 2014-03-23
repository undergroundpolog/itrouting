if [ -z "${CATALINA_HOME+X}" ]
then
    echo var CATALINA_HOME is unset
elif [ -z "$CATALINA_HOME" ]
then
    echo var CATALINA_HOME set but empty
else
    echo "The value of \$CATALINA_HOME is $CATALINA_HOME"
    rm -f ../../itrouting2/dist/itrouting2.war
    ./makewar.sh
    echo "Moviendo war"
    echo "------------"
    echo ""
    mv $CATALINA_HOME/webapps/itrouting2/neo4j_target /var/tmp
    rm -f $CATALINA_HOME/webapps/itrouting2.war
    cp -f ../../itrouting2/dist/itrouting2.war $CATALINA_HOME/webapps/
    sleep 5
    mv /var/tmp/neo4j_target $CATALINA_HOME/webapps/itrouting2/
    chmod -R 777 $CATALINA_HOME/webapps/itrouting2/neo4j_target
    service tomcat7 restart
fi
