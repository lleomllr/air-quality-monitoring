# Démarrer les consommateurs Kafka Streams

Compiler Maven à chaque modification (```mvn clean package -DskipTests```)\

## Méthode 1
- Build l'image des consommateurs (```docker build -t consumer-image:1.0 .```)
- Lancer Docker (```docker compose up -d```)

## Méthode 2
- Lancer Docker une première fois (```docker compose up -d```)
- Docker va planter à cause de la création en parralèle de l'image des consommateurs, c'est normal.
- Relancer Docker (```docker compose up -d```)