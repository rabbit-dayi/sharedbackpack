# Optional client compatibility

## Installation and rollout

The root project remains a dedicated-server mod (`environment: server`). The `client` subproject is an independent client-only addon (`environment: client`) with no server implementation or SQLite dependency. Install matching 1.7.0 server/client releases for the warehouse dashboard; both Minecraft 1.16.5 and 1.20.1 are supported. The server remains usable without any client addon.

The server negotiates the dashboard only when a player advertises `sharedbackpack:dashboard_state_v1`. Clients without that channel retain the original commands and chest GUI. A new client connected to an older server that supports `state_v1` keeps the original title-row search enhancement. Servers without Shared Backpack and unrelated chests receive no dashboard. Unload remains the original 9x5 blank container.

## Independent interface

The dashboard has scrollable box navigation and box creation on the left, search and a 9x5 virtual catalog in the center, native player inventory below, and capacity, namespace filtering, upgrade, diagnostics, refresh and a deposit slot on the right. It fits a 320x240 scaled GUI. Existing boxes beyond the visible rows are reachable by scrolling the sidebar. Blank box names generate a unique name; switching boxes is validated against the current team on the server.

Catalog entries aggregate identical item IDs and structurally equal complete NBT. Counts are separate 64-bit values, not oversized `ItemStack` counts. Hover shows the exact quantity; inventory withdrawals respect the item's native maximum stack size. Durability, names, enchantments, maps, nested data, list order and NBT numeric types remain significant. Physical database rows remain capped at 64 for compatibility; the capacity readout counts those rows, not virtual catalog entries. Aggregation does not provide unlimited physical storage.

Search uses the client's actual localized item names, full pinyin, initials and registry IDs, with multiple whitespace-separated terms. Filtering, namespace selection, six sort orders and paging are local. Language/resource reloads rebuild localized search names. Command queries such as `/c shitou` initialize the new dashboard search. Page and sort preferences are stored in SQLite per player, storage owner, team/box storage type and box name. Search does not overwrite the remembered unfiltered page; clearing it restores that page, clamped to available results. Preferences survive server restart.

## Inventory ownership and transactions

The server uses a vanilla 9x6 container to synchronize real inventory slots. Slots 0–44 and 46–53 are empty, read-only dummy slots hidden by the client; slot 45 is the real deposit slot; slots 54–89 are the player's main inventory and hotbar. Virtual catalog cells never contain transferable inventory stacks. The client reuses the opened handler in its independent `HandledScreen` rather than closing/reopening it during screen replacement.

Left-click withdraws one item to the cursor. Right-click withdraws up to one native stack. Shift+left-click withdraws a stack directly into free inventory space. Server-owned catalog keys and revisions identify the complete item identity; client-supplied item IDs, NBT, owners or arbitrary quantities cannot choose a withdrawal. Removal rechecks live matching rows in one SQLite transaction before giving items to the player.

Shift+left-click on player inventory sends the native quick-move request without generic-container inventory prediction. The source remains visible until the server commits the entire deposit and synchronizes its removal. Clicking the deposit slot with a carried stack similarly clears the cursor only after a successful server transaction. A physical deposit-slot item drains after vanilla click processing at the end of the server tick, not during cursor ownership transitions. Full storage or SQL failure keeps the original items; failed physical deposits and carried items return to inventory on close, dropping only overflow under normal inventory-full semantics. Upgrades consume a diamond only after the database upgrade succeeds.

`addItem`, `addBoxItem` and the dashboard share the same all-or-nothing deposit path. Capacity failures cannot partially merge items and then return failure. SQLite rollback covers item rows and storage timestamps. The migration only adds the preference table and does not rewrite stored item NBT.

## Wire protocol

`dashboard_request_v1` and `dashboard_state_v1` are separate from the legacy search protocol. Requests carry version, active sync ID and a bounded action body. The server ignores malformed/trailing input, unknown versions/actions, stale handlers/revisions, dead/spectator players and requests outside the player's current team or existing boxes.

Catalog snapshots carry a sync ID and revision, storage metadata/preferences/box names in the first chunk, and at most 32 entries per chunk. Icons retain complete NBT with count one; aggregate quantities are transmitted independently. Only the final chunk commits the client's new catalog, so a partial refresh never replaces the current visible list. A server-side comparison once per second refreshes open viewers when rows, capacity or boxes change; unchanged data sends no new catalog. Explicit operations refresh immediately. Status uses the existing separately versioned diagnostic channels.

Diagnostics poll every two seconds while the dashboard is open, with the existing active-handler checks and one-second server rate limit. Replies contain aggregate health data without file paths or player names. Traffic counters include legacy and dashboard addon payloads, not vanilla slot/item packets, other mods, compression or TCP/IP overhead. RTT includes server scheduling; the fixed 100-tick work average is not TPS.

## Verification

- `./gradlew build -PmcVersion=1.16.5` and `./gradlew build -PmcVersion=1.20.1` compile both artifacts and run five isolated real-SQLite regression tests: capacity rollback, injected SQL failure after a merge, structural-NBT multirow removal, numeric-type/list-order separation, and persisted/scoped preferences.
- Live dedicated-server/client smoke checks cover both targets: 200-count entries, localized pinyin/initials/ID filtering, normal-stack withdrawals, cursor and Shift deposits, full-storage item retention, box switching, page/sort restore, and a 320x240 GUI.
- A live 1.16.5 protocol probe covers no-addon fallback, NBT-separated aggregation, native deposit-slot draining, stale sync/revision rejection and box-specific preferences.
- `docs/client-dashboard.png` is an actual 1.16.5 multiplayer screenshot with sample items stored in the real SQLite database, not a mockup. The main README displays it.

Editable diagnostic icon source remains `client/art/icons.svg`; regenerate its checked-in PNG atlas with `python3 client/art/generate_icons.py` (standard library only).
