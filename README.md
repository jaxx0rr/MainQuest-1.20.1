# JX MainQuest Mod - Trigger & Stage System Documentation

This guide provides detailed documentation and examples for using the `jxmainquest` mod's stage/trigger system.
Each quest stage has a `trigger` object, which determines when the player progresses to the next stage.

---

## Structure Overview

A single stage JSON object looks like this:

```json
{
  "text": "Quest description shown in the HUD",
  "trigger": {
    "type": "...", // Type of trigger
    ... // Additional fields depending on type
  }
}
```

---

## Trigger Types

### 1. `location`

Advances when the player is within a given radius of a location.

```json
{
  "type": "location",
  "x": 100,
  "y": 64,
  "z": -50,
  "radius": 5
}
```

### 2. `item`

Advances when the player has specific items in their inventory.
Supports extra required and/or alternate items:

```json
{
  "type": "item",
  "item": "minecraft:stick:2",
  "anditem1": "minecraft:feather:3",
  "oritem1": "minecraft:glowstone_dust",
  "oritem2": "minecraft:gold_nugget:5"
}
```

* `item` = required base item (with optional amount)
* `anditemX` = additional required items (all must be present)
* `oritemX` = at least one must be present to pass

### 3. `locationitem`

A combination of location and item — player must be at the specified location **and** have required items.
Supports the same fields as `item`:

```json
{
  "type": "locationitem",
  "x": 100,
  "y": 64,
  "z": -50,
  "radius": 4,
  "item": "hexerei:willow_log:2",
  "anditem1": "minecraft:feather:3"
}
```

### 4. `interaction`

Triggered when the player right-clicks a specific named NPC.
Can include dialogue.

```json
{
  "type": "interaction",
  "npc_name": "Hagrid",
  "x": 123,
  "y": 65,
  "z": -60,
  "dir": 180,
  "profession": "nitwit",
  "dialogue": [
    { "npc": "Welcome to Hogwarts!" },
    {
      "player_choices": ["Who are you?", "Where am I?"],
      "npc_responses": ["I'm Hagrid.", "You're in Hogwarts."]
    }
  ]
}
```

NPC will automatically spawn when the player is near the coordinates.
Progresses after the dialogue completes.

### 5. `enemy`

Triggered when a specific entity is killed.
Supports optional name match and location radius.
Automatically spawns the enemy if needed.

```json
{
  "type": "enemy",
  "enemy": "minecraft:zombie",
  "enemy_name": "Troll",
  "x": 100,
  "y": 40,
  "z": -60,
  "enemy_radius": 10,
  "reward_item": "minecraft:emerald",
  "reward_amount": 2,
  "reward_xp": 10,
  "spawn_enemy": true
}
```

Rewards are dropped directly from the enemy entity.
Progression happens even if the enemy dies from fire, fall damage, etc.

### 6. `waypoint`

Identical to `location` but silent:

* Does not show messages like "Quest Complete" or "New Quest"
* Does not appear in `/jxmq list`
* Still advances the stage and plays a subtle sound

```json
{
  "type": "waypoint",
  "x": 200,
  "y": 64,
  "z": -20,
  "radius": 3
}
```

---

## Quest Flow

* All triggers are processed on tick
* Some (like `interaction`) require player action
* Stage advancement resets related state (e.g. NPCs respawn, enemy kills cleared)

---

## Commands

* `/jxmq reload` — Reload stages from disk
* `/jxmq stage` — Show current stage
* `/jxmq stage set <n>` — Jump to stage (ignores waypoints)
* `/jxmq debug` — Show interaction + spawn tracker state for your player

---

## Rewards

Only `enemy` triggers drop item/xp rewards to the world.
All other trigger types give items/xp directly to the player.

```json
"reward_item": "minecraft:emerald:2",
"reward_xp": 10
```

(Only supported in `enemy` triggers.)

---

## Notes

* Use `:amount` on any `item`, `anditemX`, `oritemX`
* You can define up to:

  * 9 `anditemX`
  * 9 `oritemX`
* Add `spawn_enemy: false` to prevent autospawning an enemy

---

