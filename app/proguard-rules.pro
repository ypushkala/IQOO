# sherpa-onnx is reached from native code by name: keep its classes and members untouched.
-keep class com.k2fsa.sherpa.onnx.** { *; }
# MediaPipe / protobuf rely on reflection.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
# Debug-level logging never ships: v/d/i calls are removed (warnings and errors stay, and never carry content).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
# The optional accessibility probe and debug tools exist only in debug builds.
