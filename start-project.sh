#!/bin/bash

########################################
# Script de démarrage des services Docker
########################################

set -e

#############################################
# COULEURS
#############################################
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[0;37m'
NC='\033[0m' # No Color

#############################################
# FONCTION: AFFICHER LE MENU
#############################################
show_menu() {
    echo -e "\n${CYAN}========================================${NC}"
    echo -e "${GREEN}  DEMARRAGE DES SERVICES DOCKER${NC}"
    echo -e "${CYAN}========================================${NC}"
    echo ""
    echo -e "${YELLOW}  1. Demarrage rapide (sans rebuild)${NC}"
    echo -e "${GRAY}     - Lance docker compose up -d${NC}"
    echo -e "${GRAY}     - Utilise les images existantes${NC}"
    echo ""
    echo -e "${YELLOW}  2. Compilation complete + Build${NC}"
    echo -e "${GRAY}     - Compile Maven (backend + kafka)${NC}"
    echo -e "${GRAY}     - Rebuild toutes les images Docker${NC}"
    echo -e "${GRAY}     - Lance docker compose up -d --build${NC}"
    echo ""
    echo -e "${RED}  3. Quitter${NC}"
    echo ""
    echo -e "${CYAN}========================================${NC}"
}

#############################################
# FONCTION: DETECTER JAVA
#############################################
detect_java() {
    echo -e "\n${CYAN}  Detection de Java 17...${NC}"

    JAVA_FOUND=false
    JAVA_PATHS=(
        "/usr/lib/jvm/java-17-openjdk-amd64"
        "/usr/lib/jvm/java-17-openjdk"
        "/usr/lib/jvm/jdk-17"
        "/usr/lib/jvm/java-17"
        "/opt/java/jdk-17"
        "/opt/jdk-17"
    )

    # Essayer de trouver Java dans les chemins communs
    for java_path in "${JAVA_PATHS[@]}"; do
        if [ -d "$java_path" ] && [ -f "$java_path/bin/java" ]; then
            export JAVA_HOME="$java_path"
            JAVA_FOUND=true
            break
        fi
    done

    # Vérifier si JAVA_HOME est déjà défini
    if [ "$JAVA_FOUND" = false ] && [ -n "$JAVA_HOME" ]; then
        if [ -f "$JAVA_HOME/bin/java" ]; then
            JAVA_FOUND=true
        fi
    fi

    # Essayer avec la commande java
    if [ "$JAVA_FOUND" = false ]; then
        if command -v java &> /dev/null; then
            JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
            JAVA_FOUND=true
        fi
    fi

    if [ "$JAVA_FOUND" = false ]; then
        echo -e "${RED}  ERREUR: Java 17 non trouve${NC}"
        echo -e "${YELLOW}  Installez Java 17:${NC}"
        echo -e "${YELLOW}    Ubuntu/Debian: sudo apt install openjdk-17-jdk${NC}"
        echo -e "${YELLOW}    Fedora/RHEL:   sudo dnf install java-17-openjdk${NC}"
        exit 1
    fi

    echo -e "${GREEN}  Java detecte: $JAVA_HOME${NC}"

    # Ajouter Java au PATH
    export PATH="$JAVA_HOME/bin:$PATH"
}

#############################################
# FONCTION: DETECTER MAVEN
#############################################
detect_maven() {
    echo -e "\n${CYAN}  Detection de Maven...${NC}"

    MAVEN_SYSTEM_AVAILABLE=false

    if command -v mvn &> /dev/null; then
        MAVEN_VERSION=$(mvn -version 2>&1 | head -n 1)
        MAVEN_SYSTEM_AVAILABLE=true
        echo -e "${GREEN}  Maven systeme detecte${NC}"
        echo -e "${GRAY}    $MAVEN_VERSION${NC}"
    else
        echo -e "${GRAY}  Maven systeme non detecte (utilisation de Maven Wrapper)${NC}"
    fi
}

#############################################
# FONCTION: INSTALLER MAVEN WRAPPER
#############################################
install_maven_wrapper() {
    local project_path=$1
    echo -e "${YELLOW}  Installation du Maven Wrapper dans $project_path...${NC}"

    local wrapper_installed=false

    # Méthode 1: Télécharger depuis Internet
    echo -e "${GRAY}    Tentative de telechargement depuis Maven Central...${NC}"

    mkdir -p "$project_path/.mvn/wrapper"

    local base_url="https://raw.githubusercontent.com/apache/maven/master/maven-wrapper/src/main/resources"
    local wrapper_version="3.2.0"
    local maven_version="3.9.6"

    if wget -q --timeout=10 "$base_url/mvnw" -O "$project_path/mvnw" && \
       wget -q --timeout=10 "$base_url/mvnw.cmd" -O "$project_path/mvnw.cmd" 2>/dev/null; then

        cat > "$project_path/.mvn/wrapper/maven-wrapper.properties" << EOF
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$maven_version/apache-maven-$maven_version-bin.zip
wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/$wrapper_version/maven-wrapper-$wrapper_version.jar
EOF

        wget -q --timeout=10 \
            "https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/$wrapper_version/maven-wrapper-$wrapper_version.jar" \
            -O "$project_path/.mvn/wrapper/maven-wrapper.jar"

        chmod +x "$project_path/mvnw"
        wrapper_installed=true
        echo -e "${GREEN}    Maven Wrapper telecharge avec succes${NC}"
    else
        echo -e "${YELLOW}    Echec du telechargement${NC}"
    fi

    # Méthode 2: Copier depuis backend (fallback)
    if [ "$wrapper_installed" = false ]; then
        echo -e "${GRAY}    Tentative de copie depuis backend/...${NC}"

        if [ -f "backend/mvnw" ]; then
            cp -r backend/mvnw backend/.mvn "$project_path/" 2>/dev/null || true
            [ -f "backend/mvnw.cmd" ] && cp backend/mvnw.cmd "$project_path/" 2>/dev/null || true

            chmod +x "$project_path/mvnw"
            wrapper_installed=true
            echo -e "${GREEN}    Maven Wrapper copie depuis backend avec succes${NC}"
        else
            echo -e "${RED}    ERREUR: backend/mvnw non trouve${NC}"
        fi
    fi

    [ "$wrapper_installed" = true ]
}

#############################################
# FONCTION: COMPILER AVEC MAVEN
#############################################
compile_maven() {
    local project_path=$1
    local project_name=$2
    local use_system_maven=$3

    echo -e "\n${CYAN}  Compilation du projet $project_name avec Maven...${NC}"

    cd "$project_path"

    if [ ! -f "pom.xml" ]; then
        echo -e "${GRAY}  INFO: Pas de pom.xml dans $project_path, skip compilation${NC}"
        cd - > /dev/null
        return 0
    fi

    local compiled=false
    local maven_command=""

    # Priorité 1: Maven wrapper (mvnw)
    if [ -f "./mvnw" ]; then
        echo -e "${GRAY}  Utilisation de Maven Wrapper (mvnw)${NC}"
        maven_command="./mvnw"
    # Priorité 2: Maven système (si disponible)
    elif [ "$use_system_maven" = true ] && [ "$MAVEN_SYSTEM_AVAILABLE" = true ]; then
        echo -e "${GRAY}  Utilisation de Maven systeme${NC}"
        maven_command="mvn"
    # Sinon: Erreur
    else
        echo -e "${RED}  ERREUR: Aucun Maven disponible pour compiler $project_name${NC}"
        echo -e "${YELLOW}  Solutions:${NC}"
        echo -e "${YELLOW}    1. Installez Maven Wrapper dans le projet${NC}"
        echo -e "${YELLOW}    2. Installez Maven sur votre systeme${NC}"
        cd - > /dev/null
        return 1
    fi

    # Exécuter la compilation
    echo -e "${GRAY}  Commande: $maven_command clean package -DskipTests${NC}"
    $maven_command clean package -DskipTests

    if [ $? -eq 0 ]; then
        # Vérifier que le JAR a été créé
        local jar_files=$(find target -maxdepth 1 -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" 2>/dev/null)

        if [ -n "$jar_files" ]; then
            local jar_name=$(basename $(echo "$jar_files" | head -n 1))
            echo -e "${GREEN}  Compilation Maven $project_name reussie${NC}"
            echo -e "${GRAY}    JAR cree: $jar_name${NC}"
            compiled=true
        else
            echo -e "${YELLOW}  AVERTISSEMENT: Compilation reussie mais aucun JAR trouve${NC}"
            compiled=true
        fi
    else
        echo -e "${RED}  ERREUR: La compilation Maven $project_name a echoue${NC}"
    fi

    cd - > /dev/null
    [ "$compiled" = true ]
}

#############################################
# FONCTION: VERIFIER LES CONTENEURS
#############################################
verify_containers() {
    echo -e "\n${CYAN}  Verification des conteneurs...${NC}"

    local critical_containers=("timescaledb" "postgres" "kafka" "zookeeper" "backend")
    local all_running=true

    for container in "${critical_containers[@]}"; do
        if docker ps --filter "name=$container" --filter "status=running" --format "{{.Names}}" | grep -q "$container"; then
            echo -e "${GREEN}    $container : Running${NC}"
        else
            echo -e "${RED}    $container : NOT RUNNING${NC}"
            all_running=false
        fi
    done

    # Vérifier les Kafka consumers
    local kafka_consumers=("rss-parser" "ozone-avg" "pm10-avg" "pm25-avg" "ozone-max" "pm10-max" "pm25-max")
    local running_consumers=0

    for consumer in "${kafka_consumers[@]}"; do
        if docker ps --filter "name=$consumer" --filter "status=running" --format "{{.Names}}" | grep -q "$consumer"; then
            ((running_consumers++))
        fi
    done

    local total_consumers=${#kafka_consumers[@]}

    if [ $running_consumers -eq $total_consumers ]; then
        echo -e "${GREEN}    Kafka consumers: $running_consumers/$total_consumers running${NC}"
    elif [ $running_consumers -gt 0 ]; then
        echo -e "${YELLOW}    Kafka consumers: $running_consumers/$total_consumers running${NC}"
    else
        echo -e "${RED}    Kafka consumers: $running_consumers/$total_consumers running${NC}"
    fi

    if [ "$all_running" = false ]; then
        echo -e "\n${YELLOW}ATTENTION: Certains conteneurs critiques ne sont pas demarres${NC}"
        echo -e "${YELLOW}Verifiez les logs: docker compose logs <service-name>${NC}"
    fi

    if [ $running_consumers -lt $total_consumers ]; then
        echo -e "\n${YELLOW}⚠️  ATTENTION: Tous les Kafka consumers ne sont pas demarres${NC}"
        echo -e "${YELLOW}   Verifiez: docker compose logs rss-parser${NC}"
    fi
}

#############################################
# FONCTION: AFFICHER LE RESUME
#############################################
show_summary() {
    echo -e "\n${CYAN}========================================${NC}"
    echo -e "${GREEN}  DEMARRAGE TERMINE${NC}"
    echo -e "${CYAN}========================================${NC}"

    echo -e "\n${YELLOW}URLs d'acces:${NC}"
    echo -e "${CYAN}  Frontend:        http://localhost:5173${NC}"
    echo -e "${CYAN}  Backend:         http://localhost:8095${NC}"
    echo -e "${CYAN}  Kafka Connect:   http://localhost:8083${NC}"
    echo -e "${CYAN}  Schema Registry: http://localhost:8081${NC}"
    echo -e "${CYAN}  HDFS NameNode:   http://localhost:9870${NC}"

    echo -e "\n${YELLOW}Bases de donnees:${NC}"
    echo -e "${CYAN}  TimescaleDB:     localhost:5432 (db: db_airquality)${NC}"
    echo -e "${CYAN}  PostgreSQL:      localhost:5433 (db: clients)${NC}"

    echo -e "\n${YELLOW}Commandes utiles:${NC}"
    echo -e "${GRAY}  Voir tous les logs:         docker compose logs -f${NC}"
    echo -e "${GRAY}  Logs Backend:               docker compose logs -f backend${NC}"
    echo -e "${GRAY}  Logs Kafka:                 docker compose logs -f kafka${NC}"
    echo -e "${GRAY}  Logs Consumers:             docker compose logs -f rss-parser${NC}"
    echo -e "${GRAY}  Arreter tous les services:  docker compose down${NC}"
    echo -e "${GRAY}  Restart un service:         docker compose restart <nom>${NC}"

    echo -e "\n${CYAN}========================================${NC}"
}

#############################################
# MENU PRINCIPAL
#############################################
show_menu

echo -ne "\nChoisissez une option (1, 2 ou 3): "
read -r choice

case $choice in
    1)
        echo -e "\n${GREEN}>>> DEMARRAGE RAPIDE SELECTIONNE <<<${NC}"
        BUILD_MODE=false
        ;;
    2)
        echo -e "\n${GREEN}>>> COMPILATION COMPLETE + BUILD SELECTIONNE <<<${NC}"
        BUILD_MODE=true
        ;;
    3)
        echo -e "\n${CYAN}Au revoir!${NC}"
        exit 0
        ;;
    *)
        echo -e "\n${YELLOW}Option invalide. Lancement en mode rapide par defaut...${NC}"
        BUILD_MODE=false
        ;;
