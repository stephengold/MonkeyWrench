<img height="150" src="https://i.imgur.com/ivp31XQ.png" alt="MonkeyWrench Project logo">

[The MonkeyWrench Project][project] is developing
a software library to load 3-D assets into [JMonkeyEngine][jme].

It contains 2 sub-projects:

1. lib: the MonkeyWrench [JVM] runtime library based on lwjgl-assimp
2. apps: non-automated test software

Complete source code (in [Java]) is provided under
[a 3-clause BSD license][license].

MonkeyWrench attempts to load 3-D models and animations
in a wide variety of formats, including:

+ [Blender] .blend
+ [COLLADA] .dae
+ [Autodesk Filmbox .fbx][fbx]
+ [Khronos .glb and .gltf][gltf]
+ [Wavefront .obj][obj]
+ [OGRE] .mesh.xml


## How to add MonkeyWrench to an existing project

MonkeyWrench comes pre-built as a single library that depends on:
+ [LWJGL]
+ lwjgl-assimp
+ jme3-core
+ jme3-desktop
+ jme3-lwjgl3
+ [Heart], and
+ [Wes].

Adding MonkeyWrench to an existing [jMonkeyEngine][jme] project
begins with ensuring that these libraries are on the classpath.

For projects built using [Maven] or [Gradle], it is sufficient to add a
dependency on the MonkeyWrench Library.
The build tool should automatically resolve the remaining dependencies.

Since MonkeyWrench depends on LWJGL version 3 (jme3-lwjgl3),
it isn't compatible with LWJGL version 2 (jme3-lwjgl),
nor will it run on mobile platforms such as Android.


### Gradle-built projects

Add to the project’s "build.gradle" file:

    repositories {
        mavenCentral()
    }
    dependencies {
        implementation 'com.github.stephengold:MonkeyWrench:0.5.0'
    }

For some older versions of Gradle,
it's necessary to replace `implementation` with `compile`.

### Maven-built projects

Add to the project’s "pom.xml" file:

    <repositories>
      <repository>
        <id>mvnrepository</id>
        <url>https://repo1.maven.org/maven2/</url>
      </repository>
    </repositories>

    <dependency>
      <groupId>com.github.stephengold</groupId>
      <artifactId>MonkeyWrench</artifactId>
      <version>0.5.0</version>
    </dependency>

### Configuring the asset manager

The MonkeyWrench loader class is named `LwjglAssetLoader`.

Once the classpath is configured, the next step is to
configure the application's `AssetManager`
to use `LwjglAssetLoader` in place of its default loaders.
In Java:

    import com.github.stephengold.wrench.LwjglAssetLoader;
    // ...
    assetManager.registerLoader(LwjglAssetLoader.class,
            "blend", "dae", "fbx", "glb", "gltf", "obj", "meshxml", "mesh.xml");

### Further considerations

#### Verbose logging

By default, MonkeyWrench emits diagnostic information to `System.out`.
To suppress this output, invoke `loadModel()` on an `LwjglAssetKey`
with verbose logging disabled:

    LwjglAssetKey key = new LwjglAssetKey("Models/m/m.mesh.xml");
    key.setVerboseLogging(false);
    Spatial m = assetManager.loadModel(key);

#### Choice of file format

If the asset to be loaded is available in multiple file formats,
the best choice is usually glTF version 2.0 (either .glb or .gltf format).
For best results, convert assets .blend or .fbx format
to glTF before loading them.

The most efficient format
for loading model assets into JMonkeyEngine is ".j3o".
Best practice is to convert assets to .j3o at build time, not during gameplay.

#### Structure of loaded assets

An asset loaded using MonkeyWrench might be structured differently from
the same asset loaded using jme3-plugins.  In particular:

+ MonkeyWrench always uses
  JMonkeyEngine's new animation system (com.jme3.anim package),
  not the old one (com.jme3.animation package).
  Jme-plugins still uses the old animation system to load some assets.
