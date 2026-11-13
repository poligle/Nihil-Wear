# Changelog

## Unreleased

- Releases: a tag builds and signs NIHIL and its watch face and publishes both with
  `SHA256SUMS` (`RELEASING.md`), which `nihil watch` on the computer installs: it finds the
  watch's Wireless debugging on the network, so only its code is typed, and invites it.
- NIHIL's watch face, in Watch Face Format 1: the time in dots, sixty LEDs round the edge with
  the seconds lit, the battery as ten LEDs, red at 15 % or less, and a LED for what is unread.
  A tap on the time opens NIHIL. Drawn by `tools/watchface.py`, its own APK.
- Home: the computer's deck, the watch's apps and the network test in a list the bezel or the
  crown turns, with a tick for each; the one in the middle lit in ink, the rest grey.
- The deck: the computer's music and its keys, its volume on the bezel or the crown and round
  the edge, its caffeine, night light and lock. Over the default network, or Wi-Fi asked for
  when that doesn't reach the computer, let go when the deck closes.
- The light on the edge, as on the desktop and the phone: once round for a notification,
  twice in red for a call, an alarm or a battery at 15, 10 or 5 %, two lights climbing for the
  charger; over any screen with notification access and the accessibility service, which
  `tools/watch halo` grants over adb. NIHIL only looks at which app posted and its category.
- The bezel or the crown moves the list one app a click, never two.
- Caffeine and night light are lit in ink while they are on, with no LED under them.
- `tools/watch install` puts NIHIL and its watch face on the watch; the version of both is in
  `gradle.properties`.
- The first screen: a network test, for the deck the watch will be. It measures how the
  watch reaches the computer over the default network (on most watches, the phone's
  Bluetooth) and over Wi-Fi asked for, each with a new connection and again on the same one,
  and a request with the token on top; and how long mDNS takes to find the computer. Light
  runs round the screen's edge while it measures; each way has its LED, ink when it answered
  and red when it failed, and its milliseconds in dots.
- The watch pairs with NIHIL on the computer as the phone does, with the code and the pin of
  the computer's QR, handed over by adb instead of a camera (`tools/watch invite`). Only
  adb's shell can hand them over.
- `tools/watch`: adb's Wireless debugging paired and connected, the debug build installed,
  the watch invited, the test run and printed, and the watch and the computer forgetting
  each other.
- The bezel or the crown: the test counts the turns that reach it, Galaxy's touch bezel
  included.
- NIHIL's icon on the watch: a watch in dots, as the phone wears the Android robot and the
  desktop the NixOS snowflake.
