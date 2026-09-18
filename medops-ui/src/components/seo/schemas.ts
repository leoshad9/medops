export const ORGANIZATION_SCHEMA = {
  '@context': 'https://schema.org',
  '@type': 'Organization',
  name: 'MedOps',
  url: 'https://medops.ai',
  logo: 'https://medops.ai/logo.png',
  sameAs: [
    'https://twitter.com/medops',
    'https://linkedin.com/company/medops',
  ],
  contactPoint: {
    '@type': 'ContactPoint',
    telephone: '+1-800-MED-OPS1',
    contactType: 'customer service',
    availableLanguage: 'English',
  },
};

export const WEBSITE_SCHEMA = {
  '@context': 'https://schema.org',
  '@type': 'WebSite',
  name: 'MedOps',
  url: 'https://medops.ai',
  potentialAction: {
    '@type': 'SearchAction',
    target: {
      '@type': 'EntryPoint',
      urlTemplate: 'https://medops.ai/search?q={search_term_string}',
    },
    'query-input': 'required name=search_term_string',
  },
};

export const SOFTWARE_APPLICATION_SCHEMA = {
  '@context': 'https://schema.org',
  '@type': 'SoftwareApplication',
  name: 'MedOps',
  applicationCategory: 'MedicalApplication',
  operatingSystem: 'Cloud',
  offers: {
    '@type': 'Offer',
    price: '0',
    priceCurrency: 'USD',
    availability: 'https://schema.org/InStock',
  },
  description: 'AI-powered medical operations platform for healthcare professionals.',
};