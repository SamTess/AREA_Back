---
sidebar_position: 5
---

# API Reference

Cette section contient la documentation technique détaillée de l'API AREA Backend.

## Documentation disponible

### 📚 Doxygen - Code Documentation

La documentation Doxygen fournit une référence complète de toutes les classes Java, méthodes et interfaces du backend AREA.

**[📖 Accéder à la documentation Doxygen](/doxygen/html/index.html)**

Features:
- 🔍 Navigation par classes et packages
- 📝 Documentation détaillée de chaque méthode
- 🔗 Graphes de dépendances entre classes
- 🎯 Recherche rapide dans le code source

### 🔌 Swagger/OpenAPI - REST API

La documentation Swagger fournit une interface interactive pour tester les endpoints REST de l'API.

**[🚀 Accéder à Swagger UI](http://localhost:8080/swagger-ui.html)**

Features:
- 🧪 Test des endpoints en direct
- 📋 Schémas de requêtes/réponses
- 🔐 Authentification intégrée
- 📊 Modèles de données

## Comment générer la documentation Doxygen

Si vous souhaitez régénérer la documentation Doxygen localement :

```bash
# À la racine du projet
./scripts/generate-doxygen.sh
```

Ou manuellement :

```bash
# Installer Doxygen (si nécessaire)
sudo apt-get install doxygen  # Ubuntu/Debian
# brew install doxygen          # macOS

# Générer la documentation
doxygen Doxyfile
```

La documentation sera générée dans `docusaurus/static/doxygen/html/`.

## Structure de la documentation

```
AREA_Back/
├── Doxyfile                        # Configuration Doxygen
├── docusaurus/
│   ├── docs/                       # Documentation Docusaurus (guides)
│   └── static/
│       └── doxygen/
│           └── html/               # Documentation Doxygen générée
└── scripts/
    └── generate-doxygen.sh         # Script de génération
```

## Tips pour utiliser Doxygen

1. **Recherche rapide** : Utilisez la barre de recherche en haut à droite
2. **Navigation** : Utilisez l'arborescence à gauche pour explorer les packages
3. **Graphes** : Activez les diagrammes pour voir les relations entre classes
4. **Code source** : Cliquez sur les méthodes pour voir le code source

## Mise à jour de la documentation

La documentation Doxygen doit être régénérée après chaque modification importante du code :

1. Modifiez le code Java avec les commentaires Javadoc
2. Exécutez `./scripts/generate-doxygen.sh`
3. Commitez les changements (optionnel - peut être dans .gitignore)

:::tip Bonnes pratiques
Documentez toujours vos classes et méthodes publiques avec des commentaires Javadoc pour qu'elles apparaissent correctement dans Doxygen.
:::

:::info CI/CD Integration
Vous pouvez ajouter la génération de Doxygen dans votre pipeline CI/CD pour automatiser la mise à jour de la documentation.
:::
