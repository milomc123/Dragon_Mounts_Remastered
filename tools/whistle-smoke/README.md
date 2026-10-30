# Local multiplayer whistle smoke test

This runs the current source on a localhost-only server with two offline development profiles. The world, profiles, logs, and EULA choice live under `runs/whistle-smoke/`, separate from the usual `run/` world. Both profiles are operators and whitelisted. No Microsoft sign-in is needed.

Requires Java 21 and Python 3. Use three terminals from the repository root:

```sh
# Record your own EULA acceptance once, then start the server.
python3 tools/whistle-smoke/setup.py --accept-eula
bash gradlew runWhistleSmokeServer
```

Once the server reports `Done`, launch the clients in the other terminals:

```sh
bash gradlew runWhistleSmokeOwner
```

```sh
bash gradlew runWhistleSmokeObserver
```

Each client connects automatically to `127.0.0.1:25575`. If Java is not on your path on macOS, first run `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`. Preserve any custom `GRADLE_USER_HOME` used for the normal development build.

## Prepare the scenario

After both players join, enter this in the **owner's** chat (or omit the slash in the server console):

```mcfunction
/function whistle_smoke:setup
```

This resets only the tagged smoke-test dragons, prepares two Overworld platforms and a Nether platform, gives each player a white whistle, and creates two adult dragons owned by `WhistleOwner`:

- **Whistle Bound** at `(5, 81, 5)`: right-click it with the owner's white whistle to bind it.
- **Whistle Unbound** at `(-8, 81, 5)`: leave this dragon unbound.

Both have a chest and five diamonds. They have AI and gravity disabled to keep the scenario stable. The observer starts 1,024 blocks away with no whistle binding. Sneak-right-click a dragon with an empty hand to inspect its inventory. Keep the owner's whistle in the main hand; right-click away from the dragon to open the command menu, then choose **Whistle**. Let the configured cooldown expire between calls.

## Checks

Run these functions from the owner's chat. After every step, verify the white whistle tooltip still names **Whistle Bound**, its command menu opens, and calling works. The observer's whistle should stay unbound.

| Step | Command | Expected result |
| --- | --- | --- |
| Start tracking another player | `/function whistle_smoke:tracking_near` | Neither player's binding changes when they see each other. |
| Repeat tracking | `/function whistle_smoke:tracking_away`, then `tracking_near` | Binding survives both tracking transitions. |
| Long-distance same-dimension teleport | `/function whistle_smoke:owner_far`, then Call | Bound dragon reaches the owner; five diamonds remain. |
| Player changes dimension | `/function whistle_smoke:owner_nether`, then Call | Bound dragon reaches the Nether; binding and inventory survive. |
| Return across dimensions | `/function whistle_smoke:owner_home`, then Call | Bound dragon returns; inventory remains intact. |
| Unbound dragon changes dimension | `/function whistle_smoke:unbound_nether` | White whistle still names the bound dragon. |
| Bound dragon changes dimension independently | `/function whistle_smoke:dragon_nether`, then Call | Calling brings the same dragon back; no duplicate remains. |
| Reconnect | Disconnect and reconnect the owner | Tooltip, command menu, calling, and inventory still work. |
| Restart | Stop and restart the server; reconnect both clients | Binding and inventory survive disk persistence. |

`owner_home` and `owner_far` use fixed Overworld platforms. `owner_nether` uses a platform above the Nether roof. The dragon-transfer functions operate on dragons currently in the Overworld. Return home and Call before the last two dragon-transfer checks.

Use `/function whistle_smoke:inspect` before and after a loaded cross-dimension call to print the bound dragon's vanilla UUID, persistent `dragonUUID`, and owner. The two dragon IDs should remain unchanged, and only one bound dragon should be present across the two dimensions. Messages about no matching entity in the other dimension are expected. The Nether test chunk is kept loaded so this exercises transfer of an existing entity.

The automated suite separately covers reconstruction of unavailable dragons and rejection of obsolete entities. This manual setup checks the real client tooltip, menu, tracking packets, and reconnect behavior.

## Stop and reset

Enter `/stop` in the owner's chat (or `stop` in the server console), then quit each client normally. To repeat the scenario, run `setup` again after both players connect; this clears their white-whistle bindings and replaces the tagged dragons. It does not delete the world. Server logs are in `runs/whistle-smoke/server/logs/latest.log`; each client's logs are under its own `logs/` directory.
