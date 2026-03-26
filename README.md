# Xander's Sodium Options (Revived)

A revival of [isXander/xanders-sodium-options](https://github.com/isXander/xanders-sodium-options), which was archived after Sodium 0.8.7 overhauled its internal options API. This fork updates the mod to work with the new Sodium API and will continue development going forward.

**Original authors:** [isXander](https://github.com/isXander), [IMB11](https://github.com/IMB11)

---

## What it does

Xander's Sodium Options replaces Sodium's settings screen with a Minecraft-style alternative powered by [Yet Another Config Lib (YACL)](https://modrinth.com/mod/yacl). Options from Sodium and compatible mods are converted into YACL categories, giving a unified look and feel.

## Supported versions

| Minecraft | Sodium | Status |
|-----------|--------|--------|
| 26.1      | 0.8.7  | Planned (waiting for Fabric yarn mappings) |
| 1.21.11   | 0.8.7  | Supported |

## Mod compatibility

- [Iris](https://modrinth.com/mod/iris) — shader pack page replacement
- [Sodium Extra](https://modrinth.com/mod/sodium-extra) — *temporarily disabled*, pending new API adaptation
- [More Culling](https://modrinth.com/mod/moreculling) — *temporarily disabled*, pending new API adaptation

Other mods that integrate with Sodium's GUI should work automatically. If something breaks, open an issue.

## Building

```bash
# Prerequisites: JDK 21, NTFS junctions on Windows (see below)

# Build for current version
gradlew.bat build

# Build and collect JARs
gradlew.bat buildAndCollect
```

On Windows without admin privileges, create NTFS junctions before building:

```cmd
mklink /J versions\1.21.11\src src
```

## License

[LGPL-3.0-or-later](LICENSE.md) — same as the original project.
