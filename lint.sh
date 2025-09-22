#!/bin/bash

# Script pour exécuter Checkstyle sur le projet AREA_Back
# Usage: ./lint.sh [options]
# Options:
#   --main-only    : Analyser uniquement le code source principal
#   --test-only    : Analyser uniquement les tests
#   --fix-imports  : Réorganiser automatiquement les imports (nécessite IDE)

set -e

echo "🔍 Analyse du code avec Checkstyle..."

# Couleurs pour l'affichage
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Fonction pour afficher les statistiques
show_stats() {
    local report_file="$1"
    local context="$2"
    
    if [ -f "$report_file" ]; then
        local error_count=$(grep -o '\[ERROR\]' "$report_file" | wc -l)
        local warning_count=$(grep -o '\[WARN\]' "$report_file" | wc -l)
        
        echo -e "${BLUE}📊 Statistiques $context:${NC}"
        echo -e "   ${RED}Erreurs: $error_count${NC}"
        echo -e "   ${YELLOW}Avertissements: $warning_count${NC}"
        echo
    fi
}

# Fonction pour analyser le code principal
analyze_main() {
    echo -e "${BLUE}🔍 Analyse du code source principal...${NC}"
    ./gradlew checkstyleMain
    show_stats "build/reports/checkstyle/main.xml" "du code principal"
}

# Fonction pour analyser les tests
analyze_test() {
    echo -e "${BLUE}🧪 Analyse des tests...${NC}"
    ./gradlew checkstyleTest
    show_stats "build/reports/checkstyle/test.xml" "des tests"
}

# Fonction pour analyser tout
analyze_all() {
    echo -e "${BLUE}🔍 Analyse complète du projet...${NC}"
    ./gradlew check
    show_stats "build/reports/checkstyle/main.xml" "du code principal"
    show_stats "build/reports/checkstyle/test.xml" "des tests"
}

# Fonction pour afficher les conseils d'amélioration
show_tips() {
    echo -e "${GREEN}💡 Conseils pour corriger les violations courantes:${NC}"
    echo
    echo -e "${YELLOW}1. Noms de packages:${NC}"
    echo "   - Les underscores sont autorisés dans ce projet"
    echo "   - Utilisez des lettres minuscules et des points de préférence"
    echo
    echo -e "${YELLOW}2. Imports:${NC}"
    echo "   - Évitez les imports avec étoile (.*)"
    echo "   - Organisez les imports dans l'ordre: java, javax, org, com"
    echo "   - Supprimez les imports inutilisés"
    echo
    echo -e "${YELLOW}3. Formatage:${NC}"
    echo "   - Utilisez des espaces au lieu de tabulations"
    echo "   - Placez les opérateurs ternaires (?) sur de nouvelles lignes"
    echo "   - Déclarez les paramètres comme final quand c'est possible"
    echo
    echo -e "${YELLOW}4. Rapports détaillés:${NC}"
    echo "   - HTML: build/reports/checkstyle/main.html"
    echo "   - XML:  build/reports/checkstyle/main.xml"
    echo
}

# Traitement des arguments
case "${1:-}" in
    --main-only)
        analyze_main
        ;;
    --test-only)
        analyze_test
        ;;
    --fix-imports)
        echo -e "${YELLOW}⚠️  Fonctionnalité non implémentée${NC}"
        echo "Utilisez votre IDE pour réorganiser les imports automatiquement"
        echo "Dans VS Code: Ctrl+Shift+O (Organiser les imports)"
        ;;
    --help|-h)
        echo "Usage: $0 [options]"
        echo "Options:"
        echo "  --main-only    Analyser uniquement le code source principal"
        echo "  --test-only    Analyser uniquement les tests"
        echo "  --fix-imports  Afficher les conseils pour corriger les imports"
        echo "  --help, -h     Afficher cette aide"
        ;;
    "")
        analyze_all
        ;;
    *)
        echo -e "${RED}❌ Option inconnue: $1${NC}"
        echo "Utilisez --help pour voir les options disponibles"
        exit 1
        ;;
esac

# Afficher les conseils à la fin
if [ "${1:-}" != "--help" ] && [ "${1:-}" != "-h" ]; then
    echo
    show_tips
    
    echo -e "${GREEN}✅ Analyse Checkstyle terminée!${NC}"
    echo -e "📊 Consultez les rapports détaillés dans ${BLUE}build/reports/checkstyle/${NC}"
fi