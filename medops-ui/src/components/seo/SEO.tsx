import { Helmet } from 'react-helmet-async';
import {
  ORGANIZATION_SCHEMA,
  WEBSITE_SCHEMA,
  SOFTWARE_APPLICATION_SCHEMA,
} from './schemas';

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

export {
  ORGANIZATION_SCHEMA,
  WEBSITE_SCHEMA,
  SOFTWARE_APPLICATION_SCHEMA,
};

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