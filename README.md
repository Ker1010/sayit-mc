# Say It! — learn a language while you play Minecraft

A Fabric mod for **Minecraft Java 26.3**. Every time you get an item you have not learned yet, a card asks you to
**say its name out loud in the language you are learning**, and a timer starts:

```
┌──────────────────────────────────────┐
│ [wheat]  SAY IT IN GERMAN        9s  │
│          Weizen                      │
│          ━━━━━━━━━━━━━━━━━━━━        │
│          listening...                │
└──────────────────────────────────────┘
```

## The game

- **Say it before the timer hits 0** → level-up sound, the item is learned and never asked again.
- **Timer hits 0** → the item is **cleared** from your inventory. Pick it up again to get another try.
- **Drop the item** (or put it in a chest) → its **timer resets**. When you pick it up again you get a fresh timer.
- One word at a time; other new items wait their turn.
- **The server picks the language** — everyone on it learns the same one, and players cannot change it.
- **Every player plays for themselves**: their own microphone, their own timer, their own learned items.

Item names come from Minecraft's own translations, so every language the game has works and your game UI can stay
in English. Words not written in Latin letters get a **pronunciation subtitle**:

- Japanese `ダイヤモンドの剣` → `(daiyamondo no ken)`, `小麦` → `(komugi)`
- Chinese `小麦` → `(xiǎo mài)` · Korean `다이아몬드 검` → `(daiamondeu geom)`
- Russian `Алмазный меч` → `(Almaznyy mech)` · also Ukrainian, Greek, Arabic, Hebrew, Thai, Hindi, Georgian, ...

Speech is recognized **offline** on each player's own computer with [Vosk](https://alphacephei.com/vosk/). The ~50 MB
model for your language downloads automatically the first time (the timer waits for it). The microphone is only
open while the card says *listening...*.

## Install

Requirements: Minecraft Java **26.3**, Fabric Loader **0.19.3+**, [Fabric API](https://modrinth.com/mod/fabric-api)
**0.165.0+26.3**, Java 25, a microphone.

| Where | What happens |
| --- | --- |
| **Server** | Put the jar and Fabric API in the server's `mods` folder **and** in every player's `mods` folder. Each player with the mod plays the game; players without it can still join and are left alone. |
| **Singleplayer** | Put the jar and Fabric API in `.minecraft/mods`. Your game acts as the server, so the language comes from your `config/sayitmc-server.json`. |
| **Server without the mod** | Nothing happens; the language has to come from a server running the mod. |

## Commands

Server operators (permission level 2+):

| Command | What it does |
| --- | --- |
| `/sayitadmin` | Show the server's language and time per word |
| `/sayitadmin language <code>` | Language everyone learns, e.g. `ja_jp`, `zh_cn`, `ko_kr`, `de_de`, `fr_fr`, `es_es` (tab-completes). Everyone's current word is cancelled; progress is kept per language |
| `/sayitadmin time <seconds>` | Time per word (3–120) |

Players:

| Command | What it does |
| --- | --- |
| `/sayit` | Show the server's language and time |
| `/sayit on` / `off` | Join or leave the game |
| `/sayit test` | Practice the item in your hand in the server's language (never cleared) |
| `/sayit skip` | Skip a practice card (server words can't be skipped — drop the item instead) |
| `/sayit forget` | Reset your learned items for the server's language |

## Config

Server rules — `config/sayitmc-server.json` on the server (or your own game for singleplayer). Edit it and run
`/reload`, or use `/sayitadmin`:

| Key | Default | Meaning |
| --- | --- | --- |
| `language` | `de_de` | Minecraft language code every player learns |
| `secondsToAnswer` | `10` | Length of each timer |
| `clearItemsOnTimeout` | `true` | Take the item when time runs out; `false` for a friendly mode |
| `includeCreativePlayers` | `false` | Also play in creative mode |
| `quizItemsAlreadyInInventory` | `false` | Also ask for what players carry when they join |
| `waitForClientSeconds` | `60` | How long to wait for a player's speech model download |

Learned items are saved per player in the world folder: `data/sayitmc/learned.json`.

Player settings — `config/sayitmc.json` (there is no language setting here on purpose):

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Play the game |
| `matchThreshold` | `0.75` | How close the spoken word must be (0–1). Lower it if you keep getting rejected |
| `speechModelUrls` | `{}` | Extra / replacement Vosk models per language |

Speech models are stored in `.minecraft/sayitmc/models/`.

## Speech recognition languages

Built-in model downloads: English, German, French, Spanish, Italian, Portuguese, Dutch, Russian, Ukrainian, Polish,
Turkish, Japanese, Chinese (Mandarin), Korean, Vietnamese, Hindi, Catalan, Czech, Swedish, Esperanto, Persian.

If the server picks another language, players see the word and pronunciation as a flashcard without a speech check,
and their items are never cleared. Players can add any [Vosk model](https://alphacephei.com/vosk/models) in
`config/sayitmc.json`:

```json
"speechModelUrls": {
  "ar": "https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip"
}
```

## Notes

- If a player has no microphone or speech model, their cards become flashcards and their items are not cleared.
- **macOS:** the app that launches Minecraft needs microphone permission (System Settings → Privacy & Security →
  Microphone). The official launcher may not ask for it; launchers like Prism Launcher do.
- Speech recognition natives are included for Windows x64, macOS and Linux x64.

## Building

```bash
./gradlew build
```

The jar is written to `build/libs/`. GitHub Actions builds it on every push (see the *Actions* tab → *Build* →
artifact `sayit-mc`). Unit tests for the game rules, romanizer and answer matching: `./gradlew test`.
