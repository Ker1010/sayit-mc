# Say It! — learn a language while you play Minecraft

A client-side Fabric mod for **Minecraft Java 26.3**. Whenever you get an item you have not learned yet,
a card pops up asking you to **say its name out loud in the language you are learning**:

```
┌──────────────────────────────────────┐
│ [wheat]  SAY IT IN GERMAN        9s  │
│          Weizen                      │
│          ━━━━━━━━━━━━━━━━━━━━        │
│          listening...                │
└──────────────────────────────────────┘
```

- Item names come from **Minecraft's own translation files**, so every language the game supports works,
  and your game UI can stay in English.
- If the word is not written in Latin letters, a **pronunciation subtitle** shows how it sounds in
  English-style letters:
  - Japanese `ダイヤモンドの剣` → `(daiyamondo no ken)`, `小麦` → `(komugi)`
  - Chinese `小麦` → `(xiǎo mài)` · Korean `다이아몬드 검` → `(daiamondeu geom)`
  - Russian `Алмазный меч` → `(Almaznyy mech)` · Ukrainian, Greek, Arabic, Hebrew, Thai, Hindi, Georgian, ...
- Speech is recognized **offline** with [Vosk](https://alphacephei.com/vosk/). The ~50 MB model for your
  language downloads automatically the first time. The microphone is only open while a card says *listening...*.
- Say it right → level-up sound and the item is marked learned. Miss it → you get asked again the next time you
  pick it up (after a 2-minute cooldown).

## Requirements

- Minecraft Java **26.3**, Fabric Loader **0.20.1+**, [Fabric API](https://modrinth.com/mod/fabric-api) **0.165.0+26.3**
- Java 25 (bundled with the official launcher for 26.3)
- A microphone

## Commands

| Command | What it does |
| --- | --- |
| `/sayit` | Status: language, number of items learned |
| `/sayit language <code>` | Language to learn, e.g. `ja_jp`, `zh_cn`, `ko_kr`, `de_de`, `fr_fr`, `es_es` (tab-completes) |
| `/sayit time <seconds>` | Time to answer (3–120, default 10) |
| `/sayit test` | Practice the item in your hand right now |
| `/sayit skip` | Skip the current card |
| `/sayit on` / `off` | Turn quizzes on or off |
| `/sayit forget` | Reset learned items for the current language |

## Speech recognition languages

Built-in model downloads: English, German, French, Spanish, Italian, Portuguese, Dutch, Russian, Ukrainian, Polish,
Turkish, Japanese, Chinese (Mandarin), Korean, Vietnamese, Hindi, Catalan, Czech, Swedish, Esperanto, Persian.

Other languages still work as flashcards (word + pronunciation, no speech check). You can add any
[Vosk model](https://alphacephei.com/vosk/models) in `config/sayitmc.json`:

```json
"speechModelUrls": {
  "ar": "https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip"
}
```

## Config — `config/sayitmc.json`

| Key | Default | Meaning |
| --- | --- | --- |
| `language` | `de_de` | Minecraft language code to learn |
| `enabled` | `true` | Quiz on new items |
| `secondsToAnswer` | `10` | Countdown length |
| `matchThreshold` | `0.75` | How close the spoken word must be (0–1). Lower it if you keep getting rejected |
| `retryCooldownSeconds` | `120` | Wait before asking a missed item again |
| `quizItemsAlreadyInInventory` | `false` | Also quiz what you are carrying when joining a world |
| `speechModelUrls` | `{}` | Extra / replacement Vosk models per language |

Learned items are saved in `config/sayitmc/learned.json`, speech models in `.minecraft/sayitmc/models/`.

## Notes

- **macOS:** the app that launches Minecraft needs microphone permission (System Settings → Privacy & Security →
  Microphone). The official launcher may not ask for it; launchers like Prism Launcher do.
- Speech recognition natives are included for Windows x64, macOS and Linux x64.
- This mod is client-only; it works on any server.

## Building

```bash
./gradlew build
```

The mod jar is written to `build/libs/sayit-mc-<version>.jar`. Copy it into `.minecraft/mods` along with Fabric API.

Unit tests for the romanizer and answer matching: `./gradlew test`.
