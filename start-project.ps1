########################################
# Script de démarrage des services Docker
########################################

$ErrorActionPreference = "Continue"

#############################################
# MENU PRINCIPAL
#############################################
function Show-Menu {
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "  DEMARRAGE DES SERVICES DOCKER" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  1. Demarrage rapide (sans rebuild)" -ForegroundColor Yellow
    Write-Host "     - Lance docker compose up -d" -ForegroundColor Gray
    Write-Host "     - Utilise les images existantes" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  2. Compilation complete + Build" -ForegroundColor Yellow
    Write-Host "     - Compile Maven (backend + kafka)" -ForegroundColor Gray
    Write-Host "     - Rebuild toutes les images Docker" -ForegroundColor Gray
    Write-Host "     - Lance docker compose up -d --build" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  3. Quitter" -ForegroundColor Red
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
}

Show-Menu

$choice = Read-Host "`nChoisissez une option (1, 2 ou 3)"

switch ($choice) {
    "1" {
        Write-Host "`n>>> DEMARRAGE RAPIDE SELECTIONNE <<<" -ForegroundColor Green
        $buildMode = $false
    }
    "2" {
        Write-Host "`n>>> COMPILATION COMPLETE + BUILD SELECTIONNE <<<" -ForegroundColor Green
        $buildMode = $true
    }
    "3" {
        Write-Host "`nAu revoir!" -ForegroundColor Cyan
        exit 0
    }
    default {
        Write-Host "`nOption invalide. Lancement en mode rapide par defaut..." -ForegroundColor Yellow
        $buildMode = $false
    }
}

