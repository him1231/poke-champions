<script setup>
import { ref, computed, onMounted } from 'vue'
import { typeRosterApi } from '../api/typeRoster'

const types = ref([])
const loading = ref(true)
const error = ref(null)
const selectedType1 = ref('')
const selectedType2 = ref('')
const matchup = ref(null)
const matchupLoading = ref(false)

onMounted(async () => {
  try {
    const res = await typeRosterApi.getTypes()
    types.value = Array.isArray(res.data) ? res.data : []
  } catch (e) {
    error.value = '無法載入屬性資料'
  } finally {
    loading.value = false
  }
})

async function calculateMatchup() {
  if (!selectedType1.value) return
  matchupLoading.value = true
  try {
    const res = await typeRosterApi.getTypeMatchup(
      selectedType1.value,
      selectedType2.value || undefined,
    )
    matchup.value = res.data
  } catch {
    matchup.value = null
  } finally {
    matchupLoading.value = false
  }
}

function selectType(typeName) {
  if (!selectedType1.value) {
    selectedType1.value = typeName
  } else if (!selectedType2.value && typeName !== selectedType1.value) {
    selectedType2.value = typeName
  } else {
    selectedType1.value = typeName
    selectedType2.value = ''
    matchup.value = null
  }
}

function clearSelection() {
  selectedType1.value = ''
  selectedType2.value = ''
  matchup.value = null
}

function getChineseName(typeName) {
  const t = types.value.find((x) => x.name === typeName)
  return t ? t.chineseName : typeName
}

const matchupSections = computed(() => {
  if (!matchup.value) return []
  const sections = []
  const m = matchup.value

  if (m.immunities?.length) sections.push({ title: '0× 無效', items: m.immunities, cls: 'immune' })
  if (m.quadResistances?.length)
    sections.push({ title: '¼× 抵抗', items: m.quadResistances, cls: 'great' })
  if (m.resistances?.length) sections.push({ title: '½× 抵抗', items: m.resistances, cls: 'good' })
  if (m.neutral?.length) sections.push({ title: '1× 普通', items: m.neutral, cls: 'neutral-cls' })
  if (m.weaknesses?.length) sections.push({ title: '2× 弱點', items: m.weaknesses, cls: 'warn' })
  if (m.quadWeaknesses?.length)
    sections.push({ title: '4× 弱點', items: m.quadWeaknesses, cls: 'danger' })

  return sections
})
</script>

<template>
  <div class="container">
    <h1 class="page-title">屬性相剋查詢</h1>

    <div v-if="loading" class="loading">載入中</div>
    <div v-else-if="error" class="error-msg">{{ error }}</div>

    <template v-else>
      <p class="hint">
        <span class="material-symbols-rounded" style="font-size: 18px">touch_app</span>
        點選屬性查詢防禦相性（可選擇 1~2 個屬性）
      </p>

      <div class="type-grid">
        <button
          v-for="t in types"
          :key="t.name"
          type="button"
          :class="[
            'type-btn',
            t.name,
            {
              selected: selectedType1 === t.name || selectedType2 === t.name,
            },
          ]"
          @click="selectType(t.name)"
        >
          <span class="type-btn-name">{{ t.chineseName }}</span>
          <span class="type-btn-en">{{ t.name }}</span>
        </button>
      </div>

      <div v-if="selectedType1" class="selection-bar">
        <div class="selected-types">
          <span :class="`type-badge ${selectedType1}`">{{ getChineseName(selectedType1) }}</span>
          <span v-if="selectedType2" class="type-sep">
            <span class="material-symbols-rounded">add</span>
          </span>
          <span v-if="selectedType2" :class="`type-badge ${selectedType2}`">
            {{ getChineseName(selectedType2) }}
          </span>
        </div>
        <div class="selection-actions">
          <button type="button" class="btn-calc" @click="calculateMatchup">
            <span class="material-symbols-rounded" style="font-size: 18px">calculate</span>
            查詢防禦相性
          </button>
          <button type="button" class="btn-clear" @click="clearSelection">
            <span class="material-symbols-rounded" style="font-size: 18px">close</span>
            清除
          </button>
        </div>
      </div>

      <div v-if="matchupLoading" class="loading">計算中</div>

      <div v-if="matchup && !matchupLoading" class="matchup-result">
        <h2 class="result-title">
          <span class="material-symbols-rounded result-icon">shield</span>
          {{ getChineseName(selectedType1) }}
          <span v-if="selectedType2"> / {{ getChineseName(selectedType2) }}</span>
          的防禦相性
        </h2>

        <div v-if="matchupSections.length" class="matchup-sections">
          <div v-for="sec in matchupSections" :key="sec.title" class="matchup-group">
            <h3 :class="['matchup-title', sec.cls]">
              <span class="matchup-dot" :class="sec.cls"></span>
              {{ sec.title }}
            </h3>
            <div class="matchup-badges">
              <span v-for="item in sec.items" :key="item.type" :class="`type-badge ${item.type}`">
                {{ item.chineseName || item.type }}
              </span>
            </div>
          </div>
        </div>
        <pre v-else class="raw-json">{{ JSON.stringify(matchup, null, 2) }}</pre>
      </div>
    </template>
  </div>
</template>

<style scoped>
.hint {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  margin-bottom: 24px;
  font-size: 0.9rem;
}

.type-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 10px;
  margin-bottom: 28px;
}

