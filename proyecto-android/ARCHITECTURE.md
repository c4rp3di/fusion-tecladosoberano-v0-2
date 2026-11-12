# Teclado Soberano (App 9 · Suite Vigía) — Arquitectura

## Principios
- 100% offline: el Manifest NO declara `INTERNET` (ni `ACCESS_NETWORK_STATE`). Sin librerías de red, analítica ni nube.
- Todo el texto, diccionario y portapapeles se procesan y guardan localmente (almacenamiento privado de la app, cifrado).
- Modo contraseña: sin sugerencias, sin historial de portapapeles, sin aprendizaje.
- `allowBackup="false"` y `dataExtractionRules` vacías: nada sale del dispositivo por copias de seguridad.

## Estructura de paquetes (`com.vigia.tecladosoberano`)

```
app/src/main/
├── AndroidManifest.xml
├── java/com/vigia/tecladosoberano/
│   ├── ime/
│   │   ├── SovereignIME.kt            # InputMethodService: ciclo de vida, InputConnection, orquestación
│   │   ├── KeyboardState.kt           # Estado (shift, caps, modo, página) — sobrevive a rotación
│   │   ├── InputFieldInfo.kt          # Detección de isPassword y tipo de campo
│   │   └── KeyActionHandler.kt        # Traduce pulsaciones a commitText/deleteSurroundingText/etc.
│   ├── ui/
│   │   ├── KeyboardView.kt            # Vista custom (Canvas): dibuja teclas, feedback visual, táctil
│   │   ├── KeyboardLayouts.kt         # Definición de layouts: QWERTY ES, símbolos 1/2, numérico, emojis
│   │   ├── Key.kt                     # Modelo de tecla (etiqueta, código, ancho relativo, tipo)
│   │   ├── SuggestionBar.kt           # Barra superior: sugerencias + atajo Bóveda + portapapeles
│   │   ├── EmojiPanel.kt              # Panel de emojis por categorías (RecyclerView)
│   │   ├── ClipboardPanel.kt          # Panel de historial de portapapeles
│   │   └── Theme.kt                   # Colores: fondo, tecla, pulsada, acento (personalizable)
│   ├── prediction/
│   │   ├── Dictionary.kt              # Trie en memoria cargado desde assets/es_dict.txt
│   │   ├── Predictor.kt               # Prefijo + frecuencia; autocompletado de la palabra actual
│   │   └── UserWordStore.kt           # Palabras aprendidas (cifrado); desactivado en modo contraseña
│   ├── clipboard/
│   │   ├── ClipboardWatcher.kt        # OnPrimaryClipChangedListener (solo si no es campo contraseña)
│   │   └── ClipboardStore.kt          # Últimos N textos en EncryptedSharedPreferences/Keystore
│   ├── vault/
│   │   ├── VaultBridge.kt             # Lanza la petición, descifra y escribe con commitText
│   │   ├── PuenteContrato.kt          # Constantes del protocolo (idéntico en La Bóveda)
│   │   └── PuenteCanal.kt             # ECDH + HKDF + AES-GCM (idéntico en La Bóveda)
│   └── settings/
│       ├── SettingsActivity.kt        # Activar teclado, color de acento, fondo
│       └── Prefs.kt                   # SharedPreferences (solo preferencias visuales)
└── res/
    ├── xml/method.xml                 # Subtipo es-ES
    ├── layout/keyboard_root.xml       # Contenedor: SuggestionBar + KeyboardView + paneles
    ├── layout/activity_settings.xml
    ├── values/colors.xml, themes.xml, strings.xml
    └── drawable/ (iconos vector: shift, borrar, bóveda, portapapeles, emoji)
```

## Flujo de la interfaz
1. `onCreateInputView()` infla `keyboard_root.xml` (una sola vez por servicio).
2. `onStartInputView(info, restarting)` lee `info.inputType` → `InputFieldInfo.isPassword`; configura layout inicial (texto, numérico, email) y activa/desactiva barra de sugerencias y portapapeles.
3. `KeyboardView` emite eventos `onKey(code)`; `KeyActionHandler` los aplica sobre `currentInputConnection`.
4. `onFinishInputView` / `onFinishInput`: se limpia el buffer de palabra actual y cualquier credencial en memoria.
5. Rotación: el IME no se recrea con la Activity; `onConfigurationChanged` solo recalcula alturas. `KeyboardState` vive en el servicio, así que shift/modo/página se conservan.

