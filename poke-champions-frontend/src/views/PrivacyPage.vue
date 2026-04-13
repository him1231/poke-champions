<script setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const siteName = 'Poké Champions'
const siteUrl = 'https://victorpoke-champions.com'
const contactEmail = 'lujiang.scout@gmail.com'
const lastUpdated = '2026 年 4 月 12 日'
</script>

<template>
  <div class="privacy-page container">
    <section class="hero-section">
      <div class="hero-badge">
        <span class="material-symbols-rounded">shield</span>
      </div>
      <h1>{{ t('privacy.title') }}</h1>
      <p class="hero-subtitle">{{ t('privacy.lastUpdated', { date: lastUpdated }) }}</p>
    </section>

    <section class="policy-card">
      <article class="policy-section">
        <h2><span class="material-symbols-rounded">info</span>{{ t('privacy.overview') }}</h2>
        <p>{{ t('privacy.overviewText', { siteName, siteUrl }) }}</p>
      </article>

      <article class="policy-section">
        <h2><span class="material-symbols-rounded">storage</span>{{ t('privacy.dataCollection') }}</h2>
        <p>{{ t('privacy.dataCollectionText1') }}</p>
        <p>{{ t('privacy.dataCollectionText2') }}</p>
        <ul>
          <li>{{ t('privacy.dataItems.browser') }}</li>
          <li>{{ t('privacy.dataItems.device') }}</li>
          <li>{{ t('privacy.dataItems.pages') }}</li>
          <li>{{ t('privacy.dataItems.ip') }}</li>
        </ul>
      </article>

      <article class="policy-section">
        <h2><span class="material-symbols-rounded">cookie</span>{{ t('privacy.cookies') }}</h2>
        <p>{{ t('privacy.cookiesText1') }}</p>
        <p>{{ t('privacy.cookiesText2') }}</p>
      </article>

      <article class="policy-section">
        <h2><span class="material-symbols-rounded">ads_click</span>{{ t('privacy.ads') }}</h2>
        <p>{{ t('privacy.adsText1') }}</p>
        <p v-html="t('privacy.adsText2')"></p>
      </article>

      <article class="policy-section">
        <h2><span class="material-symbols-rounded">link</span>{{ t('privacy.externalLinks') }}</h2>
        <p>{{ t('privacy.externalLinksText') }}</p>
      </article>

      <article class="policy-section">
        <h2><span class="material-symbols-rounded">child_care</span>{{ t('privacy.children') }}</h2>
        <p>{{ t('privacy.childrenText') }}</p>
      </article>

      <article class="policy-section">
        <h2><span class="material-symbols-rounded">edit_note</span>{{ t('privacy.changes') }}</h2>
        <p>{{ t('privacy.changesText') }}</p>
      </article>

      <article class="policy-section">
        <h2><span class="material-symbols-rounded">mail</span>{{ t('privacy.contact') }}</h2>
        <p>{{ t('privacy.contactText', { email: contactEmail }) }} <a :href="'mailto:' + contactEmail">{{ contactEmail }}</a></p>
      </article>
    </section>
  </div>
</template>

<style scoped>
.privacy-page {
  max-width: 800px;
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.hero-section {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.hero-badge {
  width: 64px;
  height: 64px;
  border-radius: 20px;
  background: var(--accent-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}

.hero-badge .material-symbols-rounded {
  font-size: 32px;
  color: var(--accent);
}

.hero-section h1 {
  font-size: 2rem;
  font-weight: 800;
  letter-spacing: -0.03em;
}

.hero-subtitle {
  color: var(--text-muted);
  font-size: 0.9rem;
}

.policy-card {
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 36px;
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.policy-section h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 1.1rem;
  font-weight: 700;
  margin-bottom: 10px;
}

.policy-section h2 .material-symbols-rounded {
  font-size: 22px;
  color: var(--accent);
}

.policy-section p {
  color: var(--text-secondary);
  font-size: 0.9rem;
  line-height: 1.7;
  margin-bottom: 8px;
}

.policy-section p:last-child {
  margin-bottom: 0;
}

.policy-section ul {
  margin: 8px 0 0 20px;
  color: var(--text-secondary);
  font-size: 0.9rem;
  line-height: 1.8;
}

.policy-section a {
  color: var(--accent);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.policy-section a:hover {
  color: var(--accent-hover);
}

@media (max-width: 768px) {
  .policy-card {
    padding: 24px;
  }

  .hero-section h1 {
    font-size: 1.6rem;
  }
}
</style>
