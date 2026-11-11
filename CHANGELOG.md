# Changelog

## Unreleased

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
