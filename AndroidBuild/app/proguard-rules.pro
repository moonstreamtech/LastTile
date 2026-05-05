# Last Tile — release ProGuard / R8 rules.
#
# Minification is enabled for the release build (see build.gradle.kts
# buildTypes.release). The rules below pin classes that are loaded
# reflectively by Google Play services or Compose runtime so R8 does
# not strip / rename them.

# Google Play Services (Games v2, Tasks, and all GMS libs including those
# pulled in transitively by Firebase).
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# AdMob — keep MobileAds and any class the SDK loads via reflection.
-keep class com.google.android.gms.ads.** { *; }
-keep public class com.google.android.gms.ads.MobileAds { *; }

# Firebase Auth, Firestore, and shared Firebase core classes.
# firebase-auth-ktx and firebase-firestore-ktx ship their own consumer
# ProGuard rules inside the AAR; these blanket keeps are belt-and-braces
# for any class the Firebase SDK loads via reflection that slips through.
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Jetpack Compose runtime is normally R8-safe via included rules in
# the Compose AAR; these blanket keeps are belt-and-braces in case a
# transitive dep emits a Compose class without the standard rules.
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
