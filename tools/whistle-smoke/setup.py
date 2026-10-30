#!/usr/bin/env python3
"""Prepare the isolated whistle test world; preserve its world and EULA choice."""
import argparse
import hashlib
import json
import shutil
import uuid
from pathlib import Path


def offline_uuid(name):
    return str(uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + name).encode()).digest(), version=3))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--accept-eula", action="store_true", help="Record explicit Minecraft EULA acceptance")
    args = parser.parse_args()
    source = Path(__file__).resolve().parent
    target = source.parents[1] / "runs" / "whistle-smoke"
    server = target / "server"
    server.mkdir(parents=True, exist_ok=True)
    (server / "server.properties").write_text("""server-ip=127.0.0.1
server-port=25575
online-mode=false
enforce-secure-profile=false
white-list=true
enforce-whitelist=true
max-players=2
gamemode=creative
force-gamemode=true
difficulty=peaceful
spawn-protection=0
view-distance=5
simulation-distance=4
level-name=world
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
generate-structures=false
allow-nether=true
allow-flight=true
sync-chunk-writes=true
enable-rcon=false
enable-query=false
motd=Dragon Whistle Local Smoke Test
""")
    players = [{"uuid": offline_uuid(name), "name": name} for name in ("WhistleOwner", "WhistleObserver")]
    (server / "whitelist.json").write_text(json.dumps(players, indent=2) + "\n")
    (server / "ops.json").write_text(json.dumps([dict(player, level=4, bypassesPlayerLimit=True) for player in players], indent=2) + "\n")
    eula = server / "eula.txt"
    if args.accept_eula:
        eula.write_text("# Explicitly accepted for this isolated development server.\neula=true\n")
    elif not eula.exists():
        eula.write_text("# Review https://aka.ms/MinecraftEULA before accepting.\neula=false\n")
    pack = server / "world" / "datapacks" / "whistle_smoke"
    shutil.copytree(source / "datapack", pack, dirs_exist_ok=True)
    for name in ("owner", "observer"):
        client = target / name
        client.mkdir(parents=True, exist_ok=True)
        options = client / "options.txt"
        if not options.exists():
            options.write_text("renderDistance:4\nsimulationDistance:5\nmaxFps:30\nfullscreen:false\nguiScale:2\nmipmapLevels:0\n")
    print(f"Prepared localhost smoke test in {target}")
    print("EULA accepted:", "eula=true" in eula.read_text().splitlines())


if __name__ == "__main__":
    main()
