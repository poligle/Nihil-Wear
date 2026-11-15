# Install

On the computer, NIHIL with the deck on (Preferences, Devices). On the watch, developer
options: Settings, About watch, tap the build number until they appear; then, in Developer
options, **ADB debugging** and **Wireless debugging**, on the computer's Wi-Fi, and **Pair new
device**. On the computer, Preferences, Devices, **Install on a watch**, or:

```sh
nihil watch            # pairs, installs the latest release and its face, invites the watch
nihil watch update     # a newer release on a watch already paired
nihil watch forget     # the watch and the computer forget each other
```

It finds the watch on the network, so only the code the watch shows is typed; installs the
newest release with its face, checked against the release's `SHA256SUMS`; hands the watch the
deck's invitation and turns on the light on the edge. Then hold the watch's face and choose
NIHIL's. Releases: `RELEASING.md`.

## Try it

To try this checkout instead: on the computer, NIHIL with the deck on (`nihil.deck.enable`, or Preferences, Devices), and
this repository. On the watch, developer options: Settings, About watch, tap the build
number until they appear; then, in Developer options, **ADB debugging** and **Wireless
debugging**, on the computer's Wi-Fi.

```sh
nix develop
tools/watch pair 192.168.1.31:37123 482915    # Wireless debugging, Pair new device
tools/watch connect 192.168.1.31:41209         # the address on Wireless debugging's screen
tools/watch install                            # NIHIL and its watch face, debug builds
tools/watch invite                             # the watch pairs with this computer
tools/watch probe                              # the network test, and what it measured
tools/watch halo                               # the light on the edge, over any screen
```

Then hold the watch's face and choose NIHIL's. `tools/watch probe --json` prints the whole
report; `tools/watch forget` makes the watch and the computer forget each other. With the
phone on adb too, `tools/watch` finds the watch by itself; `NIHIL_WATCH=SERIAL` picks one.

## Build

```sh
nix develop
./gradlew test assembleDebug
```

Two modules: `app`, NIHIL itself, and `watchface`, its face, drawn by
[`tools/watchface.py`](../tools/watchface.py) (change that, not the XML it writes).
[`tools/screens.py`](../tools/screens.py) renders the face, the home and the deck for this README
and the website. The version, of both, is in `gradle.properties`. The tests are the pure Kotlin
core ([`core/`](../app/src/main/java/com/nihilshell/wear/core)): the invitation, HTTP as the deck
speaks it, the report and the turns of the bezel. They run on the JVM.
