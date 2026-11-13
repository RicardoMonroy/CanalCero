# ML Kit (escáner de QR) crea sus registrars por reflexión desde MlKitInitProvider; sin esto R8 borra el
# constructor vacío y el escáner falla al unirse a un canal.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
