#!/bin/bash
# Complete workflow script for generating and viewing documentation

set -e  # Exit on error

echo "🚀 AREA Backend - Documentation Generation Workflow"
echo "===================================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Step 1: Check prerequisites
echo -e "${BLUE}Step 1: Checking prerequisites...${NC}"
if ! command -v doxygen &> /dev/null; then
    echo -e "${YELLOW}⚠️  Doxygen not found. Installing...${NC}"
    echo "Please install Doxygen:"
    echo "  - Ubuntu/Debian: sudo apt-get install doxygen"
    echo "  - macOS: brew install doxygen"
    exit 1
fi
echo -e "${GREEN}✅ Doxygen found: $(doxygen --version)${NC}"

if ! command -v node &> /dev/null; then
    echo -e "${YELLOW}⚠️  Node.js not found${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Node.js found: $(node --version)${NC}"
echo ""

# Step 2: Generate Doxygen documentation
echo -e "${BLUE}Step 2: Generating Doxygen documentation...${NC}"
if [ -f "Doxyfile" ]; then
    doxygen Doxyfile
    echo -e "${GREEN}✅ Doxygen documentation generated${NC}"
else
    echo -e "${YELLOW}⚠️  Doxyfile not found${NC}"
    exit 1
fi
echo ""

# Step 3: Check output
echo -e "${BLUE}Step 3: Verifying output...${NC}"
if [ -d "docusaurus/static/doxygen/html" ]; then
    echo -e "${GREEN}✅ Output directory exists${NC}"
    FILE_COUNT=$(find docusaurus/static/doxygen/html -type f | wc -l)
    echo -e "${GREEN}   Generated $FILE_COUNT files${NC}"
else
    echo -e "${YELLOW}⚠️  Output directory not found${NC}"
    exit 1
fi
echo ""

# Step 4: Setup Docusaurus (if needed)
echo -e "${BLUE}Step 4: Setting up Docusaurus...${NC}"
cd docusaurus
if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    npm install
fi
echo -e "${GREEN}✅ Docusaurus ready${NC}"
echo ""

# Step 5: Summary and next steps
echo -e "${GREEN}🎉 Documentation generation complete!${NC}"
echo ""
echo "📋 Summary:"
echo "  - Doxygen HTML: docusaurus/static/doxygen/html/"
echo "  - Entry point: docusaurus/static/doxygen/html/index.html"
echo ""
echo "🚀 Next steps:"
echo ""
echo "  1. Start Docusaurus:"
echo -e "     ${BLUE}cd docusaurus && npm start${NC}"
echo ""
echo "  2. View documentation:"
echo "     - Main docs: http://localhost:3000"
echo "     - Doxygen: http://localhost:3000/doxygen/html/index.html"
echo ""
echo "  3. Build for production:"
echo -e "     ${BLUE}cd docusaurus && npm run build${NC}"
echo ""
echo "💡 Tip: Run this script whenever you update your code documentation!"
