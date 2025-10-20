---
sidebar_position: 2
---

# Doxygen & Docusaurus Integration

Ce guide explique comment utiliser la documentation technique générée par Doxygen et intégrée dans Docusaurus.

## Vue d'ensemble

Le projet AREA Backend utilise deux systèmes de documentation complémentaires :

- **Docusaurus** : Pour les guides, tutoriels et documentation conceptuelle
- **Doxygen** : Pour la référence technique détaillée du code Java

## 🚀 Accès rapide

### Via Docusaurus

Lorsque Docusaurus est démarré (`npm start`), vous pouvez accéder à :

- **Documentation principale** : http://localhost:3000
- **API Reference (Doxygen)** : http://localhost:3000/doxygen/html/index.html
- Utilisez le menu de navigation en haut pour basculer entre les sections

### Liens directs

Dans le menu de navigation :
- **Documentation** : Guides et tutoriels Docusaurus
- **API Reference (Doxygen)** : Documentation technique du code

## 📖 Génération de la documentation Doxygen

### Prérequis

Installez Doxygen sur votre système :

```bash
# Ubuntu/Debian
sudo apt-get install doxygen

# macOS
brew install doxygen

# Windows
# Téléchargez depuis https://www.doxygen.nl/download.html
```

### Génération automatique

Utilisez le script fourni :

```bash
# À la racine du projet AREA_Back
./scripts/generate-doxygen.sh
```

Ce script :
1. ✅ Vérifie que Doxygen est installé
2. 📚 Génère la documentation HTML
3. 📁 Place les fichiers dans `docusaurus/static/doxygen/html/`

### Génération manuelle

Si vous préférez générer manuellement :

```bash
# À la racine du projet
doxygen Doxyfile
```

## ⚙️ Configuration

### Fichiers de configuration

| Fichier | Description |
|---------|-------------|
| `Doxyfile` | Configuration Doxygen (racine du projet) |
| `docusaurus/docusaurus.config.ts` | Configuration Docusaurus avec liens Doxygen |
| `scripts/generate-doxygen.sh` | Script de génération automatique |

### Paramètres importants (Doxyfile)

```doxyfile
# Répertoire d'entrée (code source)
INPUT = src/main/java

# Répertoire de sortie
OUTPUT_DIRECTORY = docusaurus/static/doxygen

# Génération HTML
GENERATE_HTML = YES
HTML_OUTPUT = html

# Optimisation pour Java
OPTIMIZE_OUTPUT_JAVA = YES
JAVADOC_AUTOBRIEF = YES
```

## 📝 Documenter votre code

Pour que votre code apparaisse correctement dans Doxygen, utilisez les commentaires Javadoc :

### Classes

```java
/**
 * @brief Service de gestion des actions AREA.
 * 
 * Ce service permet de créer, modifier et supprimer des actions
 * dans le système AREA.
 * 
 * @author Votre Nom
 * @version 1.0
 * @since 2025-10-20
 */
@Service
public class ActionService {
    // ...
}
```

### Méthodes

```java
/**
 * @brief Crée une nouvelle action.
 * 
 * Cette méthode crée une action avec les paramètres fournis
 * et la sauvegarde dans la base de données.
 * 
 * @param name Le nom de l'action (ne doit pas être vide)
 * @param type Le type d'action à créer
 * @param userId L'ID de l'utilisateur propriétaire
 * @return L'action nouvellement créée
 * @throws IllegalArgumentException Si le nom est vide
 * @throws UserNotFoundException Si l'utilisateur n'existe pas
 * 
 * @see ActionType
 * @see Action
 */
public Action createAction(String name, ActionType type, Long userId) {
    // Implementation
}
```

### Paramètres et retours

```java
/**
 * @brief Calcule le résultat d'une action.
 * 
 * @param action L'action à exécuter
 * @param context Le contexte d'exécution
 * @return Le résultat de l'action, ou null si échec
 * 
 * @note Cette méthode peut être coûteuse en temps
 * @warning Ne pas appeler dans une boucle serrée
 */
public ActionResult execute(Action action, ExecutionContext context) {
    // Implementation
}
```

## 🎨 Personnalisation

### Modifier l'apparence Doxygen

