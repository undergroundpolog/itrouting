INSTALACION Y CONFIGURACION DE ITROUTING2
-----------------------------------------

Instalación de herramientas
---------------------------

Ejecutar el archivo config.sh. Este archivo instalará tomcat7 y otras herramientas necesarias para la adecuada ejecución de itrouting2

Configuración de tomcat7
------------------------

Editar el archivo /etc/default/tomcat7, en la variable JAVA_OPTS aumentar el tamaño del heap a 10GB:
JAVA_OPTS="-Djava.awt.headless=true -Xmx10g -XX:+UseConcMarkSweepGC"

"build" de itrouting2 con makewar (Recomendado)
---------------------------------

La creación del archivo WAR con este script no requiere de los archivos XML creados por netbeans. Ejecutar el archivo makewar.sh ubicado en el directorio makewar:
cd makewar/
chmod 777 makewar.sh
./makewar.sh

"build" de itrouting2 con ant
-----------------------------

La creación del archivo WAR con ant requiere de los archivos XML que son creados por netbeans, estos archivos ya estan contenidos en la carpeta itrouting2/nbproject. Ejecutar el archivo build.sh ubicado en el directorio conf_nb:
cd conf_nb
chmod 777 *.sh
./build.sh

"deploy" de itrouting2
----------------------

Luego de ejecutar makewar.sh se creara un .war en la carpeta itrouting2/dist, copiar este archivo en /var/lib/tomcat7/webapps






