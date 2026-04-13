-- 為 DEMO-SNG-01 / DEMO-DBL-01 補上寶可夢（team_snapshot + preview_pokemon_ids）
-- 使用與現有 roster 常見一致的 apiName / formId；PIN 仍為 1234

UPDATE shared_team SET
  team_snapshot = $snap$[
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
  ]$snap$::jsonb,
  preview_pokemon_ids = $prev$[
    { "apiName": "venusaur", "nationalDexNumber": 3, "formId": "0003-000", "mega": false },
    { "apiName": "charizard", "nationalDexNumber": 6, "formId": "0006-000", "mega": false },
    { "apiName": "garchomp", "nationalDexNumber": 445, "formId": "0445-000", "mega": false }
  ]$prev$::jsonb,
  updated_at = NOW()
WHERE rental_code = 'DEMO-SNG-01';

UPDATE shared_team SET
  team_snapshot = $snap$[
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
  ]$snap$::jsonb,
  preview_pokemon_ids = $prev$[
    { "apiName": "torkoal", "nationalDexNumber": 324, "formId": "0324-000", "mega": false },
    { "apiName": "charizard", "nationalDexNumber": 6, "formId": "0006-000", "mega": false },
    { "apiName": "archaludon", "nationalDexNumber": 1018, "formId": "1018-000", "mega": false },
    { "apiName": "hippowdon", "nationalDexNumber": 450, "formId": "0450-000", "mega": false }
  ]$prev$::jsonb,
  updated_at = NOW()
WHERE rental_code = 'DEMO-DBL-01';
