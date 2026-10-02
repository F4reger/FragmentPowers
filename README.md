# FragmentPowers 1.0.0

Target: Paper 1.21.11 / Java 21.

## Build
1. Install JDK 21 and Maven.
2. Open this folder in IntelliJ IDEA as a Maven project.
3. Run Maven `package`.
4. The JAR will be in `target/fragment-powers-1.0.0.jar`.

## Test commands
`/fragment give <player> power_rock_skin`
`/fragment give <player> super_phantom`
`/fragment give <player> super_berserker`
`/fragment give <player> super_frost_blast`
`/fragment give <player> extreme_warden_sonic_boom`

## Resource pack
Use the ZIP in the `resourcepack` folder. It is made for Minecraft Java 1.21.11 (resource pack 75.0).

## Activation
Server-side Paper can detect Q (drop-item) but not the physical TAB key. Therefore the plugin activates on Q while holding a Fragment. A true TAB+Q combo requires a client-side key detector/mod.
