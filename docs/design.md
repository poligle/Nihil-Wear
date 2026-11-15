# Design

The same three rules as the desktop and the phone:

1. **Hierarchy is made with greys.** Red only interrupts, and what went wrong is said in ink
   with a red LED before it.
2. **Dots are for display.** The time and the numbers are in Doto; text is Space Grotesk and
   metadata Space Mono.
3. **The frame is hardware.** On a round screen the frame is its edge, and the LEDs follow the
   screen's own shape.

The tokens are the desktop's ([`theme/nihil.nix`](https://github.com/poligle/Nihil-Shell/blob/nihil/theme/nihil.nix)),
mirrored in [`ui/Nihil.kt`](../app/src/main/java/com/nihilshell/wear/ui/Nihil.kt). Text is in
English or Spanish, the watch's language.
