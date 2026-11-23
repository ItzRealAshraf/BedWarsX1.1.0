# BedWarsX
Build: `mvn clean package`  (JDK 17+ ; output: target/BedWarsX.jar)  -> drop in /plugins (Spigot/Paper/Purpur, 1.16 -> latest)

## Quick setup
0. Open the setup assistant any time with `/bw setup <map>`; it teleports you to the map and shows clickable steps (green = done, red = missing).
1. Build a map in its own world (void/superflat recommended). From inside the map run `/bw create <name> <mode>`, or from the BedWars hub run `/bw create <name> <mode>` when the world folder has the same name. You can also specify it explicitly: `/bw create <name> <mode> <worldName>`. The command loads the world and teleports you there.
2. /bw setwaitingplayer (short: /bw setwai)   /bw setspec   (waiting spawn + spectator spawn inside the map; /bw setlobby still works as an old alias)
3. For each team: /bw addteam red ; /bw setspawn red ; /bw setbed red (look at the bed)
4. /bw addgen diamond | emerald  (stand on the spot)   /bw addshop item | upgrade (villagers)
5. /bw setvoid [y]  /bw setmin <n>  /bw info  /bw enable
6. Players: /bw join <arena>, /bw quickjoin
Each game runs on a temporary COPY of the world, deleted at the end (automatic map reset).

## Modes (each arena has ONE fixed mode, chosen when you create it)
/bw create <name> <mode>   mode = 1v1 | 4v4 | solo | doubles | 3v3v3v3 | 4v4v4v4
Teams are added automatically (1v1 / 4v4 = red + blue). Then only: setwaitingplayer (setwai), setspec, setspawn/setbed per team, addgen, addshop, enable.
Players: /bw quickjoin 1v1  |  /bw quickjoin 4v4

## v1.1 additions
- Party: /bw party invite <player> | accept | leave | kick <player> | disband | list  (only the leader joins; whole party lands in one team)
- Levels/XP: XP for kills, final kills, beds, playing, winning (config.yml `xp:`), level shown in chat, /bw stats
- Pop-up Tower (Utility shop, 24 iron): place the chest -> 3x3 tower with ladder
- Messages: config.yml `language: en|ar`, edit plugins/BedWarsX/messages_<lang>.yml
- PlaceholderAPI (soft-depend): %bwx_wins% %bwx_kills% %bwx_finals% %bwx_beds% %bwx_deaths% %bwx_games% %bwx_losses%
  %bwx_xp% %bwx_level% %bwx_arena% %bwx_mode% %bwx_state% %bwx_players% %bwx_maxplayers% %bwx_team%
- Auto-team: players are balanced into teams on join; those who picked a team themselves are never moved
- Spectator hotbar (after final death): compass = teleporter, ender eye = Play Again (rematch), bed = leave

## Dedicated BedWars lobby GUI

- Stand at the BedWars lobby spawn and run `/bw sethub` as an administrator (`bwx.admin`).
- Players joining this server are teleported to that location and receive three hotbar items: Map Selector, Party Menu, and Return to Main Hub.
- Map Selector lists enabled arenas that are waiting or starting. Clicking one attempts to join it; if the player is party leader, the existing party join flow is used.
- Party Menu has create/leave/disband, pending-invite acceptance, online-player invitations by head, a party roster (leader can click a member head to kick), refresh, and party quick-join.
- Configure `hub.return-command` in `plugins/BedWarsX/config.yml`. On a Velocity network, `server lobby` is the default proxy command; change `lobby` to your actual main hub server name if needed. The command is executed by the player, so the proxy must allow it.
- To disable automatic teleportation, set `hub.teleport-on-join: false`. The hotbar items are still granted when players join outside an arena.

The project source was updated, but a Maven build could not be run in the current environment because Maven is not installed. Compile and test it on your build machine before putting it on a live server.


### Spectator hotbar and BedWars lobby

After a player is permanently eliminated, their hotbar contains three items:
- Compass (`Spectator Teleporter`): opens the list of living players to spectate.
- Ender Eye (`Play Again`): queues a rematch in another available arena of the same mode.
- Red Bed (`BedWars Lobby`): leaves the current arena and teleports to the BedWars lobby spawn.

