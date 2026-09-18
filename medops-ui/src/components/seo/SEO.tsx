import { Helmet } from 'react-helmet-async';

interface SEOProps {
  title: string;
  description: string;
  canonical?: string;
  ogImage?: string;
  ogType?: 'website' | 'article';
  twitterCard?: 'summary' | 'summary_large_image';
  structuredData?: object;
  noIndex?: boolean;
  noFollow?: boolean;
}

const DEFAULT_OG_IMAGE = 'https://medops.ai/og-image.png';
const DEFAULT_TWITTER_CARD = 'summary_large_image';
const SITE_NAME = 'MedOps';
const BASE_URL = 'https://medops.ai';

export function SEO({
  title,
  description,
  canonical,
  ogImage = DEFAULT_OG_IMAGE,
  ogType = 'website',
  twitterCard = DEFAULT_TWITTER_CARD,
  structuredData,
  noIndex = false,
  noFollow = false,
}: SEOProps) {
  const fullTitle = `${title} | ${SITE_NAME}`;
  const fullCanonical = canonical ? `${BASE_URL}${canonical}` : BASE_URL;
  const robotsContent = `${noIndex ? 'noindex' : 'index'}, ${noFollow ? 'nofollow' : 'follow'}`;

  return (
    <Helmet>
      <title>{fullTitle}</title>
      <meta name="description" content={description} />
      <meta name="robots" content={robotsContent} />
      <link rel="canonical" href={fullCanonical} />

      <meta property="og:type" content={ogType} />
      <meta property="og:title" content={fullTitle} />
      <meta property="og:description" content={description} />
      <meta property="og:image" content={ogImage} />
      <meta property="og:url" content={fullCanonical} />
      <meta property="og:site_name" content={SITE_NAME} />

      <meta name="twitter:card" content={twitterCard} />
      <meta name="twitter:title" content={fullTitle} />
      <meta name="twitter:description" content={description} />
      <meta name="twitter:image" content={ogImage} />

      {structuredData && (
        <script type="application/ld+json">{JSON.stringify(structuredData)}</script>
      )}
    </Helmet>
  );
}

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