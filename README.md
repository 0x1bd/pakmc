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

---

## Installation

`pakmc` is compiled as a native binary. Ensure you have `zip` installed on your system path for the compression step.

```bash
git clone https://github.com/0x1bd/pakmc
cd pakmc

./gradlew nativeBinaries
```

The binary will be located in `build/bin/native/debugExecutable/pakmc.kexe`.

---

## Usage

### 1. Initialize a Modpack

Create a new project structure and configuration file.

```bash
./pakmc.kexe init "My Modpack" --mc 1.21.1 --loader neoforge
```

### 2. Add Mods

You can add multiple mods at once using slugs, names, or IDs. By default, it uses Modrinth (`mr`). Use `--provider cf`
for CurseForge.

```bash
# Add from Modrinth
./pakmc.kexe add sodium lithium iris

# Add from CurseForge
./pakmc.kexe add appleskin --provider cf
```

#### Handling Manual Downloads

If a mod on CurseForge has "3rd Party Distribution" disabled by the author:

1. `pakmc` will add the metadata to your project and issue a warning.
2. It will provide a direct link to the specific file download page.
3. You must place the downloaded `.jar` in `contents/jarmods/`.
4. The `build` command will fail with a list of missing links if these files are not present.

### 3. Build the Pack

Compile your project into a distributable format.

```bash
# Build a Modrinth-compatible client pack
./pakmc.kexe build client

# Build a server zip archive
./pakmc.kexe build server
```

---

## Project Structure

* `pakmc.json`: Main configuration (Minecraft version, loader, modpack version).
* `contents/mods/`: Metadata files (`.json`) for every mod in the pack.
* `contents/jarmods/`: Storage for mods that require manual downloading or local jars.
* `contents/configs/`: Configuration files that should be bundled with the pack.
* `build/`: Temporary directory for build artifacts.

---

## License

[GPL V3](LICENSE)