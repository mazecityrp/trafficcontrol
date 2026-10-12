RailTest drives BlockTrafficRail.getActualState() over hand built layouts to
check the joint rules (straight / diagonal corner and run / slope) without launching the
game.  ModTrafficControl.java here is a stub: the real one drags in optional mod
APIs (OpenComputers, Immersive Railroading) that a bare classpath does not have.

Run it against a Forge dev workspace, guava 21 / commons-io 2.5 / commons-lang3
3.5 / gson 2.8.0 ahead of the rest of the classpath.  Bootstrap.register() also
needs launchwrapper 1.12, asm-debug-all 5.2 and jopt-simple 5 on it, all in the
Gradle cache once the workspace is set up:

  javac -d out -cp "<forgeBin.jar>;<libs>" -sourcepath tools/test \
      tools/test/RailTest.java \
      src/main/java/com/clussmanproductions/trafficcontrol/blocks/BlockTrafficRail.java
  java -cp "out;<pinned libs>;<forgeBin.jar>;<libs>" RailTest