## Estado de mayúsculas
- `SHIFT_OFF → SHIFT_ONCE` (un toque; se apaga tras una letra) `→ CAPS_LOCK` (doble toque rápido) `→ SHIFT_OFF`.
- Auto-mayúscula al inicio de frase vía `info.initialCapsMode` / `getCursorCapsMode`.

## Integración con La Bóveda (implementada)

Requisito: inyectar usuario/contraseña en el campo activo **sin usar el portapapeles**.

**Canal: Intent explícito a `PuenteTecladoActivity` (La Bóveda) + `ResultReceiver` de vuelta.** Sustituye al diseño inicial con AIDL: la Bóveda necesita una pantalla (biometría y selector), y una Activity la da sin servicio extra.

- La Activity está exportada pero protegida con el permiso de firma `com.vigia.boveda.permission.PUENTE_TECLADO`: el sistema solo deja abrirla a apps firmadas con la misma llave. Ambas apps declaran el permiso.
- Antes de enviar nada, el teclado comprueba con `checkSignatures` que La Bóveda instalada tiene su misma firma.
- Capa extra: la respuesta viaja cifrada (ECDH P-256 efímero + HKDF-SHA256 + AES-256-GCM), con id de petición y paquete destino como datos autenticados. Ver `PuenteCanal.kt` (idéntico en ambas apps).

**Flujo:**
1. Toque en 🔑 → `VaultBridge.iniciar` genera par efímero + id y lanza la Activity de La Bóveda.
2. La Bóveda cierra su sesión y exige autenticación nueva (huella/rostro o contraseña maestra) vía `DesbloqueoActivity`.
3. Selector: primero las credenciales vinculadas al paquete de la app (`appPaquete`), después el resto con buscador; las no vinculadas piden confirmación.
4. La Bóveda cifra usuario+contraseña y responde por el `ResultReceiver`.
5. El teclado descifra y guarda la credencial en `CharArray` (máx. 90 s). Cuando el campo de la app vuelve a tener el foco: campo normal → escribe usuario con `commitText`; campo de contraseña → escribe la contraseña. Segundo paso (usuario → contraseña): tocar 🔑 en el campo de contraseña. Pulsación larga en 🔑 pide una credencial nueva.
6. Tras escribir, `Arrays.fill` a cero. Si el usuario cambia de app, se descarta.

**Limitaciones honestas:**
- El texto inyectado termina en la app destino; es inevitable. Se evita el portapapeles y su exposición a otras apps.
- Las cadenas en JVM no se pueden borrar de forma garantizada; se minimiza la vida del dato.
- Hace falta la misma llave de firma en ambas apps.

## Modo contraseña (`isPassword`)
Se considera contraseña si la variación es `TYPE_TEXT_VARIATION_PASSWORD`, `VISIBLE_PASSWORD`, `WEB_PASSWORD`, `TYPE_NUMBER_VARIATION_PASSWORD`. Efectos:
- Barra de sugerencias oculta; `Predictor` y `UserWordStore` no se invocan.
- `ClipboardWatcher` pausado y panel de portapapeles deshabilitado.
- Botón de Bóveda visible (es el caso de uso principal).
- `FLAG_SECURE` aplicado a la ventana del teclado para impedir capturas.

## Seguridad adicional
- `android:allowBackup="false"`, `android:usesCleartextTraffic="false"`.
- Sin `ACCESS_NETWORK_STATE`, sin `WRITE_EXTERNAL_STORAGE`.
- ProGuard/R8 activado en release; sin logs con contenido tipeado (`Log` prohibido en release).
- Portapapeles y palabras aprendidas cifrados con clave en Android Keystore (AES-GCM).
- Dependencias permitidas: `androidx.core`, `androidx.appcompat`, `androidx.recyclerview`, `androidx.security:security-crypto`, `androidx.biometric` (en la Bóveda). Ninguna con red.