esac

#############################################
# MODE COMPILATION COMPLETE
#############################################
if [ "$BUILD_MODE" = true ]; then
    echo -e "\n${YELLOW}Recompilation Maven + Rebuild Docker...${NC}"

    # Détection Java
    detect_java

    # Détection Maven
    detect_maven

    # Installer Maven Wrapper si nécessaire
    echo -e "\n${CYAN}  Verification Maven Wrapper...${NC}"

    if [ -f "kafka-stream-job/project/pom.xml" ] && [ ! -f "kafka-stream-job/project/mvnw" ] && [ "$MAVEN_SYSTEM_AVAILABLE" = false ]; then
        if ! install_maven_wrapper "kafka-stream-job/project"; then
            echo -e "${YELLOW}  AVERTISSEMENT: Impossible d'installer Maven Wrapper${NC}"
            echo -e "${YELLOW}  La compilation utilisera Maven systeme si disponible${NC}"
        fi
    elif [ -f "kafka-stream-job/project/pom.xml" ] && [ ! -f "kafka-stream-job/project/mvnw" ] && [ "$MAVEN_SYSTEM_AVAILABLE" = true ]; then
        echo -e "${GRAY}  Maven systeme disponible, pas besoin de Maven Wrapper${NC}"
    fi

    # Compilation Backend
    if ! compile_maven "backend" "backend" true; then
        echo -e "\n${RED}ERREUR CRITIQUE: La compilation du backend a echoue${NC}"
        exit 1
    fi

    # Vérification supplémentaire pour le backend
    if [ ! -f "backend/target/demo-0.0.1-SNAPSHOT.jar" ]; then
        echo -e "${RED}  ERREUR: Le fichier JAR backend n'a pas ete cree${NC}"
        exit 1
    fi

    # Compilation Kafka Stream Job
    if ! compile_maven "kafka-stream-job/project" "kafka-stream-job" true; then
        echo -e "${YELLOW}  AVERTISSEMENT: Compilation kafka-stream-job echouee, mais on continue...${NC}"
    fi
fi

#############################################
# DOCKER COMPOSE
#############################################
if [ "$BUILD_MODE" = true ]; then
    echo -e "\n${CYAN}  Rebuild + lancement de Docker Compose...${NC}"
    echo -e "\n${GRAY}Commande utilisee :${NC}"
    echo -e "${NC}docker compose up -d --build${NC}"
    docker compose up -d --build
else
    echo -e "\n${CYAN}  Lancement de Docker Compose (mode rapide)...${NC}"
    echo -e "\n${GRAY}Commande utilisee :${NC}"
    echo -e "${NC}docker compose up -d${NC}"
    docker compose up -d
fi

if [ $? -ne 0 ]; then
    echo -e "\n${RED}ERREUR: docker compose up a echoue.${NC}"
    echo -e "${YELLOW}Verifiez la syntaxe de votre fichier docker-compose.yml${NC}"
    exit 1
fi

echo -e "\n${GRAY}  Attente du demarrage des conteneurs (30s)...${NC}"
sleep 30

#############################################
# VERIFICATION ET RESUME
#############################################
verify_containers
show_summary