+ Scene-graph controls (such as `AnimComposer` and `SkinningControl`)
  might be added to different spatials.
+ It might have a different number of nodes, joints, or mesh vertices.
+ Its spatials, materials, animation clips, joints, lights,
  and cameras might not have the same names or indices.

JMonkeyEngine applications
should minimize their assumptions about asset structure.


## How to build MonkeyWrench from source

1. Install a [Java Development Kit (JDK)][adoptium],
   if you don't already have one.
2. Point the `JAVA_HOME` environment variable to your JDK installation:
   (The path might be something like "C:\Program Files\Java\jre1.8.0_301"
   or "/usr/lib/jvm/java-8-openjdk-amd64/" or
   "/Library/Java/JavaVirtualMachines/liberica-jdk-17-full.jdk/Contents/Home" .)
  + using Bash or Zsh: `export JAVA_HOME="` *path to installation* `"`
  + using Windows Command Prompt: `set JAVA_HOME="` *path to installation* `"`
  + using PowerShell: `$env:JAVA_HOME = '` *path to installation* `'`
3. Download and extract the MonkeyWrench source code from GitHub:
  + using [Git]:
    + `git clone https://github.com/stephengold/MonkeyWrench.git`
    + `cd MonkeyWrench`
4. Run the [Gradle] wrapper:
  + using Bash or PowerShell or Zsh: `./gradlew build`
  + using Windows Command Prompt: `.\gradlew build`

After a successful build,
the library JAR will be found in "lib/build/libs".

You can restore the project to a pristine state:
+ using Bash or PowerShell or Zsh: `./gradlew clean cleanDownloads`
+ using Windows Command Prompt: `.\gradlew clean cleanDownloads`


## An overview of the non-automated test software

The following apps are found in the "apps" subproject:

### CompareLoaders

A graphical tool for comparing MonkeyWrench
with the asset loaders built into JMonkeyEngine.

You can run it from the command line:
+ using Bash or PowerShell or Zsh: `./gradlew CompareLoaders`
+ using Windows Command Prompt: `.\gradlew CompareLoaders`

The first time CompareLoaders is run,
the buildscript downloads about 230 MBytes of test data from maven.org .
On subsequent runs, startup should go much quicker.

The app also looks for (optional) test data in
"../ext/glTF-Sample-Models/2.0" relative to the project root.
These data (about 3.7 GBytes) can be installed using Bash and [Git]:
+ `cd ..`
+ `mkdir ext`
+ `cd ext`
+ `git clone https://github.com/KhronosGroup/glTF-Sample-Models.git`
+ `cd ../CompareLoaders`

Application opens a window and displays status using 5 lines of text
in the upper left corner of the window.

<img height="400" src="https://i.imgur.com/bIJ6g78.png"
  alt="Rectangular window with a light blue background
       showing 2 robots, 3 axis arrows, and 5 lines of text
       (4 on the left and one on the right)">

At any given time, one text line is selected, indicated by a yellow arrow.
Lines that can be selected are hereafter referred to as _fields_.

The status lines indicate:
+ which loader(s) are selected:
  + "Default" for jme3-blender and jme3-plugins
  + "Lwjgl" for MonkeyWrench
  + "LwjglVerbose" for MonkeyWrench with verbose logging enabled
  + "SideBySide" to compare "Default" and "Lwjgl" side-by-side
+ which asset group is selected:
  + "gltf-sample-models-10"
  + "gltf-sample-models-10-binary"
  + "gltf-sample-models-10-common"
  + "gltf-sample-models-10-embedded"
  + "gltf-sample-models-20"
  + "gltf-sample-models-20-binary"
  + "gltf-sample-models-20-draco"
  + "gltf-sample-models-20-embedded"
  + "jme3-testdata-31"
  + "jme3-testdata-36"
  + "mixamo-dae"
  + "sketchfab-blend"
  + "sketchfab-dae"
  + "sketchfab-fbx"
  + "sketchfab-glb"
  + "sketchfab-gltf"
  + "sketchfab-obj"
