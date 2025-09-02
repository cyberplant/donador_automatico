# Donador Automático [![Android CI](https://github.com/cyberplant/donador_automatico/actions/workflows/android.yml/badge.svg)](https://github.com/cyberplant/donador_automatico/actions/workflows/android.yml)

Una aplicación Android que facilita la donación automática de saldo telefónico a organizaciones benéficas, específicamente a "Animales Sin Hogar" a través del envío programado de SMS.

## 📱 Funcionalidades

### 💰 Consulta de Saldo
- Consulta automática del saldo disponible enviando "SALDO" al número 226
- Análisis inteligente del mensaje de respuesta para calcular la cantidad óptima de SMS a enviar
- Sugerencia automática de cantidad de mensajes basada en el saldo (saldo ÷ 10)

### 📤 Envío Automático de SMS
- Envío masivo de mensajes a organizaciones benéficas (Animales Sin Hogar - 24200)
- Configuración de delay entre mensajes (en segundos)
- Barra de progreso visual en tiempo real
- Confirmación automática de donaciones recibidas (+10 unidades por donación confirmada)

### ⏰ Recordatorios Mensuales
- Notificaciones automáticas el último día de cada mes
- Recordatorio configurable para mantener el hábito de donar
- Configuración persistente entre sesiones

### 📊 Tracking de Donaciones
- Contador visual de cantidad donada acumulada
- Historial de respuestas y confirmaciones
- Función de limpieza completa de datos

## 🔧 Requisitos del Sistema

- **Android**: Versión 7.0 (API 24) o superior
- **Permisos requeridos**:
  - Envío de SMS
  - Recepción de SMS
  - Programación de alarmas exactas
  - Notificaciones (Android 13+)

## 🚀 Compilación con Android Studio

### Prerrequisitos

1. **Android Studio**: Versión Arctic Fox (2020.3.1) o superior
2. **JDK**: Versión 17 (requerido para la compilación)
3. **Android SDK**: API 24+ (configurado en Android Studio)

### Pasos de Compilación

1. **Clonar el proyecto**:
   ```bash
   git clone https://github.com/tu-usuario/donador-automatico.git
   cd donador-automatico
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

## 🎯 Uso de la Aplicación

### Primera Configuración
1. Abre la aplicación
2. Concede permisos de SMS cuando se soliciten
3. Activa el recordatorio mensual si deseas (opcional)

### Donar Saldo
1. **Consultar saldo**: Presiona "Consultar Saldo" para ver tu saldo disponible
2. **Configurar envío**:
   - Ingresa la cantidad de mensajes (o usa la sugerencia automática)
   - Configura el delay entre mensajes (5 segundos por defecto)
3. **Enviar**: Presiona "Enviar SMS" y observa el progreso
4. **Confirmación**: La app detectará automáticamente las confirmaciones de donación

### Gestión de Recordatorios
- Marca/desmarca el checkbox "Recordarme donar saldo el último día de cada mes"
- Las notificaciones se mostrarán a las 10:00 AM del último día del mes

## 🤖 Desarrollo con IA

Esta aplicación fue desarrollada utilizando **Cursor**, un entorno de desarrollo asistido por IA que acelera significativamente el proceso de desarrollo. Cursor proporciona:

- **Asistencia inteligente**: Sugerencias contextuales durante la escritura de código
- **Refactorización automática**: Mejoras en la estructura del código
- **Detección de errores**: Identificación proactiva de problemas potenciales
- **Generación de código**: Creación automática de componentes y funciones comunes

El uso de Cursor permitió desarrollar esta aplicación de manera más eficiente, enfocándonos en la lógica de negocio mientras la IA manejaba aspectos técnicos repetitivos.

## 📄 Licencia

Este proyecto está disponible bajo la Licencia MIT. Consulta el archivo LICENSE para más detalles.

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
   - GitHub Actions automáticamente compilará el APK
   - Se generarán dos versiones:
     - `DonadorAutomatico-debug-v1.0.0.apk` (versión de desarrollo)
     - `DonadorAutomatico-v1.0.0.apk` (versión de producción, si está configurada la firma)

### Configuración de Firma Digital (Opcional)

Para distribuciones de producción, configura la firma digital:

1. **Crear Keystore**:
   ```bash
   keytool -genkeypair -v -storetype PKCS12 -keystore keystore.jks -alias mykey -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Configurar signing.properties**:
   ```properties
   storeFile=../keystore.jks
   storePassword=tu_password
   keyAlias=mykey
   keyPassword=tu_password
   ```

3. **Configurar Secrets en GitHub** (para CI/CD):
   - Ve a Settings > Secrets and variables > Actions
   - Agrega los siguientes secrets:
     - `SIGNING_KEY_ALIAS`
     - `SIGNING_KEY_PASSWORD`
     - `SIGNING_STORE_PASSWORD`

4. **Subir Keystore** (NO commitear al repositorio):
   - Coloca el archivo `keystore.jks` en el directorio raíz del proyecto
   - Asegúrate de que `.gitignore` excluya este archivo

### Descarga de APKs

Los usuarios pueden descargar las versiones compiladas desde:
- La sección "Releases" del repositorio
- Assets de cada release publicada
- Enlaces directos generados automáticamente

## 🤝 Contribuir

Las contribuciones son bienvenidas. Por favor:

1. Haz fork del proyecto
2. Crea una rama para tu feature (`git checkout -b feature/nueva-funcionalidad`)
3. Commit tus cambios (`git commit -am 'Agrega nueva funcionalidad'`)
4. Push a la rama (`git push origin feature/nueva-funcionalidad`)
5. Abre un Pull Request

## 📞 Soporte

Si encuentras problemas o tienes preguntas:
- Abre un issue en el repositorio
- Revisa la documentación de Android Studio para problemas de compilación
- Asegúrate de tener los permisos necesarios habilitados en tu dispositivo
