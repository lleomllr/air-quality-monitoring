#!/usr/bin/env bash
# ========================================
#   ARRET - AirQuality Monitor
# ========================================

set -euo pipefail

echo ""
echo "========================================"
echo "  ARRET - AirQuality Monitor"
echo "========================================"
echo ""

echo "Choisissez une option :"
echo "1) Arreter tous les services (conserver les donnees)"
echo "2) Arreter ET supprimer les donnees (volumes)"
echo ""

read -rp "Votre choix (1 ou 2) : " choice

case "$choice" in
  "1")
    echo ""
    echo "Arret des conteneurs..."
    if docker compose down; then
      echo ""
      echo "OK: Services arretes (donnees conservees)"
    else
      echo ""
      echo "ERREUR lors de l'arret" >&2
      exit 1
    fi
    ;;
  "2")
    echo ""
    echo "ATTENTION: Les donnees seront supprimees !"
    read -rp "Confirmer ? (o/n) : " confirm

    if [[ "$confirm" == "o" || "$confirm" == "O" ]]; then
      echo ""
      echo "Arret et suppression des volumes..."
      if docker compose down -v; then
        echo ""
        echo "OK: Services arretes et donnees supprimees"
      else
        echo ""
        echo "ERREUR lors de l'arret" >&2
        exit 1
      fi
    else
      echo ""
      echo "Operation annulee"
      exit 0
    fi
    ;;
  *)
    echo ""
    echo "Choix invalide" >&2
    exit 1
    ;;
esac

echo ""
echo "========================================"
echo "  ARRET TERMINE"
echo "========================================"
echo ""
echo "Pour redemarrer le projet :"
echo "  ./start_airquality.sh"
echo ""
