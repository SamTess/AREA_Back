# Configuration Locale - AREA_Back

## ⚠️ IMPORTANT - Configuration Requise

Ce projet utilise des variables d'environnement pour la sécurité. **L'application ne démarrera PAS** sans ces variables.

## 🚀 Configuration Rapide

### 1. Copier le fichier exemple
```bash
cp .env.example .env
```

### 2. Éditer le fichier .env avec vos valeurs
```bash
nano .env
```

### 3. Variables OBLIGATOIRES à configurer

```bash
# Base de données (OBLIGATOIRE)
DATABASE_URL=jdbc:postgresql://localhost:5432/area_db
DATABASE_USERNAME=area_user
DATABASE_PASSWORD=votre_mot_de_passe_db

# Sécurité admin (OBLIGATOIRE)
ADMIN_USERNAME=admin
ADMIN_PASSWORD=votre_mot_de_passe_admin
```

### 4. Lancer l'application
```bash
./gradlew bootRun
```

## 🔧 Configuration Minimale .env

Si vous voulez démarrer rapidement, créez un fichier `.env` avec ceci :

```bash
# Configuration de développement local
DATABASE_URL=jdbc:postgresql://localhost:5432/area_db
DATABASE_USERNAME=area_user
DATABASE_PASSWORD=area_password
ADMIN_USERNAME=admin
ADMIN_PASSWORD=admin123
SPRING_PROFILES_ACTIVE=dev
JPA_DDL_AUTO=update
JPA_SHOW_SQL=true
SWAGGER_ENABLED=true
SWAGGER_UI_ENABLED=true
```

## 🐳 Démarrer PostgreSQL avec Docker

```bash
docker run --name postgres-area \
  -e POSTGRES_DB=area_db \
  -e POSTGRES_USER=area_user \
  -e POSTGRES_PASSWORD=area_password \
  -p 5432:5432 \
  -d postgres:15
```

## ❌ Erreurs Communes

### "Failed to configure a DataSource"
➡️ Vérifiez que `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` sont dans votre `.env`

### "Could not resolve placeholder"
➡️ Il manque une variable d'environnement dans votre `.env`

### "Connection refused"
➡️ PostgreSQL n'est pas démarré ou pas accessible

## 🔐 Sécurité

- ✅ Le fichier `.env` est dans `.gitignore`
- ✅ Aucune valeur sensible n'est dans le code
- ⚠️ Changez les mots de passe par défaut
- ⚠️ Ne partagez jamais votre fichier `.env`

## 📋 Variables Disponibles

Voir le fichier `.env.example` pour la liste complète des variables configurables.