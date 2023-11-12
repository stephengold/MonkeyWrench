# release log for the MonkeyWrench library

## Version 0.5.3 released on 11 November 2023

+ Bugfix:  index out of range in `SkinningControl` with software skinning
+ Changed so that a BVH import won't try to generate a complete scene.
+ Updated the imageio-webp library to version 3.10.1 .

## Version 0.5.2 released on 8 November 2023

+ Bugfix: `NullPointerException` in `MaterialBuilder.modifyTextureCoordinates()`
+ Bugfix: `NumberFormatException` in `MaterialBuilder.toTexture()`
+ Bugfix: assertion failure while loading the "zophrac" model
+ Disabled verbose logging by default.
+ Implemented a content cache in the `AssetFileSystem` class.
+ Added a `TextureLoader` to each `LwjglAssetKey` instance,
  with a new getter and constructor.
+ Changed how texture asset paths are generated; now a format can override
  the texture's file extension.
+ Added the `TextureEdit` class and the `PathEdit` enum (both public).
+ Added support for embedded textures in WebP format.
+ Added support for materials that specify "flat" shading.
+ Enhanced the `MaterialBuilder` class to handle or ignore
  more Assimp material properties.
+ Changed to sort transparent geometries in the `Transparent` bucket.
+ Implemented recursive dumping of metadata.
+ Enhanced the diagnostics for when a non-embedded texture isn't found
  or when an embedded texture fails to load.
+ Added sanity checks for the dimensions of an embedded texture.
+ Clarified the verbose logging output.
+ Plugged some memory leaks.

## Version 0.5.1 released on 23 October 2023

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
