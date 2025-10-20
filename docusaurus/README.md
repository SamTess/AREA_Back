# AREA Backend Documentation# Website



This directory contains the complete documentation for the AREA Backend project, built with [Docusaurus](https://docusaurus.io/).This website is built using [Docusaurus](https://docusaurus.io/), a modern static website generator.



## 📚 Documentation Structure## Installation



The documentation is organized into the following sections:```bash

yarn

- **Introduction**: Overview and quick start guide```

- **Guides**: Step-by-step tutorials for common tasks

- **Technical Documentation**: Deep dives into system architecture and internals## Local Development

- **Service Providers**: Integration guides for external services (GitHub, Slack, Discord, Google)

- **Worker System**: Documentation for the asynchronous worker system```bash

yarn start

## 🚀 Quick Start```



### PrerequisitesThis command starts a local development server and opens up a browser window. Most changes are reflected live without having to restart the server.



- **Node.js 18+** (Node.js 20 recommended)## Build

- **npm** or **yarn**

- **Docker** (optional, for containerized deployment)```bash

yarn build

### Local Development```



1. **Install dependencies:**This command generates static content into the `build` directory and can be served using any static contents hosting service.

   ```bash

   npm install## Deployment

   ```

Using SSH:

2. **Start the development server:**

   ```bash```bash

   npm startUSE_SSH=true yarn deploy

   ``````



   This command starts a local development server and opens up a browser window. Most changes are reflected live without having to restart the server.Not using SSH:



3. **Access the documentation:**```bash

   Open [http://localhost:3000](http://localhost:3000) in your browser.GIT_USER=<Your GitHub username> yarn deploy

```

### Build for Production

If you are using GitHub pages for hosting, this command is a convenient way to build the website and push to the `gh-pages` branch.

Build the static site:

```bash
npm run build
```

This command generates static content into the `build` directory and can be served using any static contents hosting service.

### Preview Production Build

Test your production build locally:

```bash
npm run serve
```

This command serves the built website locally at [http://localhost:3000](http://localhost:3000).

## 🐳 Docker Deployment

### Development Mode

Run the documentation site in development mode with hot-reloading:

```bash
docker compose -f docker-compose.dev.yml up --build
```

Access at [http://localhost:3000](http://localhost:3000)

### Production Mode

Build and run the production-ready static site with Nginx:

```bash
docker compose up --build
```

Access at [http://localhost:8081](http://localhost:8081)

### Stop Containers

```bash
# For development
docker compose -f docker-compose.dev.yml down

# For production
docker compose down
```

## 📝 Adding Documentation

### Create a New Document

1. Create a new `.md` file in the appropriate directory:
   - `docs/guides/` - For step-by-step guides
   - `docs/technical/` - For technical documentation
   - `docs/providers/` - For service provider integrations
   - `docs/worker/` - For worker system documentation

2. Add frontmatter to your document:
   ```markdown
   ---
   sidebar_position: 1
   title: Your Document Title
   ---

   # Your Document Title

   Your content here...
   ```

3. The document will automatically appear in the sidebar based on the directory structure.

### Add Images

Place images in the `static/img/` directory and reference them in your markdown:

```markdown
![Alt text](/img/your-image.png)
```

### Add Code Blocks

Use fenced code blocks with syntax highlighting:

````markdown
```java
public class Example {
    public static void main(String[] args) {
        System.out.println("Hello, AREA!");
    }
}
```
````

Supported languages: Java, JavaScript, TypeScript, Bash, JSON, YAML, SQL, Gradle, and more.

## 🎨 Customization

### Configuration

Edit `docusaurus.config.ts` to customize:
- Site title and tagline
- Navigation bar
- Footer
- Theme colors
- And more...

### Styling

Custom CSS can be added to `src/css/custom.css`.

### Components

React components can be added to `src/components/` and used in your markdown files.

## 📖 Available Commands

| Command | Description |
|---------|-------------|
| `npm start` | Start development server |
| `npm run build` | Build for production |
| `npm run serve` | Serve production build locally |
| `npm run clear` | Clear Docusaurus cache |
| `npm run write-translations` | Extract translatable strings |
| `npm run write-heading-ids` | Generate heading IDs |

## 🔍 Search

Docusaurus comes with built-in search functionality. The search bar in the navigation allows users to quickly find documentation.

## 🌐 Deployment Options

### GitHub Pages

```bash
GIT_USER=<Your GitHub username> npm run deploy
```

### Netlify

1. Connect your repository to Netlify
2. Set build command: `npm run build`
3. Set publish directory: `build`

### Vercel

1. Import your repository to Vercel
2. Vercel will auto-detect Docusaurus
3. Deploy!

### Docker + Nginx (Recommended for Production)

Use the provided `Dockerfile` and `docker-compose.yml` for production deployment with Nginx.

## 🔧 Troubleshooting

### Clear Cache

If you encounter issues, try clearing the cache:

```bash
npm run clear
```

### Node Version

Ensure you're using Node.js 18 or higher:

```bash
node --version
```

### Port Already in Use

If port 3000 is already in use, you can specify a different port:

```bash
npm start -- --port 3001
```

## 📚 Resources

- [Docusaurus Documentation](https://docusaurus.io/)
- [Markdown Features](https://docusaurus.io/docs/markdown-features)
- [Docusaurus Configuration](https://docusaurus.io/docs/configuration)
- [Deployment Guide](https://docusaurus.io/docs/deployment)

## 🤝 Contributing

When contributing to the documentation:

1. Follow the existing document structure
2. Use clear, concise language
3. Include code examples where appropriate
4. Add images or diagrams to clarify complex concepts
5. Test your changes locally before submitting

## 📄 License

This documentation is part of the AREA Backend project and follows the same license.

## 💬 Support

For questions or issues related to the documentation:

1. Check existing documentation sections
2. Review the [Technical Documentation](/docs/category/technical-documentation)
3. Consult the [API Documentation](http://localhost:8080/swagger-ui.html) for the backend
4. Open an issue on the GitHub repository
