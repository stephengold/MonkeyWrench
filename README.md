<img height="150" src="https://i.imgur.com/ivp31XQ.png" alt="MonkeyWrench Project logo">

[The MonkeyWrench Project][project] is developing
a software library to import 3-D models in various formats into [JMonkeyEngine][jme].

It contains 2 sub-projects:

1. lib: the MonkeyWrench runtime library
2. apps: non-automated test software

Complete source code (in [Java]) is provided under
[a 3-clause BSD license][license].

As of October 2023, the library is an unreleased work in progress
and not ready for production.


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

The following apps are found in the "apps" sub-project:

### CompareLoaders

A keyboard-driven graphical tool for comparing MonkeyWrench
with the model loaders built into JMonkeyEngine.

You can run it from the command line:
+ using Bash or PowerShell or Zsh: `./gradlew CompareLoaders`
+ using Windows Command Prompt: `.\gradlew CompareLoaders`

The first time CompareLoaders is run,
the buildscript downloads about 230 MBytes of test data from maven.org .
On subsequent runs, startup should go much quicker.

The app also looks for (optional) test data in
"../ext/glTF-Sample-Models/2.0" relative to the project root.
These data (about 3.7 GBytes) can be installed using Bash:
+ `cd ..`
+ `mkdir ext`
+ `cd ext`
+ `git clone https://github.com/KhronosGroup/glTF-Sample-Models.git`
+ `cd ../CompareLoaders`

Application opens a window and displays status using 4 or 5 lines of text
in the upper left corner of the window.
The displayed status includes:
+ which test data are selected (the "Locator", in JME jargon)
+ which loaders are selected (such as "Default"
  for jme3-blender and jme3-plugins or "Lwjgl" for MonkeyWrench)
+ which model in the test data is selected for loading
+ which animation (if any) in the loaded model (if any) is running

At any given time, one text line is selected, indicated by a yellow arrow.
Lines that can be selected are hereafter referred to as _fields_.

#### Keyboard controls

Selecting fields and values:

+ DownArrow or Numpad2 : selects the next field (cyclic)
+ UpArrow or Numpad8 : selects the previous field (cyclic)
+ "=" or Numpad6 : changes the selected field to its next value (cyclic)
+ "-" or Numpad4 : changes the selected field to its previous value (cyclic)
+ Numpad7 : sets the selected field back by 7 values (cyclic)
+ Numpad9 : advances the selected field by 7 values (cyclic)

Running tests:

+ L or Return or Numpad5 : loads the selected model using the selected loader

Camera movement:

+ W and S : dolly the camera forward and backward, respectively
+ A and D : strafe the camera left and right, respectively
+ Q and Z : move the camera up and down, respectively
+ LeftArrow and RightArrow : cause the camera to orbit

Rotate the camera by dragging with the left mouse button.

Other useful keys:

+ H or F1: toggles the help overlay between minimized and full-sized versions
+ Esc : close the window and end the application
+ P : print the scene graph to standard output
+ Shift+P : print a more detailed description of the scene graph
+ N : advances to the next animation (if any) in the loaded model (if any)
+ "." or Pause : toggle the loaded animation (if any) between paused and running


### TestIssue5232

A console app to reproduce [Assimp issue 5232](https://github.com/assimp/assimp/issues/5232).

### TestIssue5242

A console app to reproduce [Assimp issue 5242](https://github.com/assimp/assimp/issues/5242).


[adoptium]: https://adoptium.net/releases.html "Adoptium Project"
[assimp]: https://www.assimp.org/ "The Open Asset Importer Library"
[git]: https://git-scm.com "Git"
[gradle]: https://gradle.org "Gradle Project"
[java]: https://en.wikipedia.org/wiki/Java_(programming_language) "Java programming language"
[jme]: https://jmonkeyengine.org "jMonkeyEngine Project"
[license]: https://github.com/stephengold/MonkeyWrench/blob/master/LICENSE "MonkeyWrench license"
[lwjgl]: https://www.lwjgl.org "Lightweight Java Game Library"
[project]: https://github.com/stephengold/MonkeyWrench "MonkeyWrench Project"