## Hoja de ruta
1. **Base e interfaz de escritura** (este commit): Manifest, IME, KeyboardView, layouts, Shift/Caps, numérico, símbolos, tema.
2. Predicción: Dictionary/Predictor + barra de sugerencias.
3. Emojis y panel de símbolos ampliado.
4. Portapapeles seguro.
5. ✅ VaultBridge + puente con La Bóveda (Intent firmado + ResultReceiver cifrado).
6. Ajustes y personalización de color.

## Compilación (Fusión / GitHub Actions)
- Gradle en Groovy (`build.gradle`, `settings.gradle`), AGP 8.5.0, Kotlin 1.9.24, `compileSdk`/`targetSdk` 34: igual que La Bóveda y lo que instala el workflow.
- La firma la aporta el keystore compartido `apk-build/debug.keystore` de Fusión (el bloque `signingConfigs` ya apunta a él). Es lo que hace coincidir la firma de ambas apps y concede el permiso del puente 🔑.

## Portapapeles, emojis y pulsación larga (v0.3)

- **Portapapeles seguro** (`clipboard/`): historial cifrado con AES-256-GCM (clave en Android Keystore) en un archivo privado. `ClipboardWatcher` escucha `OnPrimaryClipChangedListener` y hace una lectura al abrir el teclado. Ignora clips marcados `IS_SENSITIVE`, y se pausa en campos de contraseña. Nunca se usa para credenciales de La Bóveda.
- **Emojis** (`ui/EmojiData`, `ui/EmojiPanel`): rejilla por categorías, filtrada con `Paint.hasGlyph`; recientes en `Prefs` (no se guardan en campos de contraseña).
- **Pulsación larga** (`ui/KeyboardView`): 350 ms abre una tira de alternativas. Fila superior q–p: números 1–0; vocales y c: tildes/diéresis/cedilla; punto: `? ¿ ! ¡ : ; … - '`. Deslizar elige; soltar escribe.
- **Diagnóstico 🔑**: toasts diferenciados (`vault_*`) y watchdog de 3 s si Bóveda no llega a abrirse.

## Sugerencias, botón 123 y buscador de emojis (v0.4)

- **Sugerencias en español** (`suggest/`): `Dictionary` lee `assets/es_words.txt` (local, ~2600 palabras por frecuencia). `Predictor` busca por prefijo sin importar tildes. `WordStore` guarda, cifrado con Keystore, solo palabras que NO están en el diccionario y su recuento (máx. 1500). Sin sugerencias, ni aprendizaje, en campos de contraseña, correo, URL o sin sugerencias. Ajuste para desactivar el aprendizaje y botón para borrarlo.
- **Botón 123** junto al portapapeles: alterna entre teclado numérico y letras.
- **Buscador de emojis** (`ui/EmojiSearch`): tabla de palabras clave en español. Se teclea con el propio teclado; la consulta solo vive en memoria.
- **Bóveda**: ajuste «Desbloqueo del teclado» (pedir siempre / recordar 1, 5 o 15 min). La clave sigue solo en memoria; solo cambia el temporizador.

## v0.5: personalización, precisión y sugerencias avanzadas

- `settings/KeyboardEditorActivity`: editor con vista previa. Las medidas (`KbMetrics`) se guardan en `Prefs` (perfil vertical/horizontal) y el servicio las aplica al instante por `OnSharedPreferenceChangeListener`.
- `ui/KeyboardView`: alturas por fila, pesos de teclas especiales, separación, «imán» (la letra más probable según el prefijo favorece la tecla vecina) y decisión de tecla al tocar/soltar.
- `suggest/`: `Dictionary` indexado por forma sin tildes; `Predictor` (atajos, prefijos, correcciones por distancia de edición, siguiente palabra); `CountStore` + `SecureFile` (cifrado Keystore) para `WordStore` y `BigramStore`; `ShortcutStore` para atajos.
- Privacidad: nada de esto sale del móvil; sin aprendizaje ni atajos en contraseñas, correo o URL; las opciones que guardan más información (palabras frecuentes, pares) están apagadas por defecto.
