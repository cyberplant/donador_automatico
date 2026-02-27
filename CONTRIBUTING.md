# Guía de Contribución y Desarrollo

## 🚀 Compilación con Android Studio

### Prerrequisitos

1. **Android Studio**: Versión Arctic Fox (2020.3.1) o superior
2. **JDK**: Versión 17 (requerido para la compilación)
3. **Android SDK**: API 24+ (configurado en Android Studio)

### Pasos de Compilación

1. **Clonar el proyecto**:
   ```bash
   git clone https://github.com/cyberplant/donador_automatico.git
   cd donador_automatico
   ```

2. **Abrir en Android Studio**:
   - Inicia Android Studio
   - Selecciona "Open" en la pantalla de bienvenida
   - Navega hasta la carpeta del proyecto y selecciónala

3. **Sincronizar dependencias**:
   - Android Studio debería sincronizar automáticamente las dependencias de Gradle
   - Si no ocurre, ve a `File > Sync Project with Gradle Files`

4. **Configurar dispositivo**:
   - Conecta un dispositivo Android físico o configura un emulador
   - Asegúrate de que el dispositivo tenga permisos de SMS habilitados

5. **Compilar y ejecutar**:
   - Haz clic en el botón "Run" (ícono de play verde)
   - Selecciona tu dispositivo conectado/emulador
   - La aplicación se instalará y ejecutará automáticamente

### Configuración de Build

El proyecto utiliza:
- **Lenguaje**: Kotlin
- **Gradle**: DSL Kotlin
- **JDK**: 17 (requerido)
- **Compose**: Para la interfaz de usuario moderna
- **Coroutines**: Para operaciones asíncronas
- **Target SDK**: API 35 (Android 15)
- **Min SDK**: API 24 (Android 7.0)

### Solución de Problemas

#### Permisos no concedidos
Si la aplicación no puede enviar/recibir SMS:
1. Ve a Configuración > Aplicaciones > Donador Automático
2. Permisos > SMS (habilitar Enviar y Recibir)

#### Problemas de compilación
- Limpia el proyecto: `Build > Clean Project`
- Reconstruye: `Build > Rebuild Project`
- Invalida caché: `File > Invalidate Caches / Restart`

## 🔄 Flujos de Trabajo (GitHub Actions)

### Compilación de Testing en PRs

El workflow `test-build-on-push.yml` se ejecuta automáticamente en:
- Cada push a ramas que no sean `main`
- Cada pull request creado o actualizado
- Manualmente mediante `workflow_dispatch`

**Características:**
- Ejecuta tests de verificación (`./gradlew check`)
- Compila un APK debug automáticamente en todos los PRs
- El APK debug queda disponible como artefacto en la página de Actions por 30 días

**Formas de compilar el APK debug:**
1. **Automático**: Se compila en cada PR automáticamente
2. **Manual desde Actions**: Ve a la pestaña "Actions" > selecciona "Build app" > "Run workflow"
3. **Desde un comentario en PR**: Escribe `/build` en un comentario del PR para compilar

**Para descargar el APK de prueba:**
1. Ve a la pestaña "Actions" del repositorio
2. Selecciona el workflow run correspondiente a tu PR
3. Descarga el artefacto `DonadorAutomatico-debug-{commit-hash}`

### Compilación de Producción en Releases

El workflow `release-build-and-upload.yml` se ejecuta automáticamente cuando:
- Se publica una nueva release en GitHub
- Se ejecuta manualmente mediante `workflow_dispatch`

**Características:**
- Compila únicamente el APK de producción (firmado)
- Sube el APK automáticamente a la release de GitHub
- Requiere keystore configurada en los secrets del repositorio

## 📦 Releases y Distribución

### Creando una Release

Cuando estés listo para crear una nueva versión de la aplicación:

1. **Crear un Tag**:
   ```bash
   git tag -a v1.0.0 -m "Release version 1.0.0"
   git push origin v1.0.0
   ```

2. **Crear Release en GitHub**:
   - Ve a la pestaña "Releases" en tu repositorio
   - Haz clic en "Create a new release"
   - Selecciona el tag que acabas de crear
   - Agrega un título y descripción de la release
   - Publica la release

3. **Compilación Automática**:
   - GitHub Actions automáticamente compilará el APK de producción
   - Se generará: `DonadorAutomatico-v1.0.0.apk` (versión firmada)

### Configuración de Firma Digital

Para distribuciones de producción, configura la firma digital:

1. **Crear Keystore**:
   ```bash
   keytool -genkeypair -v -storetype PKCS12 -keystore keystore.jks -alias mykey -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Configurar signing.properties** (local, no commitear):
   ```properties
   storeFile=../keystore.jks
   storePassword=tu_password
   keyAlias=mykey
   keyPassword=tu_password
   ```

3. **Configurar Secrets en GitHub** (para CI/CD):
   - Ve a Settings > Secrets and variables > Actions
   - Agrega los siguientes secrets:
     - `KEYSTORE`: Contenido del archivo keystore.jks codificado en base64
     - `SIGNING_KEY_ALIAS`: Alias de la clave (ej: "mykey")
     - `SIGNING_KEY_PASSWORD`: Contraseña de la clave
     - `SIGNING_STORE_PASSWORD`: Contraseña del keystore

4. **Subir Keystore** (NO commitear al repositorio):
   - Coloca el archivo `keystore.jks` en el directorio raíz del proyecto para desarrollo local
   - Asegúrate de que `.gitignore` excluya este archivo

## 🤝 Contribuir

Las contribuciones son bienvenidas. Por favor:

1. Haz fork del proyecto
2. Crea una rama para tu feature (`git checkout -b feature/nueva-funcionalidad`)
3. Commit tus cambios (`git commit -am 'Agrega nueva funcionalidad'`)
4. Push a la rama (`git push origin feature/nueva-funcionalidad`)
5. Abre un Pull Request
6. **Para probar cambios**: 
   - El APK se compila automáticamente en cada PR
   - O comenta `/build` en el PR para forzar una nueva compilación
   - Descarga el artefacto desde la pestaña Actions

## 🤖 Desarrollo con IA

Esta aplicación fue desarrollada utilizando **Cursor**, un entorno de desarrollo asistido por IA que acelera significativamente el proceso de desarrollo. Cursor proporciona:

- **Asistencia inteligente**: Sugerencias contextuales durante la escritura de código
- **Refactorización automática**: Mejoras en la estructura del código
- **Detección de errores**: Identificación proactiva de problemas potenciales
- **Generación de código**: Creación automática de componentes y funciones comunes

El uso de Cursor permitió desarrollar esta aplicación de manera más eficiente, enfocándonos en la lógica de negocio mientras la IA manejaba aspectos técnicos repetitivos.
