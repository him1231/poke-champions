<script setup>
const creator = {
  name: 'Victor',
  role: '全端開發者',
  bio: '熱愛寶可夢與網頁開發，打造 Poké Champions 的初衷是讓每位訓練家都能輕鬆查閱冠軍賽資料、規劃最強隊伍。',
  skills: ['Vue.js', 'Spring Boot', 'PostgreSQL', 'GCP'],
  links: [
    { label: 'GitHub', url: 'https://github.com/', icon: 'code' },
    { label: 'Email', url: 'mailto:lujiang.scout@gmail.com', icon: 'mail' },
  ],
}

const milestones = [
  { icon: 'lightbulb', title: '靈感來源', desc: '對寶可夢冠軍賽數據散落各處感到不便，決定自己動手整合。' },
  { icon: 'architecture', title: '技術選型', desc: '前端採 Vue 3 + Vite，後端 Spring Boot + PostgreSQL，部署於 GCP。' },
  { icon: 'database', title: '資料整合', desc: '自動爬取並同步官方冠軍賽名單、招式、屬性等資料。' },
  { icon: 'rocket_launch', title: '正式上線', desc: '持續優化 UI 與效能，為訓練家提供最佳查詢體驗。' },
]
</script>

<template>
  <div class="about-page container">
    <section class="hero-section">
      <div class="hero-badge">
        <span class="material-symbols-rounded">person</span>
      </div>
      <h1>關於製作者</h1>
      <p class="hero-subtitle">認識 Poké Champions 背後的開發者</p>
    </section>

    <section class="creator-card">
      <div class="creator-avatar">
        <span class="material-symbols-rounded">face</span>
      </div>
      <div class="creator-info">
        <h2>{{ creator.name }}</h2>
        <span class="creator-role">{{ creator.role }}</span>
        <p class="creator-bio">{{ creator.bio }}</p>
        <div class="skill-tags">
          <span v-for="skill in creator.skills" :key="skill" class="skill-tag">{{ skill }}</span>
        </div>
        <div class="creator-links">
          <a
            v-for="link in creator.links"
            :key="link.label"
            :href="link.url"
            target="_blank"
            rel="noopener noreferrer"
            class="creator-link"
          >
            <span class="material-symbols-rounded">{{ link.icon }}</span>
            {{ link.label }}
          </a>
        </div>
      </div>
    </section>

    <section class="milestones-section">
      <h2 class="section-title">
        <span class="material-symbols-rounded">timeline</span>
        開發歷程
      </h2>
      <div class="milestones-grid">
        <div v-for="(m, i) in milestones" :key="i" class="milestone-card">
          <div class="milestone-icon">
            <span class="material-symbols-rounded">{{ m.icon }}</span>
          </div>
          <h3>{{ m.title }}</h3>
          <p>{{ m.desc }}</p>
        </div>
      </div>
    </section>

    <section class="thanks-section">
      <div class="thanks-card">
        <span class="material-symbols-rounded thanks-icon">favorite</span>
        <h2>特別感謝</h2>
        <p>感謝所有寶可夢訓練家的支持與回饋，以及 PokeAPI、官方冠軍賽資料的公開。</p>
        <p class="disclaimer">本站為非官方粉絲作品，與 The Pokémon Company 無關。所有寶可夢相關名稱與圖像為其各自擁有者之商標與著作權。</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.about-page {
  max-width: 800px;
  display: flex;
  flex-direction: column;
  gap: 40px;
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
  font-size: 1rem;
}

.creator-card {
  display: flex;
  gap: 28px;
  padding: 32px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: 20px;
  align-items: flex-start;
}

.creator-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent), var(--accent-hover));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.creator-avatar .material-symbols-rounded {
  font-size: 40px;
  color: white;
}

.creator-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.creator-info h2 {
  font-size: 1.4rem;
  font-weight: 700;
}

.creator-role {
  font-size: 0.85rem;
  color: var(--accent);
  font-weight: 600;
  background: var(--accent-soft);
  padding: 4px 12px;
  border-radius: 8px;
  width: fit-content;
}

.creator-bio {
  color: var(--text-secondary);
  font-size: 0.92rem;
  line-height: 1.6;
  margin-top: 4px;
}

.skill-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.skill-tag {
  font-size: 0.78rem;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 8px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  color: var(--text-secondary);
}

.creator-links {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.creator-link {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--text-secondary);
  padding: 8px 16px;
  border-radius: 12px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  transition: all 0.2s ease;
}

.creator-link:hover {
  color: var(--accent);
  border-color: var(--accent);
  background: var(--accent-soft);
}

.creator-link .material-symbols-rounded {
  font-size: 18px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 1.2rem;
  font-weight: 700;
  margin-bottom: 20px;
}

.section-title .material-symbols-rounded {
  font-size: 24px;
  color: var(--accent);
}

.milestones-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.milestone-card {
  padding: 24px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: border-color 0.2s;
}

.milestone-card:hover {
  border-color: var(--accent);
}

.milestone-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--accent-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}

.milestone-icon .material-symbols-rounded {
  font-size: 22px;
  color: var(--accent);
}

.milestone-card h3 {
  font-size: 0.95rem;
  font-weight: 700;
}

.milestone-card p {
  font-size: 0.85rem;
  color: var(--text-secondary);
  line-height: 1.5;
}

.thanks-section {
  text-align: center;
}

.thanks-card {
  padding: 36px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.thanks-icon {
  font-size: 36px;
  color: var(--accent);
}

.thanks-card h2 {
  font-size: 1.2rem;
  font-weight: 700;
}

.thanks-card p {
  color: var(--text-secondary);
  font-size: 0.9rem;
  line-height: 1.6;
  max-width: 540px;
}

.disclaimer {
  font-size: 0.78rem !important;
  color: var(--text-muted) !important;
  margin-top: 8px;
}

@media (max-width: 768px) {
  .creator-card {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .creator-role {
    align-self: center;
  }

  .skill-tags {
    justify-content: center;
  }

  .creator-links {
    justify-content: center;
  }

  .milestones-grid {
    grid-template-columns: 1fr;
  }

  .hero-section h1 {
    font-size: 1.6rem;
  }
}
</style>