#############################################
# MODE COMPILATION COMPLETE
#############################################
if ($buildMode) {
    Write-Host "`nRecompilation Maven + Rebuild Docker..." -ForegroundColor Yellow

    #############################################
    # JAVA DETECTION
    #############################################
    Write-Host "`n  Detection de Java 17..." -ForegroundColor Cyan

    $javaFound = $false
    $javaPaths = @(
        "C:\JAVA",
        "C:\Program Files\Eclipse Adoptium\jdk-17*",
        "C:\Program Files\Eclipse Adoptium\jdk17*",
        "C:\Program Files\Java\jdk-17*",
        "C:\Program Files\Java\jdk17*"
    )

    # Try to find Java
    foreach ($pattern in $javaPaths) {
        $found = Get-ChildItem -Path (Split-Path $pattern -Parent) -Filter (Split-Path $pattern -Leaf) -Directory -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending |
                Select-Object -First 1

        if ($found) {
            $javaExe = Join-Path $found.FullName "bin\java.exe"
            if (Test-Path $javaExe) {
                $env:JAVA_HOME = $found.FullName
                $javaFound = $true
                break
            }
        }
    }

    # Check if JAVA_HOME is already set
    if (-not $javaFound -and $env:JAVA_HOME) {
        if (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe")) {
            $javaFound = $true
        }
    }

    if (-not $javaFound) {
        Write-Host "  ERREUR: Java 17 non trouve" -ForegroundColor Red
        Write-Host "  Installez Java 17 depuis: https://adoptium.net/" -ForegroundColor Yellow
        exit 1
    }

    # Clean JAVA_HOME
    $env:JAVA_HOME = $env:JAVA_HOME.TrimEnd('\')
    Write-Host "  Java detecte: $env:JAVA_HOME" -ForegroundColor Green

    # Add Java to PATH
    $javaBin = Join-Path $env:JAVA_HOME "bin"
    if ($env:PATH -notlike "*$javaBin*") {
        $env:PATH = "$javaBin;$env:PATH"
    }

    #############################################
    # MAVEN DETECTION
    #############################################
    Write-Host "`n  Detection de Maven..." -ForegroundColor Cyan

    $mavenSystemAvailable = $false
    try {
        $mavenVersion = mvn -version 2>$null
        if ($LASTEXITCODE -eq 0) {
            $mavenSystemAvailable = $true
            Write-Host "  Maven systeme detecte" -ForegroundColor Green
            Write-Host "    $($mavenVersion[0])" -ForegroundColor Gray
        }
    } catch {
        Write-Host "  Maven systeme non detecte (utilisation de Maven Wrapper)" -ForegroundColor Gray
    }

    #############################################
    # FONCTION: INSTALLER MAVEN WRAPPER
    #############################################
    function Install-MavenWrapper {
        param([string]$ProjectPath)

        Write-Host "  Installation du Maven Wrapper dans $ProjectPath..." -ForegroundColor Yellow

        $wrapperInstalled = $false

        # Méthode 1: Télécharger depuis Internet
        try {
            Write-Host "    Tentative de telechargement depuis Maven Central..." -ForegroundColor Gray
            New-Item -ItemType Directory -Path "$ProjectPath/.mvn/wrapper" -Force | Out-Null
            $baseUrl = "https://raw.githubusercontent.com/apache/maven/master/maven-wrapper/src/main/resources"
            $wrapperVersion = "3.2.0"
            $mavenVersion = "3.9.6"

            Invoke-WebRequest -Uri "$baseUrl/mvnw.cmd" -OutFile "$ProjectPath/mvnw.cmd" -UseBasicParsing -TimeoutSec 10
            Invoke-WebRequest -Uri "$baseUrl/mvnw" -OutFile "$ProjectPath/mvnw" -UseBasicParsing -TimeoutSec 10

            $wrapperProps = @"
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$mavenVersion/apache-maven-$mavenVersion-bin.zip
wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/$wrapperVersion/maven-wrapper-$wrapperVersion.jar
"@
            $wrapperProps | Out-File -FilePath "$ProjectPath/.mvn/wrapper/maven-wrapper.properties" -Encoding UTF8 -Force

            Invoke-WebRequest -Uri "https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/$wrapperVersion/maven-wrapper-$wrapperVersion.jar" `
                -OutFile "$ProjectPath/.mvn/wrapper/maven-wrapper.jar" -UseBasicParsing -TimeoutSec 10

            $wrapperInstalled = $true
            Write-Host "    Maven Wrapper telecharge avec succes" -ForegroundColor Green

        } catch {
            Write-Host "    Echec du telechargement: $($_.Exception.Message)" -ForegroundColor Yellow
        }

        # Méthode 2: Copier depuis backend (fallback)
        if (-not $wrapperInstalled) {
            try {
                Write-Host "    Tentative de copie depuis backend/..." -ForegroundColor Gray

                if (Test-Path "backend/mvnw.cmd") {
                    Copy-Item "backend/mvnw.cmd" "$ProjectPath/" -Force
                    Copy-Item "backend/mvnw" "$ProjectPath/" -Force -ErrorAction SilentlyContinue
                    Copy-Item "backend/.mvn" "$ProjectPath/" -Recurse -Force

                    $wrapperInstalled = $true
                    Write-Host "    Maven Wrapper copie depuis backend avec succes" -ForegroundColor Green
                } else {
                    Write-Host "    ERREUR: backend/mvnw.cmd non trouve" -ForegroundColor Red
                }
            } catch {
                Write-Host "    ERREUR: Copie echouee: $($_.Exception.Message)" -ForegroundColor Red
            }
        }

        return $wrapperInstalled
    }

    #############################################
    # FONCTION: COMPILER AVEC MAVEN
    #############################################
    function Invoke-MavenCompile {
        param(
            [string]$ProjectPath,
            [string]$ProjectName,
            [bool]$UseSystemMaven = $false
        )

        Write-Host "`n  Compilation du projet $ProjectName avec Maven..." -ForegroundColor Cyan

        Push-Location $ProjectPath

        if (-not (Test-Path "pom.xml")) {
            Write-Host "  INFO: Pas de pom.xml dans $ProjectPath, skip compilation" -ForegroundColor Gray
            Pop-Location
            return $true
        }

        $compiled = $false
        $mavenCommand = ""

        # Priorité 1: Maven wrapper (mvnw.cmd)
        if (Test-Path ".\mvnw.cmd") {
            Write-Host "  Utilisation de Maven Wrapper (mvnw.cmd)" -ForegroundColor Gray
            $mavenCommand = ".\mvnw.cmd"
        }
        # Priorité 2: Maven wrapper Unix (mvnw)
        elseif (Test-Path "mvnw") {
            Write-Host "  Utilisation de Maven Wrapper (mvnw)" -ForegroundColor Gray
            $mavenCommand = ".\mvnw"
        }
        # Priorité 3: Maven système (si disponible)
        elseif ($UseSystemMaven -and $mavenSystemAvailable) {
            Write-Host "  Utilisation de Maven systeme" -ForegroundColor Gray
            $mavenCommand = "mvn"
        }
        # Sinon: Erreur
        else {
            Write-Host "  ERREUR: Aucun Maven disponible pour compiler $ProjectName" -ForegroundColor Red
            Write-Host "  Solutions:" -ForegroundColor Yellow
            Write-Host "    1. Installez Maven Wrapper dans le projet" -ForegroundColor Yellow
            Write-Host "    2. Installez Maven sur votre systeme" -ForegroundColor Yellow
            Pop-Location
            return $false
        }

        # Exécuter la compilation
        Write-Host "  Commande: $mavenCommand clean package -DskipTests" -ForegroundColor Gray
        & $mavenCommand clean package -DskipTests

        if ($LASTEXITCODE -eq 0) {
            # Vérifier que le JAR a été créé
            $jarFiles = Get-ChildItem -Path "target" -Filter "*.jar" -File -ErrorAction SilentlyContinue |
                    Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" }

            if ($jarFiles) {
                Write-Host "  Compilation Maven $ProjectName reussie" -ForegroundColor Green
                Write-Host "    JAR cree: $($jarFiles[0].Name)" -ForegroundColor Gray
                $compiled = $true
            } else {
                Write-Host "  AVERTISSEMENT: Compilation reussie mais aucun JAR trouve" -ForegroundColor Yellow
                $compiled = $true
            }
        } else {
            Write-Host "  ERREUR: La compilation Maven $ProjectName a echoue (code: $LASTEXITCODE)" -ForegroundColor Red
        }

        Pop-Location
        return $compiled
    }

    #############################################
    # INSTALLER MAVEN WRAPPER SI NECESSAIRE
    #############################################
    Write-Host "`n  Verification Maven Wrapper..." -ForegroundColor Cyan

    $projectsNeedingWrapper = @("kafka-stream-job/project")

    foreach ($project in $projectsNeedingWrapper) {
        if ((Test-Path "$project/pom.xml") -and -not (Test-Path "$project/mvnw.cmd") -and -not $mavenSystemAvailable) {
            $installed = Install-MavenWrapper -ProjectPath $project
            if (-not $installed) {
                Write-Host "  AVERTISSEMENT: Impossible d'installer Maven Wrapper dans $project" -ForegroundColor Yellow
                Write-Host "  La compilation utilisera Maven systeme si disponible" -ForegroundColor Yellow
            }
        } elseif ((Test-Path "$project/pom.xml") -and -not (Test-Path "$project/mvnw.cmd") -and $mavenSystemAvailable) {
            Write-Host "  Maven systeme disponible, pas besoin de Maven Wrapper pour $project" -ForegroundColor Gray
        }
    }

    #############################################
    # MAVEN COMPILATION - BACKEND
    #############################################
    $backendCompiled = Invoke-MavenCompile -ProjectPath "backend" -ProjectName "backend" -UseSystemMaven $mavenSystemAvailable

    if (-not $backendCompiled) {
        Write-Host "`nERREUR CRITIQUE: La compilation du backend a echoue" -ForegroundColor Red
        exit 1
    }

    # Vérification supplémentaire pour le backend (critique)
    if (-not (Test-Path "backend/target/demo-0.0.1-SNAPSHOT.jar")) {
        Write-Host "  ERREUR: Le fichier JAR backend n'a pas ete cree" -ForegroundColor Red
        exit 1
    }

    #############################################
    # MAVEN COMPILATION - KAFKA STREAM JOB
    #############################################
    $kafkaCompiled = Invoke-MavenCompile -ProjectPath "kafka-stream-job/project" -ProjectName "kafka-stream-job" -UseSystemMaven $mavenSystemAvailable

    if (-not $kafkaCompiled) {
        Write-Host "  AVERTISSEMENT: Compilation kafka-stream-job echouee, mais on continue..." -ForegroundColor Yellow
    }
}

#############################################
# DOCKER COMPOSE
#############################################
if ($buildMode) {
    Write-Host "`n  Rebuild + lancement de Docker Compose..." -ForegroundColor Cyan
    Write-Host "`nCommande utilisee :" -ForegroundColor Gray
    Write-Host "docker compose up -d --build" -ForegroundColor White
    docker compose up -d --build
} else {
    Write-Host "`n  Lancement de Docker Compose (mode rapide)..." -ForegroundColor Cyan
    Write-Host "`nCommande utilisee :" -ForegroundColor Gray
    Write-Host "docker compose up -d" -ForegroundColor White
    docker compose up -d
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nERREUR: docker compose up a echoue." -ForegroundColor Red
    Write-Host "Verifiez la syntaxe de votre fichier docker-compose.yml" -ForegroundColor Yellow
    exit 1
}

Write-Host "`n  Attente du demarrage des conteneurs (30s)..." -ForegroundColor Gray
Start-Sleep -Seconds 30

#############################################
# VERIFICATION DES CONTENEURS
#############################################
Write-Host "`n  Verification des conteneurs..." -ForegroundColor Cyan

$criticalContainers = @(
    "timescaledb",
    "postgres",
    "kafka",
    "zookeeper",
    "backend"
)

$allRunning = $true
foreach ($container in $criticalContainers) {
    $status = docker ps --filter "name=$container" --filter "status=running" --format "{{.Names}}"
    if ($status) {
        Write-Host "    $container : Running" -ForegroundColor Green
    } else {
        Write-Host "    $container : NOT RUNNING" -ForegroundColor Red
        $allRunning = $false
    }
}

# Vérifier les Kafka consumers
$kafkaConsumers = @("rss-parser", "ozone-avg", "pm10-avg", "pm25-avg", "ozone-max", "pm10-max", "pm25-max")
$runningConsumers = 0
foreach ($consumer in $kafkaConsumers) {
    $status = docker ps --filter "name=$consumer" --filter "status=running" --format "{{.Names}}"
    if ($status) {
        $runningConsumers++
    }
}

$consumerColor = if ($runningConsumers -eq $kafkaConsumers.Count) { "Green" } elseif ($runningConsumers -gt 0) { "Yellow" } else { "Red" }
Write-Host "    Kafka consumers: $runningConsumers/$($kafkaConsumers.Count) running" -ForegroundColor $consumerColor

if (-not $allRunning) {
    Write-Host "`nATTENTION: Certains conteneurs critiques ne sont pas demarres" -ForegroundColor Yellow
    Write-Host "Verifiez les logs: docker compose logs <service-name>" -ForegroundColor Yellow
}

#############################################
# RESUME FINAL
#############################################
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  DEMARRAGE TERMINE" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "`nURLs d'acces:" -ForegroundColor Yellow
Write-Host "  Frontend:        http://localhost:5173" -ForegroundColor Cyan
Write-Host "  Backend:         http://localhost:8095" -ForegroundColor Cyan
Write-Host "  Kafka Connect:   http://localhost:8083" -ForegroundColor Cyan
Write-Host "  Schema Registry: http://localhost:8081" -ForegroundColor Cyan
Write-Host "  HDFS NameNode:   http://localhost:9870" -ForegroundColor Cyan

Write-Host "`nBases de donnees:" -ForegroundColor Yellow
Write-Host "  TimescaleDB:     localhost:5432 (db: db_airquality)" -ForegroundColor Cyan
Write-Host "  PostgreSQL:      localhost:5433 (db: clients)" -ForegroundColor Cyan

Write-Host "`nCommandes utiles:" -ForegroundColor Yellow
Write-Host "  Voir tous les logs:         docker compose logs -f" -ForegroundColor Gray
Write-Host "  Logs Backend:               docker compose logs -f backend" -ForegroundColor Gray
Write-Host "  Logs Kafka:                 docker compose logs -f kafka" -ForegroundColor Gray
Write-Host "  Logs Consumers:             docker compose logs -f rss-parser" -ForegroundColor Gray
Write-Host "  Arreter tous les services:  docker compose down" -ForegroundColor Gray
Write-Host "  Restart un service:         docker compose restart <nom>" -ForegroundColor Gray

Write-Host "`n========================================" -ForegroundColor Cyan

# Afficher un avertissement si des consumers ne tournent pas
if ($runningConsumers -lt $kafkaConsumers.Count) {
    Write-Host "`n ATTENTION: Tous les Kafka consumers ne sont pas demarres" -ForegroundColor Yellow
    Write-Host "   Verifiez: docker compose logs rss-parser" -ForegroundColor Yellow
}
