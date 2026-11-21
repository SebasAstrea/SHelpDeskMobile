# Reglas R8/ProGuard de SHelpDeskMobile (release con minify + shrinkResources).

# No hacemos reflexión sobre nuestras clases: los JSON se leen con org.json del
# sistema, así que no hacen falta keep globales (dejarían el minificado inútil).
# Se conservan las trazas de línea para leer los crash reports del release.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
