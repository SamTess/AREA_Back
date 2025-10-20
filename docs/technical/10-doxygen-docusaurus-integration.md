# Doxygen + Docusaurus Integration

This guide explains how Doxygen documentation is integrated with Docusaurus to provide comprehensive technical documentation.

## 🎯 Architecture

```
AREA Backend Documentation
├── Docusaurus (Guides & Tutorials)
│   ├── Introduction
│   ├── Configuration
│   ├── Service Providers
│   └── Worker System
│
└── Doxygen (API Reference)
    ├── Java Classes
    ├── Methods & Functions
    └── Source Code
```

## 🚀 Quick Start

### Generate Doxygen Documentation

```bash
# At the project root
./scripts/generate-doxygen.sh
```

### Start Docusaurus

```bash
cd docusaurus
npm install  # First time only
npm start
```

### Access Documentation

- **Docusaurus**: http://localhost:3000
- **Doxygen**: http://localhost:3000/doxygen/html/index.html
- **Swagger API**: http://localhost:8080/swagger-ui.html

## 📝 Configuration

### Doxyfile

The `Doxyfile` at the root configures Doxygen:

- **INPUT**: `src/main/java` (source code to document)
- **OUTPUT_DIRECTORY**: `docusaurus/static/doxygen` (output in Docusaurus)
- **RECURSIVE**: YES (traverses all subdirectories)
- **OPTIMIZE_OUTPUT_JAVA**: YES (optimized for Java)

### docusaurus.config.ts

The Docusaurus configuration includes:

- Link in navbar to `/doxygen/html/index.html`
- Link in footer
- Introduction page to API Reference

## 🔧 Customization

### Modify Doxygen Appearance

Edit the `Doxyfile`:

```doxyfile
HTML_COLORSTYLE_HUE    = 220  # Hue (0-360)
HTML_COLORSTYLE_SAT    = 100  # Saturation (0-255)
HTML_COLORSTYLE_GAMMA  = 80   # Gamma (40-240)
```

### Add Custom Style

1. Create a CSS file: `doxygen-custom.css`
2. In the `Doxyfile`:
   ```doxyfile
   HTML_EXTRA_STYLESHEET = doxygen-custom.css
   ```

### Filter Documented Files

In the `Doxyfile`:

```doxyfile
EXCLUDE_PATTERNS = */test/* */legacy/*
FILE_PATTERNS = *.java *.md
```

## 🔄 Development Workflow

### 1. Document the Code

Use Javadoc comments:

```java
/**
 * Service for managing AREA actions.
 * 
 * @author Your Name
 * @version 1.0
 * @since 2025-10-20
 */
public class ActionService {
    
    /**
     * Creates a new action.
     * 
     * @param name The name of the action
     * @param type The action type
     * @return The created action
     * @throws IllegalArgumentException if the name is empty
     */
    public Action createAction(String name, ActionType type) {
        // Implementation
    }
}
```

### 2. Generate Documentation

```bash
./scripts/generate-doxygen.sh
```

### 3. Verify Locally

```bash
cd docusaurus && npm start
```

### 4. Commit (Optional)

```bash
git add Doxyfile docusaurus/docs/api-reference.md
git commit -m "docs: update API reference"
# Note: docusaurus/static/doxygen/ is in .gitignore
```

## 🐳 Docker Integration

### Dockerfile with Doxygen

```dockerfile
FROM node:18-alpine

# Install Doxygen
RUN apk add --no-cache doxygen

# Copy project
WORKDIR /app
COPY . .

# Generate Doxygen docs
RUN doxygen Doxyfile

# Build Docusaurus
WORKDIR /app/docusaurus
RUN npm install && npm run build

EXPOSE 3000
CMD ["npm", "run", "serve"]
```

### Docker Compose

```yaml
services:
  docs:
    build: .
    ports:
      - "3000:3000"
    volumes:
      - ./docusaurus/docs:/app/docusaurus/docs:ro
    command: npm start
```

## 🚀 CI/CD Integration

### GitHub Actions

```yaml
name: Generate Docs

on:
  push:
    branches: [main, documentation-update]

jobs:
  docs:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Install Doxygen
        run: sudo apt-get install -y doxygen
      
      - name: Generate Doxygen
        run: doxygen Doxyfile
      
      - name: Setup Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '18'
      
      - name: Build Docusaurus
        run: |
          cd docusaurus
          npm ci
          npm run build
      
      - name: Deploy to GitHub Pages
        uses: peaceiris/actions-gh-pages@v3
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: ./docusaurus/build
```

## 📚 Resources

- [Doxygen Manual](https://www.doxygen.nl/manual/)
- [Docusaurus Documentation](https://docusaurus.io/)
- [Javadoc Guide](https://www.oracle.com/technical-resources/articles/java/javadoc-tool.html)

## 🆘 Troubleshooting

### Doxygen is not generating documentation

1. Check that Doxygen is installed: `doxygen --version`
2. Verify the INPUT path in Doxyfile
3. Make sure Java files have Javadoc comments

### Doxygen links don't work in Docusaurus

1. Check that OUTPUT_DIRECTORY points to `docusaurus/static/doxygen`
2. Restart Docusaurus after generation
3. Verify paths in docusaurus.config.ts

### Doxygen style doesn't display correctly

1. Clear browser cache
2. Check GENERATE_TREEVIEW = YES in Doxyfile
3. Regenerate with `./scripts/generate-doxygen.sh`
