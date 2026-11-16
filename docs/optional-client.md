# Optional client compatibility

## Installation and rollout

The root project remains a dedicated-server mod (`environment: server`). The `client` subproject is an independent client-only addon (`environment: client`) with no server implementation or SQLite dependency. Install matching 1.7.1 server/client releases for the full light-themed dashboard and in-place box switching; both Minecraft 1.16.5 and 1.20.1 are supported. The server remains usable without any client addon. The light theme and modal rendering fix are client-side; avoiding screen reopen/mouse recentering also requires the 1.7.1 server.

The server negotiates the dashboard only when a player advertises `sharedbackpack:dashboard_state_v1`. Clients without that channel retain the original commands and chest GUI. A new client connected to an older server that supports `state_v1` keeps the original title-row search enhancement. Servers without Shared Backpack and unrelated chests receive no dashboard. Unload remains the original 9x5 blank container.

## Independent interface

The light-themed dashboard has scrollable box navigation and box creation on the left, search and a 9x5 virtual catalog in the center, native player inventory below, and capacity, namespace filtering, upgrade, diagnostics, refresh and a deposit slot on the right. It fits a 320x240 scaled GUI. Box switching and creation update the current handler instead of reopening a screen, preserving mouse position, carried items and sidebar scroll. Search/mod filters clear on storage changes, and target-specific page/sort preferences are restored. Blank box names generate a unique name; switching boxes is validated against the current team on the server.

Catalog entries aggregate identical item IDs and structurally equal complete NBT. Counts are separate 64-bit values, not oversized `ItemStack` counts. Hover shows the exact quantity; inventory withdrawals respect the item's native maximum stack size. Durability, names, enchantments, maps, nested data, list order and NBT numeric types remain significant. Physical database rows remain capped at 64 for compatibility; the capacity readout counts those rows, not virtual catalog entries. Aggregation does not provide unlimited physical storage.

Search uses the client's actual localized item names, full pinyin, initials and registry IDs, with multiple whitespace-separated terms. Filtering, namespace selection, six sort orders and paging are local. Language/resource reloads rebuild localized search names. Command queries such as `/c shitou` initialize the new dashboard search. Page and sort preferences are stored in SQLite per player, storage owner, team/box storage type and box name. Search does not overwrite the remembered unfiltered page; clearing it restores that page, clamped to available results. Preferences survive server restart.

## Inventory ownership and transactions

The server uses a vanilla 9x6 container to synchronize real inventory slots. Slots 0–44 and 46–53 are empty, read-only dummy slots hidden by the client; slot 45 is the real deposit slot; slots 54–89 are the player's main inventory and hotbar. Virtual catalog cells never contain transferable inventory stacks. The client reuses the opened handler in its independent `HandledScreen` rather than closing/reopening it during screen replacement.

Left-click withdraws one item to the cursor. Right-click withdraws up to one native stack. Shift+left-click withdraws a stack directly into free inventory space. Server-owned catalog keys and revisions identify the complete item identity; client-supplied item IDs, NBT, owners or arbitrary quantities cannot choose a withdrawal. Removal rechecks live matching rows in one SQLite transaction before giving items to the player.

Shift+left-click on player inventory sends the native quick-move request without generic-container inventory prediction. The source remains visible until the server commits the entire deposit and synchronizes its removal. Clicking the deposit slot with a carried stack similarly clears the cursor only after a successful server transaction. A physical deposit-slot item drains after vanilla click processing at the end of the server tick, not during cursor ownership transitions. Full storage or SQL failure keeps the original items; failed physical deposits and carried items return to inventory on close, dropping only overflow under normal inventory-full semantics. Upgrades consume a diamond only after the database upgrade succeeds.

When switching storage, a physical deposit-slot item is committed to the old storage first or returned to inventory (dropping only overflow); it is never silently deposited into the new box. The carried stack stays on the cursor. Each switched catalog has a fresh revision, so withdrawals using the previous storage's item keys are rejected.

`addItem`, `addBoxItem` and the dashboard share the same all-or-nothing deposit path. Capacity failures cannot partially merge items and then return failure. SQLite rollback covers item rows and storage timestamps. The migration only adds the preference table and does not rewrite stored item NBT.

## Wire protocol

`dashboard_request_v1` and `dashboard_state_v1` are separate from the legacy search protocol. Requests carry version, active sync ID and a bounded action body. The server ignores malformed/trailing input, unknown versions/actions, stale handlers/revisions, dead/spectator players and requests outside the player's current team or existing boxes.

Catalog snapshots carry a sync ID and revision, storage metadata/preferences/box names in the first chunk, and at most 32 entries per chunk. Icons retain complete NBT with count one; aggregate quantities are transmitted independently. Only the final chunk commits the client's new catalog, so a partial refresh never replaces the current visible list. A server-side comparison once per second refreshes open viewers when rows, capacity or boxes change; unchanged data sends no new catalog. Explicit operations refresh immediately. Status uses the existing separately versioned diagnostic channels.

Diagnostics poll every two seconds while the dashboard is open, with the existing active-handler checks and one-second server rate limit. The Server and Storage tabs are selected by click or Tab; Esc/close returns to the warehouse. While open, the dashboard skips inventory rendering entirely, including virtual item icons/counts, native slots and the carried stack, rather than drawing an overlay over depth-tested item icons. Input remains blocked. Storage diagnostics include used/free physical slots, NBT-aware item kinds, matching kinds, item totals, view preferences/filters and heap/tick-budget ratios. The 50 ms budget percentage is average tick execution time divided by 50 ms, not TPS or CPU utilization. No new server status payload or sensitive system data is exposed. Traffic counters remain addon payloads only, excluding vanilla synchronization and network overhead.

## Verification

- `./gradlew build -PmcVersion=1.16.5` and `./gradlew build -PmcVersion=1.20.1` compile both artifacts and run five isolated real-SQLite regression tests: capacity rollback, injected SQL failure after a merge, structural-NBT multirow removal, numeric-type/list-order separation, and persisted/scoped preferences.
- Live dedicated-server/client smoke checks cover both targets: localized filtering, white-theme rendering, unchanged native pointer/sync ID and carried stack across box switching/creation, filter reset and scoped preferences, blocked inventory input while diagnostics is open, tab switching, filtered match statistics and a 320x240 GUI. Actual screenshots confirm that diagnostic pages contain no warehouse item/cursor overlay.
- A live 1.16.5 protocol probe covers no-addon fallback, NBT-separated aggregation, native deposit-slot draining, stale sync/revision rejection and box-specific preferences.
- `docs/client-dashboard.png`, `docs/client-status.png` and `docs/client-storage.png` are actual 1.16.5 multiplayer screenshots of the light warehouse and both diagnostic tabs, with sample items in the real SQLite database. The main README displays them after the original no-addon interface.

Editable diagnostic icon source remains `client/art/icons.svg`; regenerate its checked-in PNG atlas with `python3 client/art/generate_icons.py` (standard library only).