To configure that lobby, an administrator with `bwx.admin` stands at the desired spawn and runs `/bw sethub`. This saves `hub.spawn` in `plugins/BedWarsX/config.yml` and enables teleporting new joins to that spawn. The `hub.return-command` setting remains for the separate main network hub item.

## Setup helper, TAB and scoreboards

- `/bw create <name> <mode> [world]` opens a grouped clickable setup assistant with spawn, team bed, generator, shop, validation and enable actions. Existing setup commands remain unchanged.
- `/bw help` and `/bwx help` show grouped player/admin command sections.
- TAB header/footer support `%player%`, `%level%`, `%wins%`, `%kills%`, `%online%`, `%ingame%` and `%party%`.
- The BedWars hub has its own configurable sidebar (`hub-scoreboard.lines`); the match sidebar is shown during active matches.
- `/bw reload` and `/bwx reload` reload config/messages and refresh TAB, hub UI and leaderboards. Requires `bwx.admin`; running matches are not restarted.


## Three different "lobby" points (they are separate on purpose)
- `/bw sethub` = the **BedWars lobby** (global). New joins, the Map Selector and the spectator "BedWars Lobby" button use it.
- `/bw setwaitingplayer` / `/bw setwai` = the **waiting spawn inside one map** (players wait here before the match starts).
- **Main network hub** = a different server on your proxy. The "Return to Main Hub" item connects the player to `hub.main-server` (config.yml; works on Velocity and BungeeCord).

## Help pages
`/bw help` (or `/bwx help`) shows player + party commands. Extra pages: `/bw help player`, `/bw help party`, and for admins `/bw help setup`, `/bw help admin`.

## Build check
After `mvn clean package`, verify the plugin descriptor is inside the jar:
`unzip -l target/BedWarsX.jar | grep -E "plugin.yml|config.yml|messages_"`


## v1.2 - BedWars1058-style setup + new Map Selector

### Setup assistant (same list as BedWars1058, every line is clickable)
`/bw create <name> [type] [world]` then follow the list (green/struck-through = done, coloured squares = teams still missing - click a square to run it for that team):
- `/bw setWaitingSpawn` - waiting spawn inside the map
- `/bw waitingPos <1|2>` - corners of the waiting lobby; the region is removed when the game starts
- `/bw autoCreateTeams` - detects the beds in the loaded map and creates the teams (and their bed positions)
- `/bw createTeam <name> <color>` / `/bw removeTeam <name>`
- `/bw setSpawn <team>` / `/bw setBed <team>` (look at the bed)
- `/bw setShop <team>` / `/bw setUpgrade <team>` - item shop NPC and upgrade NPC of each team
- `/bw addGenerator <iron|gold|diamond|emerald> [team]` - iron/gold = the team generator (nearest team if none given; if not set the team spawn is used), diamond/emerald = map generators
- `/bw removeGenerator` - removes the generator you stand next to
- `/bw setType <solo|doubles|3v3v3v3|4v4v4v4|1v1|4v4>`
- `/bw save` - validates, enables the map and sends you to the BedWars lobby
Old commands (`setwai`, `addteam`, `addshop`, `addgen`, `enable` ...) still work. The spectator spawn is optional now (`/bw setspec`); without it spectators fly above the middle of the map.

### Map Selector (compass in the BedWars lobby)
Opens 6 game types: Solo, Doubles, 3v3v3v3, 4v4v4v4, 1v1, 4v4. Each opens a "Bed Wars <type>" menu:
- Red bed (slot 13) = Play: joins the fullest open map of that type (your whole party if you lead one)
- Sign (slot 15) = Map Selector: list of all maps of that type, click one to join
- Ender pearl (slot 36) = Random Map
- White block (slot 28) = Go Back, Barrier (slot 32) = Close


## v1.4 - separate config files, scoreboards, admin tp, shout
Files in plugins/BedWarsX/ (all reloaded with `/bw reload`; missing keys fall back to the built-in defaults):
- `config.yml`   general settings (language, party, xp, countdowns, hub, edit)
- `tab.yml`      TAB header/footer for the lobby and for matches
- `scoreboard.yml`  match sidebar (waiting / starting / playing / ending, BedWars1058 style) + lobby sidebar
- `generators.yml`  team generators (iron/gold/emerald), diamond/emerald tiers, forge multipliers, event timings
- `gui.yml`      names, icons, lore and slots of the Map Selector, mode menu, map list, Party menu, hotbar items and leaderboards
- `shop.yml`     the item shop (categories, items, prices, pickaxe/axe tiers) and the team upgrade prices
- `/bw tp <map|lobby>` (admin): watch a running game as spectator, or go to the map itself; `lobby` returns you
- `/shout <message>` (or `/bw shout`): talk to everyone in your match