+ which asset in the group is selected for loading
+ which animation (if any) in the loaded asset (if any) is running

#### User controls

You can control the CompareLoaders application using a keyboard and mouse.

The documentation below assumes a keyboard with the "US" (QWERTY) layout.
On keyboards with other layouts, some keys may be labeled differently.
Refer to the help overlay (F1) for localized key labels.

##### Selecting fields and values

+ DownArrow or Numpad2 : selects the next field (cyclic)
+ UpArrow or Numpad8 : selects the previous field (cyclic)
+ "=" or Numpad6 : selects the next value for the selected field (cyclic)
+ "-" or Numpad4 : selected the previous value for the selected field (cyclic)
+ Numpad7 : sets the selected field back by 7 values (cyclic)
+ Numpad9 : advances the selected field by 7 values (cyclic)

##### Running tests

+ L or Return or Numpad5 : loads the selected asset using the selected loader
+ N : advance to the next animation (if any) in the loaded asset (if any)

##### Controlling the camera

+ W and S : dolly the camera forward and backward, respectively
+ A and D : strafe the camera left and right, respectively
+ Q and Z : move the camera up and down, respectively
+ LeftArrow and RightArrow : cause the camera to orbit
+ F7 : reset the camera to its initial position
+ F8 : toggle between orthographic and perspective views

To rotate the camera, drag with the left mouse button (LMB).

To zoom the camera, turn the scroll wheel.

##### Other useful keys

+ H or F1: toggles the help overlay between minimized and full-sized versions
+ Esc : close the window and end the application
+ P : print the scene graph to standard output
+ Shift+P : print a more detailed description of the scene graph
+ Ctrl+P : print an even more detailed description of the scene graph
+ V or F3: toggles the armatures (if any) between visible and hidden
+ "." or Pause : toggle the loaded animation (if any) between paused and running

### TestIssue5232

A console app to reproduce [Assimp issue 5232](https://github.com/assimp/assimp/issues/5232).

### TestIssue5242

A console app to reproduce [Assimp issue 5242](https://github.com/assimp/assimp/issues/5242).

### TestIssue5289

A console app to reproduce [Assimp issue 5289](https://github.com/assimp/assimp/issues/5289).

### TestIssue5292

A console app to reproduce [Assimp issue 5292](https://github.com/assimp/assimp/issues/5292).


[adoptium]: https://adoptium.net/releases.html "Adoptium Project"
[assimp]: https://www.assimp.org/ "The Open Asset Importer Library"
[blender]: https://docs.blender.org "Blender Project"
[collada]: https://en.wikipedia.org/wiki/COLLADA "COLLADA file format"
[fbx]: https://en.wikipedia.org/wiki/FBX "Autodesk FBX file format"
[git]: https://git-scm.com "Git"
[gltf]: https://www.khronos.org/gltf "glTF Project"
[gradle]: https://gradle.org "Gradle Project"
[heart]: https://github.com/stephengold/Heart "Heart Project"
[java]: https://en.wikipedia.org/wiki/Java_(programming_language) "Java programming language"
[jme]: https://jmonkeyengine.org "jMonkeyEngine Project"
[jvm]: https://en.wikipedia.org/wiki/Java_virtual_machine "Java virtual machine"
[license]: https://github.com/stephengold/MonkeyWrench/blob/master/LICENSE "MonkeyWrench license"
[lwjgl]: https://www.lwjgl.org "Lightweight Java Game Library"
[maven]: https://maven.apache.org "Maven Project"
[obj]: https://en.wikipedia.org/wiki/Wavefront_.obj_file "Wavefront OBJ file format"
[ogre]: http://www.ogre3d.org "Ogre Project"
[project]: https://github.com/stephengold/MonkeyWrench "MonkeyWrench Project"
[wes]: https://github.com/stephengold/Wes "Wes Project"
