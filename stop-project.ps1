########################################
# Script d'arrêt global
# Arrête tous les conteneurs Docker et supprime les volumes
########################################

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  ARRET - Tous les conteneurs Docker" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# Arrêt de tous les conteneurs
Write-Host "Arret de tous les conteneurs en cours..." -ForegroundColor Yellow
docker stop (docker ps -aq) 2>$null

# Suppression de tous les conteneurs
Write-Host "Suppression de tous les conteneurs..." -ForegroundColor Yellow
docker rm (docker ps -aq) 2>$null

# Suppression de tous les volumes non utilisés
Write-Host "Suppression de tous les volumes..." -ForegroundColor Yellow
docker volume prune -f 2>$null

# Vérification
if ($LASTEXITCODE -eq 0) {
    Write-Host "`nOK: Tous les conteneurs arretes et volumes nettoyes`n" -ForegroundColor Green
} else {
    Write-Host "`nATTENTION: Une erreur s'est produite lors de l'arret ou du nettoyage`n" -ForegroundColor Yellow
}

Write-Host "========================================`n" -ForegroundColor Cyan