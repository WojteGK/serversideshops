# ServersideShopsMod (Fabric, server-side) — 1.21.7

Commands:
- `/shop` — open categories
- `/shop <category>` — open that category (buy mode)
- `/buy <item_id> [qty]`
- `/sell <item_id> [qty]`
- `/balance [player]`
- `/pay <player> <amount>`
- `/shop reload` — reloads `config/serversideshops/shop.json`

Notes:
- Currency uses the scoreboard objective `money`.
- Chest GUI uses a vanilla 9x6 container; no client mod needed.
- Click the paper buttons at the bottom to navigate and toggle BUY/SELL.
- Prices & items configurable in `run/config/serversideshops/shop.json` after first run.

## Build & Run

1. Install Java 21.
2. Edit `gradle.properties` if versions change (check Fabric develop page).
3. Build: `./gradlew build` (or `gradlew.bat build` on Windows)
4. The mod jar will be in `build/libs/` — drop it into your server's `mods/` folder along with Fabric API.
5. Ensure a scoreboard objective exists or let the mod create it automatically:
   ```
   /scoreboard objectives add money dummy Money
   ```
6. Start server and test `/shop`.

> This is a minimal starter; polish (permissions, lore, sell detection by NBT, cooldowns) can be added next.
