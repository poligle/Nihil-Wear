<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/brand/nihil-logo-dark.svg">
    <img alt="NIHIL" src="docs/brand/nihil-logo-light.svg" width="470">
  </picture>
</p>

<p align="center"><i>ex nihilo</i></p>

<p align="center">A monochrome, dot-matrix launcher for Wear OS</p>

<p align="center">
  <a href="#try-it">Try it</a> &nbsp;&nbsp;&nbsp;
  <a href="#the-network-test">The network test</a> &nbsp;&nbsp;&nbsp;
  <a href="#how-the-computer-reaches-the-watch">Pairing</a> &nbsp;&nbsp;&nbsp;
  <a href="https://github.com/poligle/Nihil-Launcher">Launcher for Android</a> &nbsp;&nbsp;&nbsp;
  <a href="https://github.com/poligle/Nihil-Shell">Desktop for NixOS</a>
</p>

NIHIL for Wear OS will be the watch's side of [NIHIL](https://github.com/poligle/Nihil-Shell):
the desktop's language on the wrist (black, greys for hierarchy, dots for display, red only
to interrupt, light along the edge) and a deck for the computer, as
[NIHIL for Android](https://github.com/poligle/Nihil-Launcher) is.

For now it is one screen, a network test. Before the watch can be a deck it has to reach the
computer, and a watch has two ways to: the phone's Bluetooth, which Wear OS uses on its own,
and Wi-Fi, which it brings up when an app asks. The test measures both, and mDNS, so the deck
is built on what the watch really does.

It is Kotlin on the Android framework alone, like the phone: no AndroidX, no Compose, no
libraries beyond the Kotlin standard library. It needs Wear OS 4 or newer, the oldest that
installs a Watch Face Format watch face, as NIHIL's own will be.

---

## Try it

On the computer, NIHIL with the deck on (`nihil.deck.enable`, or Preferences, Phone), and
this repository. On the watch, developer options: Settings, About watch, tap the build
number (on a Galaxy, Software information, Software version) until they appear; then, in
Developer options, **ADB debugging** and **Wireless debugging**, on the computer's Wi-Fi.

```sh
nix develop
tools/watch pair 192.168.1.31:37123 482915    # Wireless debugging, Pair new device
tools/watch connect 192.168.1.31:41209         # the address on Wireless debugging's screen
tools/watch install                            # this checkout's debug build
tools/watch invite                             # the watch pairs with this computer
tools/watch probe                              # it measures, and this prints what
```

Then turn the bezel or the crown: the test counts the turns that reach it.

`tools/watch probe --json` prints the whole report, every round of every way;
`tools/watch forget` makes the watch and the computer forget each other. With the phone on
adb too, `tools/watch` finds the watch by itself; `NIHIL_WATCH=SERIAL` picks one.

---

## The network test

<table>
<tr><td><b>Default network</b></td><td>Where Wear OS sends an app's traffic when the app doesn't say: with the phone near, its Bluetooth.</td></tr>
<tr><td><b>Wi-Fi</b></td><td>Asked for, as Wear OS wants it for what Bluetooth can't carry. <i>Up in</i> is how long it took to come up.</td></tr>
<tr><td><b>mDNS</b></td><td>How long the computer's <code>_nihil-deck._tcp</code> takes to be found, and at what address.</td></tr>
<tr><td><b>Bezel or crown</b></td><td>The turns that reached the app: a crown, a rotating bezel and Galaxy's touch bezel all come as the same scroll.</td></tr>
</table>

Each way gets three rounds, the middle one shown. A round opens a new connection and asks
for `/v1/hello` twice on it: **first** is TCP, TLS and the first answer, what a first tap
costs; **then** is the second answer, on a connection already open. On top, one request with
the token, `/v1/state`, with the computer's own work in it.

Hierarchy in greys, as everywhere: a way that answered has its LED in ink, one not measured
yet has it off, and what went wrong is said in ink with a red LED before it. While the test
runs, light goes round the LEDs on the screen's edge.

---

## How the computer reaches the watch

Only through adb. The watch has no camera for the deck's QR, so `tools/watch invite` asks
`nihil-deck pair --json` for what the QR holds (the computer's addresses, the one-time code
and the pin of its certificate) and hands it to the watch with `content call`. The provider
that takes it ([`deck/ShellProvider.kt`](app/src/main/java/com/nihilshell/wear/deck/ShellProvider.kt))
answers adb's shell and nothing else: a computer handed over by any app on the watch could be
anyone's. Wireless debugging's own pairing is what makes the shell the computer.

From there the watch pairs as the phone does
([`docs/deck.md`](https://github.com/poligle/Nihil-Shell/blob/nihil/docs/deck.md)): TLS
that trusts only the certificate of the pin, then the code, for a token of its own, kept
encrypted with a key that never leaves the Android Keystore. To the computer it is one more
device, listed in Preferences, Phone, and forgotten there as a phone is. Nothing changes on
the computer for it.

---

## Design language

The same three rules as the desktop and the phone:

1. **Hierarchy is made with greys.** Red only interrupts.
2. **Dots are for display.** The milliseconds are in Doto; text is Space Grotesk and
   metadata Space Mono.
3. **The frame is hardware.** On a round screen the frame is its edge, and the LEDs follow
   the screen's own shape.

The tokens are the desktop's ([`theme/nihil.nix`](https://github.com/poligle/Nihil-Shell/blob/nihil/theme/nihil.nix)),
mirrored in [`ui/Nihil.kt`](app/src/main/java/com/nihilshell/wear/ui/Nihil.kt). Text is in
English or Spanish, the watch's language.

---

## Build

```sh
nix develop
./gradlew test assembleDebug
```

The tests are the pure Kotlin core ([`core/`](app/src/main/java/com/nihilshell/wear/core)):
the invitation, HTTP as the deck speaks it, and the report. They run on the JVM.

---

NIHIL is a personal project and is not affiliated with Nothing Technology. Doto,
Space Grotesk and Space Mono are under the [SIL Open Font License 1.1](app/src/main/assets/fonts/OFL.txt).
The code is MIT licensed.
