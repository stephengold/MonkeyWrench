# release log for the MonkeyWrench library

## Version 0.5.1 released on TBD

+ Replaced the `LwjglAssetKey.setFlags()` method with a new
  constructor (API change)
+ Added lwjgl-assimp native libraries for platforms
  other than the build platform.
+ Downgraded LWJGL to v3.3.2 for compatibility with JME v3.6.1.
+ Began ignoring `TransformTrack` keyframes with negative animation times.
+ Added "Textures/" and "%sTextures/" to the texture search path.
+ Added switch cases to handle "$raw.EmissiveColor|" material properties.

## Version 0.5.0 released on 22 October 2023

This was the initial baseline release for testing.