.type-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 14px 8px;
  border-radius: 14px;
  font-weight: 700;
  font-size: 0.9rem;
  color: #fff;
  border: 2px solid transparent;
  transition: all 0.2s ease;
  opacity: 0.85;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
}

.type-btn:hover {
  opacity: 1;
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
}

.type-btn.selected {
  opacity: 1;
  border-color: rgba(255, 255, 255, 0.8);
  box-shadow:
    0 0 0 2px rgba(255, 255, 255, 0.15),
    0 8px 24px rgba(0, 0, 0, 0.3);
  transform: scale(1.05);
}

.type-btn-en {
  font-size: 0.6rem;
  font-weight: 500;
  opacity: 0.75;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.type-btn.normal { background: linear-gradient(135deg, var(--type-normal), #8a8d97); }
.type-btn.fire { background: linear-gradient(135deg, var(--type-fire), #e07a2a); }
.type-btn.water { background: linear-gradient(135deg, var(--type-water), #3a78c0); }
.type-btn.electric { background: linear-gradient(135deg, var(--type-electric), #e6bc00); color: #1a1a1a; text-shadow: none; }
.type-btn.grass { background: linear-gradient(135deg, var(--type-grass), #4da44a); }
.type-btn.ice { background: linear-gradient(135deg, var(--type-ice), #5db8a8); color: #1a1a1a; text-shadow: none; }
.type-btn.fighting { background: linear-gradient(135deg, var(--type-fighting), #b03058); }
.type-btn.poison { background: linear-gradient(135deg, var(--type-poison), #9050b0); }
.type-btn.ground { background: linear-gradient(135deg, var(--type-ground), #c06030); }
.type-btn.flying { background: linear-gradient(135deg, var(--type-flying), #7090c8); }
.type-btn.psychic { background: linear-gradient(135deg, var(--type-psychic), #e05868); }
.type-btn.bug { background: linear-gradient(135deg, var(--type-bug), #78a020); }
.type-btn.rock { background: linear-gradient(135deg, var(--type-rock), #a89870); }
.type-btn.ghost { background: linear-gradient(135deg, var(--type-ghost), #405090); }
.type-btn.dragon { background: linear-gradient(135deg, var(--type-dragon), #0958a8); }
.type-btn.dark { background: linear-gradient(135deg, var(--type-dark), #464050); }
.type-btn.steel { background: linear-gradient(135deg, var(--type-steel), #487888); color: #e8e8e8; text-shadow: none; }
.type-btn.fairy { background: linear-gradient(135deg, var(--type-fairy), #d070d0); }

.selection-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  padding: 18px 24px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  margin-bottom: 28px;
  backdrop-filter: var(--glass-blur);
}

.selected-types {
  display: flex;
  align-items: center;
  gap: 10px;
}

.type-sep {
  color: var(--text-muted);
}

.type-sep .material-symbols-rounded {
  font-size: 18px;
}

.selection-actions {
  display: flex;
  gap: 8px;
}

.btn-calc {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 22px;
  background: var(--accent);
  color: #fff;
  border-radius: 12px;
  font-weight: 600;
  font-size: 0.88rem;
  transition: all 0.2s;
  box-shadow: 0 4px 16px var(--accent-glow);
}

.btn-calc:hover {
  background: var(--accent-hover);
  transform: translateY(-1px);
}

.btn-clear {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 10px 18px;
  background: rgba(255, 255, 255, 0.04);
  color: var(--text-secondary);
  border-radius: 12px;
  font-size: 0.88rem;
  font-weight: 500;
  transition: all 0.2s;
  border: 1px solid var(--border);
}

.btn-clear:hover {
  color: var(--text-primary);
  border-color: var(--border-light);
}

.matchup-result {
  padding: 28px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  backdrop-filter: var(--glass-blur);
  animation: fadeIn 0.3s ease;
}

.result-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 1.15rem;
  font-weight: 700;
  margin-bottom: 28px;
}

.result-icon {
  font-size: 24px;
  color: var(--accent);
  font-variation-settings: 'FILL' 1;
}

.matchup-sections {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.matchup-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.matchup-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.82rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.matchup-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.matchup-dot.danger { background: #ff4444; }
.matchup-dot.warn { background: #ffaa33; }
.matchup-dot.good { background: #44bb66; }
.matchup-dot.great { background: #22ddaa; }
.matchup-dot.immune { background: #9999bb; }
.matchup-dot.neutral-cls { background: var(--text-muted); }

.matchup-title.danger { color: #ff4444; }
.matchup-title.warn { color: #ffaa33; }
.matchup-title.good { color: #44bb66; }
.matchup-title.great { color: #22ddaa; }
.matchup-title.immune { color: #9999bb; }
.matchup-title.neutral-cls { color: var(--text-muted); }

.matchup-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.raw-json {
  margin: 0;
  padding: 16px;
  border-radius: var(--radius-sm);
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid var(--border);
  font-size: 0.78rem;
  overflow-x: auto;
  color: var(--text-muted);
  font-family: 'SF Mono', ui-monospace, monospace;
}

@media (max-width: 768px) {
  .type-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
  }

  .type-btn {
    padding: 10px 6px;
    font-size: 0.82rem;
  }

  .selection-bar {
    flex-direction: column;
    align-items: stretch;
    padding: 16px 18px;
  }

  .selection-actions {
    justify-content: stretch;
  }

  .btn-calc, .btn-clear {
    flex: 1;
    justify-content: center;
  }

  .matchup-result {
    padding: 20px;
  }
}
</style>
