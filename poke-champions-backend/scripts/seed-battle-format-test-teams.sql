-- 單打 / 雙打測試資料（公開列表可見；PIN 皆為 1234；含範例寶可夢）
-- 執行：psql 連線至專案 DB 後 \i 此檔，或：
--   PGPASSWORD='...' psql -h localhost -U postgres -d poke-champions-db -f scripts/seed-battle-format-test-teams.sql
-- 若資料已存在但尚無寶可夢：執行 scripts/update-demo-teams-pokemon.sql

ALTER TABLE shared_team
  ADD COLUMN IF NOT EXISTS battle_format varchar(16) NOT NULL DEFAULT 'singles';

INSERT INTO shared_team (
  rental_code,
  pin_hash,
  title,
  description,
  team_snapshot,
  preview_pokemon_ids,
  is_public,
  view_count,
  report_count,
  expired,
  battle_format,
  created_at,
  updated_at
) VALUES
(
  'DEMO-SNG-01',
  '$2a$10$KZmuBGMhuHfw9i7bdECPeeeQ1gNmgu2mSn31NhlwtrwV7EDVDzIOy',
  '[測試] 單打隊伍',
  'battle_format = singles（PIN: 1234）',
  $sng_snap$[
    {
      "pokemon": {
        "apiName": "venusaur",
        "nationalDexNumber": 3,
        "formId": "0003-000",
        "hp": 80, "attack": 82, "defense": 83,
        "specialAttack": 100, "specialDefense": 100, "speed": 80
      },
      "types": ["grass", "poison"],
      "ability": null,
      "statPoints": { "hp": 0, "attack": 0, "defense": 0, "specialAttack": 0, "specialDefense": 0, "speed": 0 },
      "nature": "modest",
      "moves": [null, null, null, null],
      "heldItem": null
    },
    {
      "pokemon": {
        "apiName": "charizard",
        "nationalDexNumber": 6,
        "formId": "0006-000",
        "hp": 78, "attack": 84, "defense": 78,
        "specialAttack": 109, "specialDefense": 85, "speed": 100
      },
      "types": ["fire", "flying"],
      "ability": null,
      "statPoints": { "hp": 0, "attack": 0, "defense": 0, "specialAttack": 0, "specialDefense": 0, "speed": 0 },
      "nature": "timid",
      "moves": [null, null, null, null],
      "heldItem": null
    },
    {
      "pokemon": {
        "apiName": "garchomp",
        "nationalDexNumber": 445,
        "formId": "0445-000",
        "hp": 108, "attack": 130, "defense": 95,
        "specialAttack": 80, "specialDefense": 85, "speed": 102
      },
      "types": ["dragon", "ground"],
      "ability": null,
      "statPoints": { "hp": 0, "attack": 0, "defense": 0, "specialAttack": 0, "specialDefense": 0, "speed": 0 },
      "nature": "jolly",
      "moves": [null, null, null, null],
      "heldItem": null
    }
  ]$sng_snap$::jsonb,
  $sng_prev$[
    { "apiName": "venusaur", "nationalDexNumber": 3, "formId": "0003-000", "mega": false },
    { "apiName": "charizard", "nationalDexNumber": 6, "formId": "0006-000", "mega": false },
    { "apiName": "garchomp", "nationalDexNumber": 445, "formId": "0445-000", "mega": false }
  ]$sng_prev$::jsonb,
  true,
  0,
  0,
  false,
  'singles',
  NOW(),
  NOW()
),
(
  'DEMO-DBL-01',
  '$2a$10$KZmuBGMhuHfw9i7bdECPeeeQ1gNmgu2mSn31NhlwtrwV7EDVDzIOy',
  '[測試] 雙打隊伍',
  'battle_format = doubles（PIN: 1234）',
  $dbl_snap$[
    {
      "pokemon": {
        "apiName": "torkoal",
        "nationalDexNumber": 324,
        "formId": "0324-000",
        "hp": 70, "attack": 85, "defense": 140,
        "specialAttack": 85, "specialDefense": 70, "speed": 20
      },
      "types": ["fire"],
      "ability": null,
      "statPoints": { "hp": 0, "attack": 0, "defense": 0, "specialAttack": 0, "specialDefense": 0, "speed": 0 },
      "nature": "quiet",
      "moves": [null, null, null, null],
      "heldItem": null
    },
    {
      "pokemon": {
        "apiName": "charizard",
        "nationalDexNumber": 6,
        "formId": "0006-000",
        "hp": 78, "attack": 84, "defense": 78,
        "specialAttack": 109, "specialDefense": 85, "speed": 100
      },
      "types": ["fire", "flying"],
      "ability": null,
      "statPoints": { "hp": 0, "attack": 0, "defense": 0, "specialAttack": 0, "specialDefense": 0, "speed": 0 },
      "nature": "timid",
      "moves": [null, null, null, null],
      "heldItem": null
    },
    {
      "pokemon": {
        "apiName": "archaludon",
        "nationalDexNumber": 1018,
        "formId": "1018-000",
        "hp": 90, "attack": 105, "defense": 130,
        "specialAttack": 125, "specialDefense": 65, "speed": 85
      },
      "types": ["steel", "dragon"],
      "ability": null,
      "statPoints": { "hp": 0, "attack": 0, "defense": 0, "specialAttack": 0, "specialDefense": 0, "speed": 0 },
      "nature": "modest",
      "moves": [null, null, null, null],
      "heldItem": null
    },
    {
      "pokemon": {
        "apiName": "hippowdon",
        "nationalDexNumber": 450,
        "formId": "0450-000",
        "hp": 108, "attack": 112, "defense": 118,
        "specialAttack": 68, "specialDefense": 72, "speed": 47
      },
      "types": ["ground"],
      "ability": null,
      "statPoints": { "hp": 0, "attack": 0, "defense": 0, "specialAttack": 0, "specialDefense": 0, "speed": 0 },
      "nature": "impish",
      "moves": [null, null, null, null],
      "heldItem": null
    }
  ]$dbl_snap$::jsonb,
  $dbl_prev$[
    { "apiName": "torkoal", "nationalDexNumber": 324, "formId": "0324-000", "mega": false },
    { "apiName": "charizard", "nationalDexNumber": 6, "formId": "0006-000", "mega": false },
    { "apiName": "archaludon", "nationalDexNumber": 1018, "formId": "1018-000", "mega": false },
    { "apiName": "hippowdon", "nationalDexNumber": 450, "formId": "0450-000", "mega": false }
  ]$dbl_prev$::jsonb,
  true,
  0,
  0,
  false,
  'doubles',
  NOW(),
  NOW()
)
ON CONFLICT (rental_code) DO NOTHING;
