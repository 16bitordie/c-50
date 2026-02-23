# Proyecto C-50 VR
**Documento de Arquitectura y Planificación**

## 1. Stack Tecnológico Acordado

### Hardware
*   **Robot (C-50):** Raspberry Pi + Cámara (CSI/USB) + HAT de control de motores/servos + Micrófono/Altavoz.
*   **Cliente:** Smartphone Android + Gafas VR móviles (tipo Cardboard).

### Software & Lenguajes
*   **Raspberry Pi (C-50):** Python 3.
    *   *WebRTC:* `aiortc` (Video, Audio, Data Channels).
    *   *Hardware:* `gpiozero` o librerías específicas del HAT.
    *   *Bluetooth:* `Bleak` o `BlueZ` (Servidor BLE para setup Wi-Fi).
*   **App Android (Receptor):** Kotlin.
    *   *UI/UX:* Jetpack Compose o XML.
    *   *Autenticación:* Firebase Authentication (Google Login).
    *   *WebRTC:* Librería oficial `org.webrtc`.
    *   *Realidad Virtual:* Google Cardboard SDK (Open Source) + OpenGL ES.
*   **Infraestructura (Nube):**
    *   *Signaling Server:* Node.js (Socket.io) o Python (WebSockets) para el handshake inicial de WebRTC.
    *   *STUN/TURN:* Servidores públicos de Google (STUN) y Coturn/Twilio (TURN) para traspasar NAT/Firewalls.

---

## 2. Arquitectura de Comunicación

1.  **Setup Inicial:** App Android conecta por BLE a la Raspberry Pi -> Envía credenciales Wi-Fi -> Raspberry Pi se conecta a la red local.
2.  **Descubrimiento:** Ambos dispositivos se conectan al *Signaling Server* en la nube, se autentican y se intercambian datos de conexión (SDP y candidatos ICE).
3.  **Conexión P2P (WebRTC):**
    *   **Track de Video:** Raspberry Pi -> App Android.
    *   **Track de Audio:** Bidireccional (Raspberry Pi <-> App Android).
    *   **Data Channel:** App Android -> Raspberry Pi (Comandos de joystick, botones y datos del giroscopio para head-tracking). Latencia ultrabaja (UDP).

---

## 3. Desglose de Fases (Roadmap)

Se recomienda seguir este orden estrictamente para aislar problemas y asegurar que cada capa base funciona antes de construir sobre ella.

### Fase 1: Infraestructura Base y Señalización
*Objetivo: Lograr que dos dispositivos se encuentren en internet.*
*   **1.1** Crear proyecto en Firebase y configurar Google Login en una app Android vacía.
*   **1.2** Desarrollar y desplegar un *Signaling Server* básico (ej. en un VPS barato o Heroku/Render usando Node.js).
*   **1.3** Crear scripts de prueba simples en Python y Kotlin que se conecten al Signaling Server y logren intercambiar mensajes de texto.

### Fase 2: Cerebro de C-50 (Raspberry Pi) - Core WebRTC
*Objetivo: La Raspberry emite video y recibe comandos por consola.*
*   **2.1** Configurar entorno Python en Raspberry Pi e instalar `aiortc`.
*   **2.2** Capturar video de la cámara y enviarlo a través de WebRTC.
*   **2.3** Implementar WebRTC Data Channels en Python para recibir strings (ej. "ADELANTE", "ATRAS").
*   **2.4** Conectar la recepción de esos strings a la librería de control de motores (`gpiozero`) para mover a C-50.

### Fase 3: App Android - Modo Normal y Control
*Objetivo: Ver el video en el móvil y controlar a C-50 con botones en pantalla.*
*   **3.1** Integrar la librería WebRTC en Android.
*   **3.2** Conectar la app al Signaling Server, negociar con la Raspberry y mostrar el video en un `SurfaceViewRenderer` normal (pantalla completa).
*   **3.3** Implementar controles en pantalla (Joystick virtual o botones).
*   **3.4** Enviar los comandos del joystick a través del Data Channel de WebRTC hacia la Raspberry.

### Fase 4: La Experiencia VR (El gran reto)
*Objetivo: Inmersión total con gafas VR y head-tracking.*
*   **4.1** Integrar Google Cardboard SDK (C++ / JNI / Kotlin) en el proyecto Android.
*   **4.2** Configurar el entorno OpenGL ES para renderizar la vista estéreo (ojo izquierdo/derecho).
*   **4.3** Interceptar los frames de video de WebRTC y convertirlos en una textura de OpenGL para proyectarlos en el entorno virtual.
*   **4.4** Capturar los datos de rotación de la cabeza (Pitch, Yaw, Roll) desde el Cardboard SDK.
*   **4.5** Enviar estos datos por el Data Channel a la Raspberry para mover los servos de la cámara (Pan/Tilt).

### Fase 5: Configuración Inicial (BLE) y Pulido
*Objetivo: Hacer el sistema "Plug & Play" para el usuario final.*
*   **5.1** Programar servidor BLE en la Raspberry Pi (Python) esperando credenciales.
*   **5.2** Programar cliente BLE en Android para escanear a C-50 y enviar SSID/Password.
*   **5.3** Script en Raspberry para aplicar la configuración de red y reiniciar servicios.
*   **5.4** Implementar el canal de audio bidireccional (Micrófono móvil -> Altavoz C-50 y viceversa).
*   **5.5** Pruebas de latencia, manejo de reconexiones y pulido de interfaz.

---

## 4. Siguientes Pasos Recomendados

Para empezar a programar, el paso más lógico es la **Fase 1.2**: El Servidor de Señalización. Sin él, la Raspberry y el móvil no podrán encontrarse para establecer la conexión WebRTC. 

¿Deseas que empecemos a definir el código de este servidor en Node.js o Python?