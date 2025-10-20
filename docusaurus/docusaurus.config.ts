import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const config: Config = {
  title: 'AREA Backend Documentation',
  tagline: 'Action REAction - Your Automation Hub',
  favicon: 'img/favicon.ico',

  // Future flags, see https://docusaurus.io/docs/api/docusaurus-config#future
  future: {
    v4: true, // Improve compatibility with the upcoming Docusaurus v4
  },

  // Set the production url of your site here
  url: 'https://area-backend-docs.example.com',
  // Set the /<baseUrl>/ pathname under which your site is served
  // For GitHub pages deployment, it is often '/<projectName>/'
  baseUrl: '/',

  // GitHub pages deployment config.
  // If you aren't using GitHub pages, you don't need these.
  organizationName: 'SamTess', // Usually your GitHub org/user name.
  projectName: 'AREA_Back', // Usually your repo name.

  onBrokenLinks: 'warn',
  onBrokenMarkdownLinks: 'warn',

  // Even if you don't use internationalization, you can use this field to set
  // useful metadata like html lang. For example, if your site is Chinese, you
  // may want to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          routeBasePath: 'docs',
          // Please change this to your repo.
          // Remove this to remove the "edit this page" links.
          editUrl:
            'https://github.com/SamTess/AREA_Back/tree/main/docusaurus/',
        },
        blog: false, // Disable blog
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    // Replace with your project's social card
    image: 'img/docusaurus-social-card.jpg',
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'AREA Backend',
      logo: {
        alt: 'AREA Logo',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'tutorialSidebar',
          position: 'left',
          label: 'Documentation',
        },
        {
          to: '/doxygen/html/index.html',
          label: 'API Reference (Doxygen)',
          position: 'left',
        },
        {
          href: 'https://github.com/SamTess/AREA_Back',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Documentation',
          items: [
            {
              label: 'Introduction',
              to: '/docs',
            },
            {
              label: 'Guides',
              to: '/docs/category/guides',
            },
            {
              label: 'Technical Docs',
              to: '/docs/category/technical-documentation',
            },
          ],
        },
        {
          title: 'Resources',
          items: [
            {
              label: 'REST API (Swagger)',
              href: 'http://localhost:8080/swagger-ui.html',
            },
            {
              label: 'API Reference (Doxygen)',
              to: '/doxygen/html/index.html',
            },
            {
              label: 'GitHub',
              href: 'https://github.com/SamTess/AREA_Back',
            },
          ],
        },
        {
          title: 'Integrations',
          items: [
            {
              label: 'Service Providers',
              to: '/docs/category/service-providers',
            },
            {
              label: 'Worker System',
              to: '/docs/category/worker-system',
            },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} AREA Project. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java', 'bash', 'json', 'yaml', 'sql', 'gradle'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
