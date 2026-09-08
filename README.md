# Kiosk Browser for Android 📱

Una aplicación Android ultra sencilla y ligera tipo **Kiosk Browser**. Permite fijar una página web a pantalla completa con gestión inteligente de inactividad de pantalla en 2 etapas y panel lateral de configuración.

---

## 🚀 Características

1. **Visor Web a Pantalla Completa**: Muestra cualquier dirección web (`http://` o `https://`) sin barras de estado ni elementos distractores del sistema.
2. **Apagado en 2 Tiempos (Gestión de Inactividad)**:
   - **Tiempo 1 (Oscurecer / Dim)**: Tras $X$ segundos de inactividad, atenúa el brillo de la pantalla para ahorrar energía.
   - **Tiempo 2 (Apagar / Blackout)**: Tras $Y$ segundos de inactividad, reduce el brillo a cero y activa un fondo negro (apariencia de pantalla apagada sin apagar los sensores de toque).
3. **Encendido Instantáneo con 1 Toque (Tap-to-Wake)**:
   - Un solo toque en cualquier punto de la pantalla (estando oscurecida o apagada) la vuelve a encender al 100% de brillo y reinicia el temporizador.
   - El primer toque solo despierta la pantalla y no activa clics accidentales en la web.
4. **Menú Lateral Deslizable (Drawer)**:
   - Desliza desde el **borde izquierdo** de la pantalla para desplegar la barra lateral de opciones.
   - Permite cambiar la URL, el tiempo de oscurecido y el tiempo de apagado.
   - Los cambios se guardan permanentemente en el dispositivo.

---

## 🔒 Manejo de Permisos (Seguro y Transparente)

- **Permisos requeridos**:
  - `INTERNET`: Necesario para cargar las webs.
  - `ACCESS_NETWORK_STATE`: Para verificar el estado de red.
- **Sin permisos invasivos**: El control de brillo se realiza a nivel de ventana de la app (`WindowManager.LayoutParams.screenBrightness`). **No** requiere permisos especiales del sistema como `WRITE_SETTINGS` o `SYSTEM_ALERT_WINDOW`.
- **Compatibilidad total**: Habilitado el tráfico Cleartext (`usesCleartextTraffic="true"`) para que funcione con direcciones locales o IPs privadas (`http://192.168.x.x`).

---

## 🛠️ Cómo Compilar el Proyecto

### Requisitos previos
- **JDK 17** instalado (o incluido en Android Studio).
- **Android Studio** (versión recomendada: Jellyfish, Ladybug o posterior).

### Opción 1: Con Android Studio (Recomendado)
1. Clona o descarga este repositorio:
   ```bash
   git clone https://github.com/TU_USUARIO/Kiosk.git
   ```
2. Abre Android Studio y selecciona **Open**, eligiendo la carpeta del proyecto.
3. Espera a que Gradle sincronice las dependencias.
4. Conecta tu dispositivo Android o inicia un emulador.
5. Pulsa **Run** (el botón verde `▶` o `Shift + F10`).

### Opción 2: Desde la Consola / Terminal
Puedes compilar la APK directamente usando el Gradle Wrapper incluido:

- **Windows (PowerShell / CMD)**:
  ```powershell
  .\gradlew.bat assembleDebug
  ```
- **Linux / macOS**:
  ```bash
  chmod +x gradlew
  ./gradlew assembleDebug
  ```

El archivo APK generado se guardará en:  
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📦 Cómo hacer una Release en GitHub

Este repositorio no contiene claves ni variables de entorno privadas, por lo que está listo para subirse directamente a GitHub.

### Método A: Crear Release Manualmente
1. Genera la APK de Release:
   ```bash
   ./gradlew assembleRelease
   ```
   La APK se generará en `app/build/outputs/apk/release/app-release-unsigned.apk`.

2. *(Opcional)* Si deseas generar una APK firmada digitalmente con tu propia Keystore:
   - En Android Studio ve a **Build > Generate Signed Bundle / APK...**
   - Elige **APK**, crea/selecciona tu archivo Keystore y genera la versión final.

3. Ve a tu repositorio en GitHub:
   - Entra en la sección **Releases** (en el lateral derecho).
   - Haz clic en **Draft a new release**.
   - Crea un nuevo tag (ejemplo: `v1.0.0`).
   - Escribe un título (ejemplo: `Kiosk Browser Release v1.0.0`).
   - Arrastra y suelta el archivo `.apk` en el recuadro de adjuntos.
   - Haz clic en **Publish release**.

### Método B: Release Automatizada mediante GitHub Actions
El proyecto incluye un flujo de trabajo preconfigurado en `.github/workflows/release.yml`.

Para publicar una nueva versión automáticamente:
```bash
git add .
git commit -m "Release v1.0.0"
git tag v1.0.0
git push origin main --tags
```
GitHub Actions compilará la APK en la nube y creará la **Release en GitHub** automáticamente con el archivo APK listo para descargar.