Éditez les paramètres de couleur dans `Doxyfile` :

```doxyfile
# Teinte (0-360)
HTML_COLORSTYLE_HUE = 220

# Saturation (0-255)
HTML_COLORSTYLE_SAT = 100

# Gamma (40-240)
HTML_COLORSTYLE_GAMMA = 80
```

### Ajouter un style CSS personnalisé

1. Créez un fichier CSS (ex: `doxygen-custom.css`)
2. Ajoutez dans `Doxyfile` :

```doxyfile
HTML_EXTRA_STYLESHEET = doxygen-custom.css
```

### Filtrer les fichiers

Pour exclure certains fichiers de la documentation :

```doxyfile
EXCLUDE_PATTERNS = */test/* */legacy/* */deprecated/*
```

## 🔍 Utilisation de Doxygen

### Navigation

- **Barre de recherche** : En haut à droite, recherche dans toute la doc
- **Menu latéral** : Navigation par packages et classes
- **Onglets** : Classes, Namespaces, Files, etc.

### Fonctionnalités utiles

1. **Graphes de dépendances** : Visualisez les relations entre classes
2. **Code source** : Cliquez sur une méthode pour voir son implémentation
3. **Recherche** : Trouvez rapidement une classe ou méthode
4. **Arborescence** : Vue hiérarchique des packages

### Astuces

- 💡 **Ctrl+F** dans la page pour rechercher localement
- 🔗 Les liens entre classes sont cliquables
- 📊 Activez les graphes pour une meilleure visualisation
- 🎯 Utilisez les filtres pour affiner votre recherche

## 🔄 Workflow complet

### 1. Développement

```java
// Écrivez votre code avec des commentaires Javadoc
/**
 * @brief Ma nouvelle fonctionnalité
 */
public void myNewFeature() {
    // Code
}
```

### 2. Génération

```bash
./scripts/generate-doxygen.sh
```

### 3. Vérification locale

```bash
cd docusaurus
npm start
# Ouvrez http://localhost:3000/doxygen/html/index.html
```

### 4. Intégration

```bash
# Les fichiers générés sont dans .gitignore
# Seuls les fichiers de configuration sont versionnés
git add Doxyfile
git commit -m "docs: update Doxygen configuration"
```

## 🐛 Dépannage

### Problème : Doxygen ne génère rien

**Solution** :
```bash
# Vérifiez l'installation
doxygen --version

# Vérifiez les chemins dans Doxyfile
grep INPUT Doxyfile
grep OUTPUT_DIRECTORY Doxyfile
```

### Problème : Liens cassés dans Docusaurus

**Solution** :
```bash
# Assurez-vous que le chemin est correct
ls -la docusaurus/static/doxygen/html/index.html

# Régénérez
./scripts/generate-doxygen.sh

# Redémarrez Docusaurus
cd docusaurus && npm start
```

### Problème : Documentation incomplète

**Solution** :
- Vérifiez que vos commentaires Javadoc sont corrects
- Assurez-vous que `EXTRACT_ALL = YES` dans Doxyfile
- Vérifiez que les fichiers ne sont pas exclus par `EXCLUDE_PATTERNS`

## 📚 Ressources

- [Documentation Doxygen](https://www.doxygen.nl/manual/)
- [Javadoc Guide Oracle](https://www.oracle.com/technical-resources/articles/java/javadoc-tool.html)
- [Docusaurus Docs](https://docusaurus.io/)

## ✅ Bonnes pratiques

1. **Documentez toutes les classes publiques** : Minimum @brief et description
2. **Documentez tous les paramètres** : Utilisez @param pour chaque paramètre
3. **Expliquez les exceptions** : Utilisez @throws pour chaque exception
4. **Ajoutez des exemples** : Utilisez @code pour montrer l'usage
5. **Maintenez à jour** : Régénérez après chaque changement important
6. **Testez localement** : Vérifiez la doc avant de commit

## 🎯 Prochaines étapes

- [ ] Enrichir les commentaires Javadoc existants
- [ ] Ajouter des exemples de code dans la documentation
- [ ] Configurer la génération automatique en CI/CD
- [ ] Déployer la documentation sur un serveur de production
