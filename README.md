# ShaderLOD

An experimental Minecraft client mod for automatic shader-pack optimizations.
The goal is better performance across shader packs while preserving their appearance
and your chosen settings.

ShaderLOD currently provides native shader-pack selection and an optimization-module
framework. No performance optimizations are shipped yet; this is not a release-ready
performance mod.

## Requirements

- Minecraft 1.21.1 with NeoForge
- Iris and Sodium for NeoForge
- A compatible shader pack

See [dependencies](docs/dependencies.md) for the versions used during development.
Shader-pack and Distant Horizons compatibility is still being tested.

## Usage

Place a development build of ShaderLOD in your client's `mods` folder alongside
its dependencies. Open ShaderLOD's configuration screen from the Mods menu to
select a shader pack and apply available modules. Original shader archives are
preserved, and Off restores the previous selection.

## Development

Build and contribution instructions are in the [development guide](docs/development.md).

## License

[MIT](LICENSE).
