# Donador Automático [![Release APK](https://github.com/cyberplant/donador_automatico/actions/workflows/release-build-and-upload.yml/badge.svg)](https://github.com/cyberplant/donador_automatico/actions/workflows/release-build-and-upload.yml)

Una aplicación Android que facilita la donación automática de saldo telefónico a organizaciones benéficas, específicamente a "Animales Sin Hogar" a través del envío programado de SMS.

## � Screenshots

<p align="center">
  <img src="docs/screenshots/main_screen.png" width="250" alt="Pantalla principal"/>
  &nbsp;&nbsp;&nbsp;
  <img src="docs/screenshots/balance_and_reminders.png" width="250" alt="Saldo y recordatorios"/>
</p>

## �📱 Funcionalidades

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
- Contador de mensajes enviados y confirmaciones pendientes
- Reinicio automático de contadores al inicio de cada mes (con confirmación del usuario)
- Función de limpieza completa de datos

## 🔧 Requisitos del Sistema

- **Android**: Versión 7.0 (API 24) o superior
- **Permisos requeridos**:
  - Envío de SMS
  - Recepción de SMS
  - Programación de alarmas exactas
  - Notificaciones (Android 13+)

## 📥 Instalación

### Descargar desde GitHub Releases

1. Ve a la [página de Releases](https://github.com/cyberplant/donador_automatico/releases) del proyecto
2. Descarga la última versión del APK: `DonadorAutomatico-vX.X.X.apk`
3. Instala el APK en tu dispositivo Android
4. Acepta los permisos de instalación de fuentes desconocidas si es necesario

## 🎯 Uso de la Aplicación

### Primera Configuración
1. Abre la aplicación
2. Concede permisos de SMS cuando se soliciten
3. Activa el recordatorio mensual si deseas (opcional)

### Donar Saldo
1. **Consultar saldo**: Presiona "Consultar Saldo" para ver tu saldo disponible
2. **Configurar envío**:
   - Ingresa la cantidad de mensajes (o usa la sugerencia automática)
   - **Importante**: Antel permite máximo 50 mensajes por día
   - Configura el delay entre mensajes (5 segundos por defecto)
3. **Enviar**: Presiona "Enviar SMS" y observa el progreso
4. **Confirmación**: La app detectará automáticamente las confirmaciones de donación

### Gestión de Recordatorios
- Marca/desmarca el checkbox "Recordarme donar saldo el último día de cada mes"
- Las notificaciones se mostrarán a las 10:00 AM del último día del mes

### Gestión de Contadores
- Al inicio de cada mes, la app detectará el cambio y te preguntará si deseas reiniciar los contadores
- Puedes ver en todo momento:
  - Cantidad total donada
  - Mensajes enviados
  - Confirmaciones pendientes

## ❓ Preguntas Frecuentes

**¿Es segura la aplicación?**
Sí, la aplicación solo requiere permisos de SMS para funcionar y no recopila ni envía información a servidores externos.

**¿Cuánto cuesta cada donación?**
Cada SMS de donación tiene un costo de $10 (pesos uruguayos).

**¿Puedo donar a otras organizaciones?**
Actualmente la aplicación está configurada solo para "Animales Sin Hogar", pero es de código abierto y puede ser modificada.

**¿Qué hago si no recibo confirmación?**
Las confirmaciones pueden tardar algunos minutos. Si no recibes confirmación después de 24 horas, contacta con Antel.

## 🤝 Contribuir

¿Quieres contribuir al proyecto? Consulta nuestra [guía de contribución](CONTRIBUTING.md) para obtener información sobre cómo compilar, desarrollar y enviar cambios.

## 📄 Licencia

Este proyecto está disponible bajo la Licencia MIT. Consulta el archivo LICENSE para más detalles.

## 📞 Soporte

Si encuentras problemas o tienes preguntas:
- Abre un [issue en el repositorio](https://github.com/cyberplant/donador_automatico/issues)
- Asegúrate de tener los permisos necesarios habilitados en tu dispositivo
- Verifica que tienes saldo disponible en tu cuenta Antel
