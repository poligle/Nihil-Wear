# The watch and the computer

## How the watch reaches the computer

A watch has two ways: the phone's Bluetooth, which Wear OS uses on its own, and Wi-Fi, which it
brings up when an app asks for it. The deck tries the default network first and asks for Wi-Fi
only when the computer isn't there; it keeps one connection open while it shows, as the
computer keeps them, and lets the connection and the Wi-Fi go when it closes.

## How the computer reaches the watch

Only through adb. The watch has no camera for the deck's QR, so `tools/watch invite` asks
`nihil-deck pair --json` for what the QR holds (the computer's addresses, the one-time code and
the pin of its certificate) and hands it to the watch with `content call`. The provider that
takes it ([`deck/ShellProvider.kt`](../app/src/main/java/com/nihilshell/wear/deck/ShellProvider.kt))
answers adb's shell and nothing else: a computer handed over by any app on the watch could be
anyone's. Wireless debugging's own pairing is what makes the shell the computer.

From there the watch pairs as the phone does
([`docs/deck.md`](https://github.com/poligle/Nihil-Shell/blob/nihil/docs/deck.md)): TLS that
trusts only the certificate of the pin, then the code, for a token of its own, kept encrypted
with a key that never leaves the Android Keystore. To the computer it is one more device,
listed in Preferences, Devices, and forgotten there as a phone is.

## The network test

<table>
<tr><td><b>Default network</b></td><td>Where Wear OS sends an app's traffic when the app doesn't say: with the phone near, its Bluetooth.</td></tr>
<tr><td><b>Wi-Fi</b></td><td>Asked for, as Wear OS wants it for what Bluetooth can't carry. <i>Up in</i> is how long it took to come up.</td></tr>
<tr><td><b>mDNS</b></td><td>How long the computer's <code>_nihil-deck._tcp</code> takes to be found, and at what address.</td></tr>
<tr><td><b>Bezel or crown</b></td><td>The turns that reached the app: a crown, a rotating bezel and a touch bezel all come as the same scroll.</td></tr>
</table>

Each way gets three rounds, the middle one shown. A round opens a new connection and asks for
`/v1/hello` twice on it: **first** is TCP, TLS and the first answer, what a first tap costs;
**then** is the second answer, on a connection already open. On top, one request with the
token, `/v1/state`, with the computer's own work in it.
