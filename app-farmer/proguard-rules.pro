# Keep rules for release builds (minification + resource shrinking enabled).

# Turso libSQL — JNI-based embedded database; native methods must keep names.
-keep class tech.turso.** { *; }

# Data classes read via manual column-index mapping — kept defensively so R8
# never renames members if reflection is introduced later.
-keep class com.pashurakshak.app.data.local.** { *; }
