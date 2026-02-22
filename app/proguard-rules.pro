# CaltrainNow ProGuard Rules
# Add project specific ProGuard rules here.

# Room
-keep class com.caltrainnow.data.db.** { *; }

# Keep core models for reflection if needed
-keep class com.caltrainnow.core.model.** { *; }
