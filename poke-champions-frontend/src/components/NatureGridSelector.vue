<script setup>
import {
  NATURE_GRID,
  NATURE_GRID_HEADERS_DEC,
  NATURE_GRID_HEADERS_INC,
  NATURE_DEFS,
} from '../constants/pokemonNatures'

const props = defineProps({
  modelValue: {
    type: String,
    default: 'serious',
  },
})

const emit = defineEmits(['update:modelValue'])

function selectNature(id) {
  if (!id) return
  emit('update:modelValue', id)
}

function labelFor(id) {
  if (!id) return ''
  return NATURE_DEFS[id]?.labelZh ?? id
}
</script>

<template>
  <div class="nature-panel">
    <div class="nature-panel-header">
      <span class="material-symbols-rounded" style="font-size: 20px; color: var(--accent)">psychology</span>
      <span class="nature-panel-title">選擇性格</span>
    </div>
    <div class="nature-grid" role="grid" aria-label="性格網格">
      <div class="nature-corner" aria-hidden="true" />
      <div
        v-for="(h, ci) in NATURE_GRID_HEADERS_DEC"
        :key="'dec-' + ci"
        class="nature-col-head"
      >
        <span class="nature-head-inner dec">
          <span class="material-symbols-rounded head-arrow" style="font-size: 14px">south</span>
          {{ h }}
        </span>
      </div>

      <template v-for="(row, ri) in NATURE_GRID" :key="'row-' + ri">
        <div class="nature-row-head">
          <span class="nature-head-inner inc">
            <span class="material-symbols-rounded head-arrow" style="font-size: 14px">north</span>
            {{ NATURE_GRID_HEADERS_INC[ri] }}
          </span>
        </div>
        <button
          v-for="(cellId, ci) in row"
          :key="'cell-' + ri + '-' + ci"
          type="button"
          class="nature-cell"
          :class="{ 'nature-cell--empty': !cellId, 'nature-cell--active': cellId && cellId === modelValue }"
          :disabled="!cellId"
          :aria-pressed="cellId === modelValue"
          :aria-label="cellId ? labelFor(cellId) : ''"
          @click="selectNature(cellId)"
        >
          <span v-if="cellId" class="nature-cell-text">{{ labelFor(cellId) }}</span>
        </button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.nature-panel {
  padding: 18px 20px 20px;
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--border);
}

.nature-panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.nature-panel-title {
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--text-primary);
}

.nature-grid {
  display: grid;
  grid-template-columns: 72px repeat(5, minmax(0, 1fr));
  grid-auto-rows: minmax(42px, auto);
  gap: 6px;
  align-items: stretch;
}

.nature-corner {
  border-radius: 8px;
}

.nature-col-head,
.nature-row-head {
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.nature-head-inner {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 6px 4px;
  border-radius: 8px;
  font-size: 0.68rem;
  font-weight: 700;
  line-height: 1.2;
  width: 100%;
}

.head-arrow {
  opacity: 0.7;
}

.nature-head-inner.dec {
  color: #74cec0;
  background: rgba(74, 206, 192, 0.1);
  border: 1px solid rgba(74, 206, 192, 0.15);
}

.nature-head-inner.inc {
  color: #ff9741;
  background: rgba(255, 151, 65, 0.1);
  border: 1px solid rgba(255, 151, 65, 0.15);
}

.nature-cell {
  min-height: 42px;
  padding: 8px 4px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.04);
  transition: all 0.15s ease;
}

.nature-cell:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.08);
  border-color: var(--border-light);
  transform: translateY(-1px);
}

.nature-cell--empty {
  cursor: default;
  background: rgba(0, 0, 0, 0.15);
  border-color: transparent;
  opacity: 0.3;
}

.nature-cell--active:not(.nature-cell--empty) {
  color: #fff;
  border-color: var(--accent);
  background: var(--accent-soft);
  box-shadow: 0 0 0 2px var(--accent-glow), 0 4px 12px var(--accent-glow);
}

.nature-cell-text {
  display: block;
  line-height: 1.25;
}

@media (max-width: 520px) {
  .nature-grid {
    grid-template-columns: 60px repeat(5, minmax(0, 1fr));
    gap: 4px;
  }
  .nature-cell {
    font-size: 0.65rem;
    padding: 6px 2px;
    min-height: 38px;
  }
  .nature-head-inner {
    font-size: 0.6rem;
  }
}
</style>
