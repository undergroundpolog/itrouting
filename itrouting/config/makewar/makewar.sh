echo "Configurando variables"
echo "----------------------"
echo ""

cd ../../itrouting2/src/java/
LIBS=../../web/WEB-INF/lib/
#LIBS=../../../config/makewar/lib/
CLASSES=../../web/WEB-INF/classes

echo "Creando árbol de carpetas"
echo "-------------------------"
echo ""

rm -rf ../../dist/
rm -rf ../../web/WEB-INF/classes

mkdir ../../web/WEB-INF/classes
mkdir ../../dist/

echo "Estableciendo archivos de configuración"
echo "---------------------------------------"
echo ""

cp *.xml $CLASSES
cp *.properties $CLASSES

echo "Compilando clases"
echo "-----------------"
echo ""

javac -proc:none -cp "$CLASSPATH:$LIBS/*" com/intraffic/itrouting/core/impl/*.java -d $CLASSES
javac -proc:none -cp "$CLASSPATH:$LIBS/*" com/intraffic/itrouting/core/*.java -d $CLASSES
javac -proc:none -cp "$CLASSPATH:$LIBS/*" com/intraffic/org/neo4j/graphalgo/impl/path/*.java -d $CLASSES
javac -proc:none -cp "$CLASSPATH:$LIBS/*" com/intraffic/org/neo4j/graphalgo/*.java -d $CLASSES
javac -proc:none -cp "$CLASSPATH:$LIBS/*" com/intraffic/utils/hibernateUtils/*.java -d $CLASSES
javac -proc:none -cp "$CLASSPATH:$LIBS/*" com/intraffic/utils/propManager/*.java -d $CLASSES
javac -proc:none -cp "$CLASSPATH:$LIBS/*" com/intraffic/utils/Verification/*.java -d $CLASSES

echo "Creando .war"
echo "------------"
echo ""

cd ../../web/

jar -cf itrouting2.war *

mv itrouting2.war ../dist/

echo "Eliminando archivos temporales"
echo "------------------------------"
echo ""

rm -rf WEB-INF/classes

