FROM eclipse-temurin:17-jre
# Créer le répertoire de travail
WORKDIR /app
# Copier le fat JAR généré par maven-assembly-plugin
COPY target/*-jar-with-dependencies.jar /app/app.jar
ENTRYPOINT ["java", "-cp", "/app/app.jar"]

