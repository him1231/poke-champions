<script setup>
import { ref, onMounted } from 'vue'
import { pokemonRosterApi } from '../api/pokemonRoster'
import { typeRosterApi } from '../api/typeRoster'
import { moveRosterApi } from '../api/moveRoster'

const stats = ref({ pokemon: 0, types: 0, moves: 0 })
const loading = ref(true)

onMounted(async () => {
  try {
    const [pokemonRes, typesRes, movesRes] = await Promise.all([
      pokemonRosterApi.getPokemonList(),
      typeRosterApi.getTypes(),
      moveRosterApi.getMoves(),
    ])
    stats.value = {
      pokemon: Array.isArray(pokemonRes.data) ? pokemonRes.data.length : 0,
      types: Array.isArray(typesRes.data) ? typesRes.data.length : 0,
      moves: Array.isArray(movesRes.data) ? movesRes.data.length : 0,
    }
  } catch (e) {
    console.error('Failed to load stats:', e)
  } finally {
    loading.value = false
  }
})

const features = [
  {
    icon: 'auto_awesome',
    title: '寶可夢圖鑑',
    desc: '瀏覽冠軍賽可用寶可夢，查看屬性、可學招式與能力值模擬',
    link: '/pokemon',
    gradient: 'linear-gradient(135deg, rgba(230, 83, 79, 0.15), rgba(255, 150, 65, 0.1))',
    iconColor: '#ff9741',
  },
  {
    icon: 'bolt',
    title: '招式查詢',
    desc: '搜尋招式資料，依屬性或分類篩選，查看哪些寶可夢可以學習',
    link: '/moves',
    gradient: 'linear-gradient(135deg, rgba(74, 144, 217, 0.15), rgba(116, 206, 192, 0.1))',
    iconColor: '#4a90d9',
  },
  {
    icon: 'shield',
    title: '屬性相剋',
    desc: '查詢屬性間的攻防倍率，計算雙屬性組合的防禦相性',
    link: '/types',
    gradient: 'linear-gradient(135deg, rgba(171, 106, 200, 0.15), rgba(250, 113, 121, 0.1))',
    iconColor: '#ab6ac8',
  },
  {
    icon: 'groups',
    title: '隊伍組建',
    desc: '自由選擇寶可夢與招式，打造你的冠軍賽最強隊伍',
    link: '/team-builder',
    gradient: 'linear-gradient(135deg, rgba(76, 175, 80, 0.15), rgba(129, 199, 132, 0.1))',
    iconColor: '#4caf50',
  },
]
</script>

<template>
  <div class="home">
    <section class="hero">
      <div class="hero-bg">
        <div class="hero-orb hero-orb-1"></div>
        <div class="hero-orb hero-orb-2"></div>
        <div class="hero-orb hero-orb-3"></div>
      </div>
      <div class="container hero-content">
        <div class="hero-badge">寶可夢冠軍賽</div>
        <h1 class="hero-title">Poké Champions</h1>
        <p class="hero-desc">快速查詢寶可夢資料、屬性相剋與招式資訊<br/>為你的冠軍之路做好準備</p>

        <!-- <div v-if="!loading" class="stat-row">
          <div class="stat-card">
            <span class="material-symbols-rounded stat-icon" style="color: #ff9741">catching_pokemon</span>
            <span class="stat-num">{{ stats.pokemon }}</span>
            <span class="stat-label">寶可夢</span>
          </div>
          <div class="stat-card">
            <span class="material-symbols-rounded stat-icon" style="color: #ab6ac8">category</span>
            <span class="stat-num">{{ stats.types }}</span>
            <span class="stat-label">屬性</span>
          </div>
          <div class="stat-card">
            <span class="material-symbols-rounded stat-icon" style="color: #4a90d9">bolt</span>
            <span class="stat-num">{{ stats.moves }}</span>
            <span class="stat-label">招式</span>
          </div>
        </div> -->

        <router-link to="/pokemon" class="hero-cta">
          <span>開始探索</span>
          <span class="material-symbols-rounded">arrow_forward</span>
        </router-link>
      </div>
    </section>

    <section class="features container">
      <h2 class="section-title">功能總覽</h2>
      <div class="feature-grid">
        <router-link
          v-for="f in features"
          :key="f.link"
          :to="f.link"
          class="feature-card"
          :style="{ background: f.gradient }"
        >
          <div class="feature-icon-wrap">
            <span class="material-symbols-rounded feature-icon" :style="{ color: f.iconColor }">{{ f.icon }}</span>
          </div>
          <div class="feature-body">
            <h3>{{ f.title }}</h3>
            <p>{{ f.desc }}</p>
          </div>
          <span class="material-symbols-rounded feature-arrow">chevron_right</span>
        </router-link>
      </div>
    </section>
  </div>