## v1.5 - everything under /bw edit, shop.yml, separate lobby/game TAB, setup holograms

### Map setup = `/bw edit <arena> <command>`
Only `/bw create <name> [type] [world]` (new map), `/bw delete`, `/bw tp`, `/bw sethub`, `/bw forcestart|forcestop`, `/bw leaderboard` and `/bw reload` stay outside.
Every map-configuration command now lives inside `edit`:

    /bw edit <arena>                          teleport to the map + setup list (clickable) + setup holograms
    /bw edit <arena> setWaitingSpawn | waitingPos <1|2> | setSpec | setMin <n> | setVoid [y]
    /bw edit <arena> autoCreateTeams | createTeam <name> <color> | removeTeam <name> | setType <type>
    /bw edit <arena> setSpawn <team>          also detects the team's bed automatically
    /bw edit <arena> setBed <team>            only needed when the bed was not detected (look at the bed)
    /bw edit <arena> setShop <team> | setUpgrade <team>
    /bw edit <arena> addGenerator <iron|gold|diamond|emerald> [team] | removeGenerator
    /bw edit <arena> markers | info | disable | save

Old top-level commands (`/bw setspawn ...`) answer with a hint to use `/bw edit`.

### Bed auto-detect
`setSpawn <team>` searches `edit.bed-search-radius` blocks (config.yml, default 30) around you for a bed in the team's colour and saves it.
If none is found you get a message and set it yourself with `setBed <team>`.

### Setup holograms
While a map is being edited, floating labels mark the waiting spawn, waiting corners, spectator spawn and, for each team, its spawn, bed, shop, upgrade shop and generator, plus the diamond/emerald generators. They update after every command, come back with `/bw edit <arena>` or `/bw edit <arena> markers`, and disappear on `save`. They are never saved with the world.

### Leaderboard
`/bw leaderboard create <wins|kills|finals|beds>` always shows the top 10 (`leaderboard.size` in gui.yml). Free places show `---` / `0` instead of "No stats yet".

### TAB
`separate-lobby-and-games: true` (tab.yml): the lobby and every match have their own player list. Sections in tab.yml and scoreboard.yml are now `hub` and `game`.

### Config files
`shop.yml` (new), `tab.yml` (hub + game), `scoreboard.yml` (hub + game), `gui.yml` (menus, party menu, hotbar items, leaderboard), `generators.yml`. Hotbar items are recognised by an invisible id, so they can be renamed freely.
The `map-builder` setting and the "Built by" texts were removed.

## v1.7 - what is new (ASHRAF ELHENAWY / ItzRealAshraf)

1. **Disconnect = death.** A player who leaves a running game dies: his items are gone (the last attacker gets the resources + kill credit).
   Bed alive -> he can come back (`offline-rejoin-seconds`) and respawns from scratch (respawn countdown, fresh gear).
   Bed destroyed -> it is a final kill and he cannot come back. If the bed is destroyed while he is away, he is out at that moment.
2. **Victory.** The end-of-game scoreboard shows `VICTORY!` to the winning team, `Game Over` to the others and `Draw` on a draw
   (`%result%` in `scoreboard.yml`, texts `sb-victory` / `sb-gameover` / `sb-draw` in `messages_*.yml`). Old `scoreboard.yml` files are fixed automatically.
3. **No creative inside a game.** Nobody (op / owner / `bwx.admin`) can switch to creative while he is in an arena (waiting, playing or spectating).
4. **`/bw tp <map>`**: your items are stored and you only get a bed. Right click the bed (or `/bw tp lobby`) = back to the BedWars lobby with all your items.
   You fly in adventure mode and are invisible while watching. The items are also saved in `plugins/BedWarsX/tp-kits/` so a crash never loses them.
5. **One-chest menus.** Party Manager, Map Selector, mode menu, map list and the spectator teleporter are now 27 slots (one chest).
   Party Manager: members on top, invite heads in the middle (9 per page, the Refresh button turns the page), buttons at the bottom.
   Slots outside 0-26 in an old `gui.yml` are ignored automatically (default layout is used).
