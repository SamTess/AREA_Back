# Guide de Sécurité - AREA_Back

## Variables d'Environnement

Ce projet utilise des variables d'environnement pour sécuriser les informations sensibles. Voici comment les configurer :

### 1. Configuration des Variables d'Environnement

#### Méthode 1 : Fichier .env (Recommandé pour le développement)
```bash
# Copiez le fichier exemple
cp .env.example .env

# Éditez le fichier .env avec vos vraies valeurs
nano .env
```

#### Méthode 2 : Variables système
```bash
# Linux/Mac
export DATABASE_PASSWORD="votre_mot_de_passe_secure"
export ADMIN_PASSWORD="votre_mot_de_passe_admin_secure"

# Windows
set DATABASE_PASSWORD=votre_mot_de_passe_secure
set ADMIN_PASSWORD=votre_mot_de_passe_admin_secure
```

#### Méthode 3 : IDE Configuration
Dans IntelliJ IDEA ou VS Code, configurez les variables d'environnement dans la configuration de lancement.

### 2. Variables Critiques à Configurer

| Variable | Description | Défaut | Sécurité |
|----------|-------------|---------|----------|
| `DATABASE_PASSWORD` | Mot de passe DB | `changeme_in_production` | ⚠️ CRITIQUE |
| `ADMIN_PASSWORD` | Mot de passe admin | `changeme_in_production` | ⚠️ CRITIQUE |
| `DATABASE_USERNAME` | Utilisateur DB | `area_user` | ⚠️ Important |
| `DATABASE_URL` | URL de la DB | `localhost:5432/area_db` | ℹ️ Important |

### 3. Profils Spring

#### Développement
```bash
SPRING_PROFILES_ACTIVE=dev
```
- Logs détaillés
- H2 console activée
- Actuator complet
- Swagger UI activé

#### Production
```bash
SPRING_PROFILES_ACTIVE=prod
```
- Logs minimalistes
- Sécurité renforcée
- Endpoints limités
- SSL recommandé

### 4. Bonnes Pratiques de Sécurité

#### ✅ À FAIRE
- Utilisez des mots de passe forts (>12 caractères)
- Changez TOUS les mots de passe par défaut
- Utilisez des profils Spring appropriés
- Activez HTTPS en production
- Sauvegardez régulièrement la base de données
- Surveillez les logs d'accès

#### ❌ À ÉVITER
- Ne commitez jamais le fichier `.env`
- N'utilisez pas les mots de passe par défaut
- Ne laissez pas les logs SQL activés en production
- N'exposez pas tous les endpoints Actuator en production
- N'utilisez pas HTTP en production

### 5. Configuration Docker

Pour Docker, utilisez un fichier `.env` ou passez les variables directement :

```bash
# Avec fichier .env
docker-compose --env-file .env up

# Avec variables directes
docker run -e DATABASE_PASSWORD=secure_password area-back
```

### 6. Vérification de la Configuration

Lancez l'application et vérifiez :

```bash
# Vérifiez que les variables sont bien chargées
curl http://localhost:8081/actuator/env

# Testez la connexion DB
curl http://localhost:8081/actuator/health
```

### 7. En cas de Problème

#### Variables non reconnues
- Vérifiez l'orthographe des noms de variables
- Redémarrez l'application après modification
- Vérifiez les logs au démarrage

#### Connexion DB échoue
- Vérifiez `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- Testez la connexion manuellement : `psql -h localhost -U area_user -d area_db`

#### Accès Swagger refusé
- Vérifiez `SWAGGER_ENABLED=true`
- Vérifiez la configuration Spring Security

### 8. Contact

Pour toute question de sécurité, contactez l'équipe de développement.

---
⚠️ **RAPPEL IMPORTANT** : Ne partagez jamais vos variables d'environnement de production !