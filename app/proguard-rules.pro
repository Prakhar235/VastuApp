# Add project specific ProGuard rules here.
-keepattributes *Annotation*

# Agora RTC SDK — required so its native/JNI-bound classes survive minification
-keep class io.agora.** { *; }
-dontwarn io.agora.**