6. **`/bw gui [mode]`**: `/bw gui solo`, `doubles`, `3v3v3v3`, `4v4v4v4`, `1v1`, `4v4` opens the map list of that mode. `/bw gui` opens the mode selector.
7. **Lobby protection.** The BedWars lobby (`/bw sethub`) cannot be changed by anybody. An admin uses `/bw build [on|off]` to build there.
   Config: `lobby-protection.enabled` and `lobby-protection.radius` (0 = the whole lobby world).
8. **Ownership protection.** `plugin.yml` lists `ASHRAF ELHENAWY` and `ItzRealAshraf` as authors; the plugin checks this at start-up and every 5 minutes
   (hashed) and disables itself if the credits were removed. Credits are also in the console banner and `/bw about`. See `LICENSE`.

## Stats + Cosmetics (v1.8)
- `/bw stats [player]` or the player head in the lobby hotbar (slot 4) opens the Stats GUI.
- `/bw cosmetics` or the ender chest (slot 6) opens the Cosmetics chest: Kill / Final Kill / Victory / Death effects, Trails, Projectile effects, Bed designs, Shop NPC skins.
- Coins are earned in games (`config.yml -> coins`). `cosmetics.yml`: `free-all: true` unlocks everything, `price:` per item, `enabled: false` hides an item.
- Admin: `/bw coins <give|take|set> <player> <n>`, `/bw cosmetics give <player> <category.id|all>`.

## Achievements, void damage, coins on the scoreboard (v1.8)
- **Achievements** (`achievements.yml`): every achievement has a goal (wins, kills, final kills, beds, games, win streak, level, void kills, flawless win, kills/beds in ONE match...). When it is reached the player gets **XP** (raises the BedWars level) + **Coins**. Open the list with `/bw achievements` (or the book in the Stats menu). `check-on-join: true` unlocks what old stats already reached. Add your own by copying a block; `name-ar` / `desc-ar` are used when `language: ar`.
- **Void damage** (`config.yml -> void`): `damage-mode: true` = the void hurts (`damage` HP every `interval-ticks`) and you only die when your health runs out. `false` = old instant death.
- **Coins on the scoreboards**: use `%coins%` (and `%level%`) in every board of `scoreboard.yml` (waiting / starting / playing / ending / hub). When a board has more than 15 lines, empty spacer lines are dropped first.
- PlaceholderAPI: `%bwx_achievements%`, `%bwx_achievements_total%`.
- Existing servers: `scoreboard.yml`, `messages_*.yml` and `gui.yml` in `plugins/BedWarsX/` are NOT overwritten - delete them (or add the new lines by hand) to get the new text.

## End podium + generator hologram (v1.9)
- **End-of-match podium** (`config.yml -> end-podium`): when a match ends, the chat shows the top 3 players, and they are displayed as NPC-style figures with **their own skin** (head + gold / silver / bronze armor) and name + kills holograms at the spectator spot. Everybody is taken there to watch (`teleport-players: false` to keep players where they are). No commands, removed when the arena resets, no NMS / Citizens needed.
- **Diamond / Emerald generators**: a Diamond / Emerald block spins above every map generator, and the second hologram line shows the NEXT upgrade and the time left (`holo-upgrade` / `holo-max` in messages_*.yml). The times come from `events:` in `generators.yml`.
- The waiting / starting / playing / ending scoreboards are the original ones again (hub board has a Coins line).
- **Base protection** (`config.yml -> base-protection`): nobody can place blocks, TNT, water or lava inside the area around every team's spawn and team generator (`radius`, default 3). `allow-own-team: true` lets a team still build in its own base. The bed area is NOT protected, so bed defence by building still works.

## Changelog
### 1.1.0
- Version numbering reset to 1.1.0.
- The ender chest is emptied for every match and the player's real ender chest comes back after it (`clear-enderchest` in config.yml).
- Weekly missions (new `weekly.yml`, shown at the top of `/bw achievements`); progress and completion reset every Monday. Lifetime stats are never reset.
- Stats are auto-saved every few minutes (`stats-autosave-minutes`).
- Updating: replace only `BedWarsX.jar`. New files / config keys / message keys are added automatically and nothing is overwritten.
