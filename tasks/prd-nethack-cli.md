# PRD: Small NetHack CLI

## Introduction

A minimal roguelike game inspired by NetHack, built as a Clojure CLI tech demo. The game uses clojure-lanterna for terminal UI rendering, runs on JVM, and features a single dungeon with procedurally generated floors. The player descends through floors to retrieve an amulet and win.

## Goals

- Demonstrate Clojure's suitability for building interactive terminal games
- Deliver a playable single-dungeon roguelike with movement, combat, and a win condition
- Use clojure-lanterna for real-time terminal UI with ASCII rendering
- Keep scope minimal — one focused, polished experience

## User Stories

### US-001: Project Setup
**Description:** As a developer, I want a working Clojure project with lanterna dependency so that I can start building the game.

**Acceptance Criteria:**
- [ ] `deps.edn` with clojure-lanterna dependency
- [ ] Main entry point namespace (`nethack-cli.core`)
- [ ] `clj -M -m nethack-cli.core` launches a lanterna terminal screen
- [ ] Screen displays a welcome message and waits for keypress
- [ ] Clean exit on quit key (q)

### US-002: Dungeon Generation
**Description:** As a player, I want procedurally generated dungeon floors so that each playthrough feels different.

**Acceptance Criteria:**
- [ ] Generate a single floor as a 2D grid (min 40x20)
- [ ] Floor contains walls (`#`), floor tiles (`.`), and one staircase down (`>`)
- [ ] At least one connected path from any floor tile to the staircase
- [ ] Rooms connected by corridors (simple room+corridor algorithm)
- [ ] Floor rendered correctly in lanterna terminal

### US-003: Player Movement
**Description:** As a player, I want to move my character around the dungeon using keyboard input.

**Acceptance Criteria:**
- [ ] Player represented by `@` symbol
- [ ] Move with arrow keys or vi keys (hjkl)
- [ ] Cannot walk through walls
- [ ] Screen redraws after each move
- [ ] Player starts in a random room on the floor

### US-004: Multiple Floors and Stairs
**Description:** As a player, I want to descend through multiple dungeon floors to reach the bottom.

**Acceptance Criteria:**
- [ ] 5 floors total, generated on first visit
- [ ] Walking onto `>` and pressing `>` descends to next floor
- [ ] Walking onto `<` and pressing `<` ascends to previous floor
- [ ] Each floor remembers its state (explored tiles, monster positions)
- [ ] Bottom floor (floor 5) contains the Amulet of Yendor (`"`)

### US-005: Simple Combat
**Description:** As a player, I want to fight monsters by bumping into them.

**Acceptance Criteria:**
- [ ] 2-3 monster types (e.g., `r` rat, `s` snake, `g` goblin) with different HP/damage
- [ ] Bump-to-attack: moving into a monster attacks it
- [ ] Monsters attack back when adjacent (simple AI: move toward player if in range)
- [ ] Monsters have HP; they die and disappear when HP reaches 0
- [ ] Player has HP (starts at 20); game over when HP reaches 0
- [ ] 3-5 monsters per floor, placed in rooms

### US-006: HUD and Messages
**Description:** As a player, I want to see my stats and game messages so I know what's happening.

**Acceptance Criteria:**
- [ ] Top or bottom bar shows: floor number, HP, and player position
- [ ] Message line shows last action ("You hit the rat for 3 damage", "The snake bites you!")
- [ ] Game over screen with final floor reached
- [ ] Victory screen when picking up the Amulet

### US-007: Win Condition
**Description:** As a player, I want to pick up the Amulet of Yendor on the bottom floor to win the game.

**Acceptance Criteria:**
- [ ] Amulet (`"`) placed on floor 5
- [ ] Walking over it picks it up automatically
- [ ] Victory message displayed
- [ ] Game ends after keypress on victory screen
- [ ] Final score shown (floors explored, monsters killed)

## Functional Requirements

- FR-1: Game runs via `clj -M -m nethack-cli.core` on JVM with no additional setup
- FR-2: Terminal UI rendered using clojure-lanterna with real-time keyboard input
- FR-3: Dungeon floors procedurally generated using rooms-and-corridors algorithm
- FR-4: 5 dungeon floors, each generated on first visit and persisted in game state
- FR-5: Player moves with arrow keys or hjkl; movement blocked by walls
- FR-6: Bump-to-attack combat with simple damage calculation (attack - defense + random)
- FR-7: Monster AI: move toward player if within 5 tiles, otherwise idle
- FR-8: Player HP starts at 20; game over at 0 HP
- FR-9: Amulet of Yendor on floor 5; picking it up wins the game
- FR-10: HUD displays floor number, HP, and last message
- FR-11: Game state is a single immutable Clojure map, updated each turn via pure functions
- FR-12: Game loop: render -> input -> update -> repeat

## Non-Goals

- No inventory system or items (beyond the Amulet)
- No character classes or leveling
- No save/load functionality
- No shops or NPCs
- No ranged combat or magic
- No fog of war or line-of-sight
- No multiplayer
- No sound

## Technical Considerations

- **Language:** Clojure (JVM)
- **TUI Library:** clojure-lanterna (wrapper around Google's lanterna Java library)
- **Architecture:** Functional core — game state as immutable map, pure update functions, side effects only in render/input
- **State shape:** `{:player {:x :y :hp} :floors [{:tiles :monsters}] :current-floor :messages :game-over?}`
- **Random generation:** Use `clojure.core` random functions with seed support for reproducibility
- **Coordinate system:** `[x y]` where `[0 0]` is top-left

## Success Metrics

- Game launches and renders dungeon in under 2 seconds
- Player can complete a full playthrough (reach floor 5, get amulet) in 5-10 minutes
- All game state managed as immutable data — no mutable atoms except for the top-level game-loop state
- Code is idiomatic Clojure: pure functions, data-oriented, minimal side effects

## Open Questions

- Should monsters move every turn, or only when player is nearby? (Current spec: move when within 5 tiles)
- Should there be healing (e.g., heal 1 HP every 10 turns)? (Current spec: no healing)
- Should explored tiles be tracked for a fog-of-war lite effect? (Current spec: no, full visibility)
