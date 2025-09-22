# Configuration Checkstyle pour AREA_Back

Ce projet utilise Checkstyle pour maintenir la qualité et la cohérence du code Java.

## 🚀 Utilisation rapide

### Analyser tout le projet
```bash
./gradlew check
# ou
./lint.sh
```

### Analyser uniquement le code source principal
```bash
./gradlew checkstyleMain
# ou
./lint.sh --main-only
```

### Analyser uniquement les tests
```bash
./gradlew checkstyleTest
# ou
./lint.sh --test-only
```

## 📊 Rapports

Les rapports Checkstyle sont générés dans :
- **HTML** (lisible) : `build/reports/checkstyle/main.html`
- **XML** (pour outils) : `build/reports/checkstyle/main.xml`

## ⚙️ Configuration

### Fichiers de configuration
- `config/checkstyle/checkstyle.xml` : Règles Checkstyle principales
- `config/checkstyle/suppressions.xml` : Exceptions et suppressions
- `build.gradle` : Configuration du plugin Checkstyle

### Règles principales vérifiées
- ✅ **Conventions de nommage** (classes, méthodes, variables)
- ✅ **Organisation des imports** (ordre, éviter les `.*`)
- ✅ **Formatage du code** (espaces, accolades, indentation)
- ✅ **Complexité du code** (longueur des méthodes, nombre de paramètres)
- ✅ **Bonnes pratiques** (éviter les magic numbers, final parameters)

### Suppressions configurées
- 🔕 **Controllers** : Paramètres non-finaux, conditions ternaires
- 🔕 **DTOs/Entities** : Visibilité des champs
- 🔕 **Tests** : Magic numbers, imports statiques
- 🔕 **Application.java** : Constructeur de classe utilitaire
- 🔕 **Spring patterns** : Imports avec étoile pour annotations communes

## 🛠️ Intégration avec les outils

### VS Code
1. Installez l'extension "Checkstyle for Java"
2. La validation se fera automatiquement à l'écriture

### IntelliJ IDEA
1. Allez dans `File > Settings > Tools > Checkstyle`
2. Ajoutez `config/checkstyle/checkstyle.xml` comme fichier de configuration

### CI/CD
Ajoutez dans votre pipeline :
```yaml
- name: Run Checkstyle
  run: ./gradlew checkstyleMain checkstyleTest
```

## 🚨 Violations courantes et solutions

### 1. Nom de package incorrect
```java
// ✅ Autorisé
package area.server.AREA_Back.controller;

// ✅ Également autorisé
package area.server.areaback.controller;
```
> **Note** : Les underscores sont autorisés dans les noms de packages pour ce projet.

### 2. Imports avec étoile
```java
// ❌ Éviter
import jakarta.persistence.*;
import org.springframework.web.bind.annotation.*;

// ✅ Préférer
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
```

### 3. Ordre des imports
```java
// ✅ Ordre correct
import java.util.List;        // java.*
import javax.validation.*;    // javax.*
import org.springframework.*; // org.*
import com.company.*;         // com.*
```

### 4. Paramètres finaux
```java
// ❌ Non-final
public void method(String param) { }

// ✅ Final (dans les services/utilitaires)
public void method(final String param) { }
```

### 5. Opérateurs ternaires
```java
// ❌ Sur une seule ligne
Sort sort = sortDir.equals("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

// ✅ Multi-lignes
Sort sort = sortDir.equals("desc") 
    ? Sort.by(sortBy).descending() 
    : Sort.by(sortBy).ascending();
```

## 📈 Métriques et seuils

- **Longueur de ligne** : Maximum 120 caractères
- **Longueur de méthode** : Maximum 150 lignes
- **Nombre de paramètres** : Maximum 7 paramètres
- **Magic numbers** : Éviter (sauf -1, 0, 1, 2)

## 🔧 Personnalisation

Pour modifier les règles :
1. Éditez `config/checkstyle/checkstyle.xml`
2. Ajoutez des suppressions dans `config/checkstyle/suppressions.xml`
3. Testez avec `./gradlew checkstyleMain`

## 📚 Ressources

- [Documentation Checkstyle](https://checkstyle.sourceforge.io/)
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Spring Boot Best Practices](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/)

## 🎯 Objectifs qualité

- 🔴 **Critique** : 0 erreur bloquante
- 🟡 **Important** : < 10 violations par 1000 lignes
- 🟢 **Optimal** : < 5 violations par 1000 lignes

---

💡 **Astuce** : Utilisez `./lint.sh --help` pour voir toutes les options disponibles.