#!/bin/bash
# Script to generate Doxygen documentation and integrate with Docusaurus

echo "🔄 Generating Doxygen documentation..."

# Check if doxygen is installed
if ! command -v doxygen &> /dev/null; then
    echo "❌ Doxygen is not installed. Please install it first:"
    echo "   - Ubuntu/Debian: sudo apt-get install doxygen"
    echo "   - macOS: brew install doxygen"
    echo "   - Windows: Download from https://www.doxygen.nl/download.html"
    exit 1
fi

# Generate Doxygen documentation
doxygen Doxyfile

if [ $? -eq 0 ]; then
    echo "✅ Doxygen documentation generated successfully!"
    echo "📁 Output location: docusaurus/static/doxygen/html"
    echo ""
    echo "📝 Next steps:"
    echo "   1. Run Docusaurus: cd docusaurus && npm start"
    echo "   2. Access Doxygen docs at: http://localhost:3000/doxygen/html/index.html"
else
    echo "❌ Error generating Doxygen documentation"
    exit 1
fi
