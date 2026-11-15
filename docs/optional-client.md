# Optional client compatibility

The root project remains a dedicated-server mod (`environment: server`). The `client` subproject is an independent client-only addon (`environment: client`) with no dependency on the server mod or SQLite. Both compile only the small protocol constants from `src/protocol/java`.

All inventory opens, slots, clicks, and item stacks continue to use vanilla generic containers. The server sends extra view metadata only when Fabric reports that a client supports `sharedbackpack:state_v1`. No client handshake is required to use `/c`, `/cc`, bindings, or the existing control bar.

The addon decorates an existing generic container only after receiving metadata for its active sync ID. It keeps the original screen and handler, and clears metadata when that screen closes or the connection ends. Unrelated chests and servers without the protocol keep their original interface. Search widgets fit in the original title row, including at small GUI sizes. The original inventory label and control icons remain visible.

Client requests contain a protocol version and either an open action or a sync ID plus a query limited to 128 UTF-16 characters. The server parses bounded data before scheduling work on its main thread. It ignores unknown versions/actions, malformed or trailing data, unavailable databases, dead/spectator players, and searches that do not target the player's current backpack handler. The client never chooses a team, box owner, item slot, or inventory contents. A shortcut open is accepted only while the player's normal inventory handler is active. Filtering keeps the server cursor stack in place.

Build both versions separately with `./gradlew clean build -PmcVersion=<version>`. CI publishes the server and optional client as separate artifacts for each Minecraft version.

Diagnostics use separate `status_request_v1` and `status_v1` channels, leaving the original view/search protocol compatible with earlier addons. Requests include a sequence and the active backpack sync ID. The client polls every two seconds only while the information panel is open; the server checks the current handler, advertised response channel, and a one-second per-player rate limit. Disconnect clears the limit and all client counters. Replies contain bounded version strings and aggregate status, without file paths or player names. Sequence checks reject late replies and a six-second timeout marks cached data stale. The server measures tick work with a fixed 100-entry ring buffer.

Upload/download counters include view metadata, search/open requests, and diagnostics payloads only; they exclude vanilla container packets and network overhead. Rates sample monotonic time once per second and average the last four intervals, and totals reset per connection. Request RTT includes main-thread scheduling. These figures do not measure the full connection bandwidth or TPS.

The client uses a small PNG icon atlas with a rect-based SVG source in `client/art/icons.svg`. `python3 client/art/generate_icons.py` recreates the texture without third-party dependencies. Icon buttons retain translated names/tooltips. The information panel fits the minimum GUI size and consumes keyboard, mouse, wheel, and drag input while it covers the original container.

Manual multiplayer checks for a release:

- Start a dedicated server with only the server JAR and Fabric API. Join with an unmodified matching Minecraft client; check `/c`, `/c <query>`, box management, item moves, and unload mode.
- Join the same server with Fabric API and only the optional client JAR. Check `B`, commands, search by Enter/button, clear, paging, mod filtering, sorting, boxes, upgrades, unload, resize, and GUI scale.
- Hold a tagged item on the cursor while submitting/clearing a search. Close the menu and confirm that the item and its complete NBT remain intact.
- Focus search and type letters bound to inventory/hotbar/drop actions; typing must not trigger those actions. Check Esc, clicks, clipboard shortcuts, and a query of 128 characters.
- Open ordinary chests after a backpack, reconnect, and join a server without the server mod. Check that no search widgets appear on ordinary containers and `B` causes no request on unsupported servers.
- Keep a player without the addon connected alongside a player with it, and repeat shared storage operations for both.
- Open the information panel and check version/health values, periodic rates, cumulative counters, close/Esc, and GUI resize. Hold an item on the cursor and confirm clicks and number/drop keys cannot interact with the covered inventory. Close the panel and confirm polling stops; reconnect and confirm traffic totals reset.
- Send status requests with the wrong screen ID, an unsupported protocol version, malformed/trailing data, and repeated requests within one second. Check they produce no reply and leave the vanilla connection working. Test an older server without the status endpoint; its search bar should still work.