</template>

<style scoped>
.hero {
  position: relative;
  text-align: center;
  padding: 80px 0 60px;
  overflow: hidden;
}

.hero-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.hero-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
}

.hero-orb-1 {
  width: 400px;
  height: 400px;
  background: rgba(230, 83, 79, 0.2);
  top: -100px;
  left: 50%;
  transform: translateX(-50%);
  animation: float 8s ease-in-out infinite;
}

.hero-orb-2 {
  width: 300px;
  height: 300px;
  background: rgba(74, 144, 217, 0.15);
  bottom: -80px;
  left: -50px;
  animation: float 10s ease-in-out infinite reverse;
}

.hero-orb-3 {
  width: 250px;
  height: 250px;
  background: rgba(171, 106, 200, 0.12);
  top: 20%;
  right: -60px;
  animation: float 12s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0) translateX(-50%); }
  50% { transform: translateY(-20px) translateX(-50%); }
}

.hero-content {
  position: relative;
  z-index: 1;
}

.hero-badge {
  display: inline-block;
  padding: 6px 18px;
  border-radius: 30px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 0.82rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  margin-bottom: 20px;
  border: 1px solid rgba(230, 83, 79, 0.2);
}

.hero-title {
  font-size: 3.5rem;
  font-weight: 900;
  letter-spacing: -0.04em;
  line-height: 1.1;
  background: linear-gradient(135deg, #fff 30%, var(--accent) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 16px;
}

.hero-desc {
  color: var(--text-secondary);
  font-size: 1.05rem;
  line-height: 1.7;
  max-width: 480px;
  margin: 0 auto 40px;
}

.stat-row {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-bottom: 36px;
  flex-wrap: wrap;
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 20px 32px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  backdrop-filter: var(--glass-blur);
  min-width: 120px;
  transition: transform 0.2s ease, border-color 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  border-color: var(--border-light);
}

.stat-icon {
  font-size: 28px;
  font-variation-settings: 'FILL' 1;
}

.stat-num {
  font-size: 2rem;
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1;
}

.stat-label {
  font-size: 0.78rem;
  color: var(--text-muted);
  font-weight: 500;
}

.hero-cta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 14px 32px;
  background: var(--accent);
  color: #fff;
  font-weight: 700;
  font-size: 0.95rem;
  border-radius: 14px;
  transition: all 0.25s ease;
  box-shadow: 0 4px 20px var(--accent-glow);
}

.hero-cta:hover {
  background: var(--accent-hover);
  transform: translateY(-2px);
  box-shadow: 0 8px 30px var(--accent-glow);
}

.hero-cta .material-symbols-rounded {
  font-size: 20px;
  transition: transform 0.2s;
}

.hero-cta:hover .material-symbols-rounded {
  transform: translateX(4px);
}

.section-title {
  font-size: 1.4rem;
  font-weight: 700;
  margin-bottom: 24px;
  color: var(--text-primary);
  letter-spacing: -0.02em;
}

.features {
  padding-bottom: 60px;
}

.feature-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feature-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px 28px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  transition: all 0.25s ease;
  backdrop-filter: var(--glass-blur);
}

.feature-card:hover {
  border-color: var(--border-light);
  transform: translateX(4px);
  box-shadow: var(--shadow);
}

.feature-icon-wrap {
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.feature-icon {
  font-size: 28px;
  font-variation-settings: 'FILL' 1;
}

.feature-body {
  flex: 1;
}

.feature-body h3 {
  font-size: 1.05rem;
  font-weight: 700;
  margin-bottom: 4px;
  color: var(--text-primary);
}

.feature-body p {
  color: var(--text-secondary);
  font-size: 0.88rem;
  line-height: 1.5;
}

.feature-arrow {
  color: var(--text-muted);
  font-size: 24px;
  transition: all 0.2s;
  flex-shrink: 0;
}

.feature-card:hover .feature-arrow {
  color: var(--text-primary);
  transform: translateX(4px);
}

@media (max-width: 768px) {
  .hero {
    padding: 48px 0 40px;
  }

  .hero-title {
    font-size: 2.4rem;
  }

  .hero-desc {
    font-size: 0.95rem;
  }

  .stat-row {
    gap: 12px;
  }

  .stat-card {
    padding: 16px 24px;
    min-width: 100px;
  }

  .stat-num {
    font-size: 1.5rem;
  }

  .feature-card {
    padding: 20px;
    gap: 16px;
  }
}
</style>
