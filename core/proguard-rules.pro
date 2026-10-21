# Shared R8 rules for both release APKs (applied via consumerProguardFiles).

# Turso libSQL — JNI-based embedded database; native methods must keep names.
-keep class tech.turso.** { *; }

# Data classes are read via manual column-index mapping — keep defensively so
# R8 never renames members if reflection is introduced later.
-keep class com.pashurakshak.app.data.local.** { *; }

# WorkManager / Room — Room instantiates generated *_Impl databases and
# WorkManager loads its entities by reflection. R8 full mode otherwise renames
# or strips the generated no-arg constructor, crashing at app startup with
# "NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init>".
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.work.impl.model.** { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# androidx.startup discovers initializers through manifest metadata.
-keep class * implements androidx.startup.Initializer { *; }
