# pakmc

**pakmc** is a CLI tool for managing Minecraft modpacks. It streamlines the process of searching, adding, and building
modpacks with automatic dependency resolution and support for multiple mod providers.

## Key Features

* **Multi-Provider Support**: Search and fetch mods from both **Modrinth** and **CurseForge**.
* **Recursive Dependency Resolution**: Automatically detects and adds required dependencies for every mod.
* **Smart Loader Resolution**: Automatically finds the latest stable (non-beta) versions for NeoForge, Fabric, Forge,
  and Quilt.
* **Build Targets**:
    * **Client**: Generates `.mrpack` files compatible with Modrinth and Prism Launcher.
    * **Server**: Generates a `.zip` archive with all necessary server-side mod jars.
* **Environment Aware**: Handles client-side, server-side, and universal mod restrictions.
* **Version Pinning**: Keep selected mod versions fixed during updates.

---

## Installation

### Fedora 44 (COPR)

Install the DNF COPR plugin if it is not already present, enable the repository,
and install `pakmc`:

```bash
sudo dnf install dnf5-plugins
sudo dnf copr enable 0x1bd/pakmc
sudo dnf install pakmc
```

After installation, run `pakmc --help` to get started. The package installs
`zip`, which pakmc needs when it creates modpack archives.

### Build from source

`pakmc` is compiled as a native binary. Install `zip`, a
JDK 21 or newer, and the development files for libcurl, then run:

```bash
git clone https://github.com/0x1bd/pakmc
cd pakmc

./gradlew assembleReleaseBinary
```

The binary will be located at `build/release/pakmc`.

---

## Usage

### 1. Initialize a Modpack

Create a new project structure and configuration file.

```bash
pakmc init "My Modpack" --mc 1.21.1 --loader neoforge
```

### 2. Add Mods

You can add multiple mods at once using slugs, names, or IDs. By default, it uses Modrinth (`mr`). Use `--provider cf`
for CurseForge.

```bash
# Add from Modrinth
pakmc add sodium lithium iris

# Add and pin a specific version
pakmc add sodium --version <version-id-or-number> --pin

# Add from CurseForge
pakmc add appleskin --provider cf

# Add a local JAR and record where it should be installed
pakmc add ~/Downloads/example-mod.jar --side client
pakmc add ./server-utility.jar --side dedicated-server
```

Local JARs are copied into `contents/jarmods/` and tracked in `contents/mods/` with their hashes and side. Use
`--side client` for client-only mods, `--side dedicated-server` for mods that must only be in the dedicated server
archive, or omit `--side` (equivalent to `--side both`) for mods needed on both sides. `--side server` is for
logical-server mods that are also needed in a client pack for its integrated server. You can also select the local
provider explicitly with `--provider local`.

#### Handling Manual Downloads

If a mod on CurseForge has "3rd Party Distribution" disabled by the author:

1. `pakmc` will add the metadata to your project and issue a warning.
2. It will provide a direct link to the specific file download page.
3. You must place the downloaded `.jar` in `contents/jarmods/`.
4. The `build` command will fail with a list of missing links if these files are not present.

### 3. Select, Pin, and Update Versions

Pin an installed mod at its current version, or select a specific version and pin it:

```bash
pakmc pin sodium lithium
pakmc select iris --version <version-id-or-number> --pin

# Update only unpinned mods
pakmc update

# Allow a mod to update again
pakmc unpin sodium
```

### 4. Build the Pack

Compile your project into a distributable format.

```bash
# Build a Modrinth-compatible client pack
pakmc build client

# Build a server zip archive
pakmc build server
```

---

## License

[GPL v3](LICENSE)