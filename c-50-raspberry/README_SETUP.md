# Guía de Instalación y Configuración: Cerebro C-50 (Raspberry Pi)

Este documento detalla los pasos exactos para configurar desde cero una Raspberry Pi para que funcione como el cerebro del robot C-50. Debido a las dependencias específicas de hardware y video (WebRTC, PyAV, v4l2), es **crítico** seguir estos pasos al pie de la letra.

## 1. Preparación del Sistema Operativo

Para garantizar la menor latencia de video posible y compatibilidad nativa con `v4l2` (Video4Linux2), el proyecto requiere una versión específica del sistema operativo.

1. Descarga e instala **Raspberry Pi Imager** en tu ordenador.
2. En "Choose OS", selecciona **Raspberry Pi OS (Other)**.
3. Selecciona **Raspberry Pi OS (Legacy, 64-bit)** (basado en Debian Bullseye). *Nota: Si tu Raspberry Pi es muy antigua (ej. Pi 2), selecciona la versión de 32-bit.*
4. Flashea la tarjeta SD.

## 2. Configuración Inicial del Hardware

Una vez que la Raspberry Pi haya arrancado y tengas acceso a la terminal (por SSH o con monitor/teclado):

1. Ejecuta la herramienta de configuración:
   ```bash
   sudo raspi-config
   ```
2. Ve a **Interface Options**.
3. Selecciona **Legacy Camera** (o simplemente **Camera** dependiendo de la versión exacta) y habilítala.
4. Reinicia la Raspberry Pi:
   ```bash
   sudo reboot
   ```

## 3. Instalación del Entorno C-50

Hemos creado un script automatizado (`setup.sh`) que resuelve todos los conflictos de dependencias (especialmente entre `aiortc`, `av`, `aiohttp` y `cryptography` en arquitectura ARM).

1. Clona el repositorio en tu Raspberry Pi:
   ```bash
   git clone <URL_DE_TU_REPOSITORIO>
   cd c-50/c-50-raspberry
   ```

2. Dale permisos de ejecución al script de instalación:
   ```bash
   chmod +x setup.sh
   ```

3. Ejecuta el script:
   ```bash
   ./setup.sh
   ```

### ¿Qué hace exactamente `setup.sh`?
* Instala las librerías de sistema necesarias (`python3-av`, `python3-numpy`, `libavdevice-dev`, etc.) usando `apt-get` para evitar compilaciones pesadas y errores de Cython.
* Elimina versiones conflictivas de `aiohttp` del sistema.
* Crea un entorno virtual (`venv`) con la opción `--system-site-packages` para heredar las librerías multimedia precompiladas.
* Instala versiones muy específicas y probadas de las librerías de Python (`aiortc==1.2.1`, `aiohttp==3.8.6`, `cryptography==38.0.4`, etc.) para asegurar la compatibilidad total.

## 4. Ejecución del Cerebro

Una vez que el script termine con éxito, puedes arrancar el cerebro del robot:

1. Activa el entorno virtual:
   ```bash
   source venv/bin/activate
   ```

2. Ejecuta el script principal (asegúrate de usar `-m` para que Python reconozca la estructura de paquetes):
   ```bash
   python3 -m src.main
   ```

Si todo está correcto, verás en la consola que el robot se conecta al servidor de señalización y detecta la cámara usando `v4l2` nativo.

## Solución de Problemas Comunes

* **Error: `ModuleNotFoundError: No module named 'src'`**
  * Causa: Estás ejecutando `python3 src/main.py` directamente.
  * Solución: Ejecuta siempre `python3 -m src.main` desde la raíz de la carpeta `c-50-raspberry`.
* **Error: `module 'aiohttp' has no attribute 'ClientWSTimeout'`**
  * Causa: Conflicto de versiones entre `python-socketio` y `aiohttp`.
  * Solución: Asegúrate de haber ejecutado el `setup.sh` o instala manualmente `python-socketio[asyncio_client]==5.7.2` y `aiohttp==3.8.6`.
* **Error: `AttributeError: 'cryptography.hazmat.bindings._rust.x509.Certificat' object has no attribute '_x509'`**
  * Causa: Versión de `cryptography` demasiado moderna para `aiortc 1.2.1`.
  * Solución: Instala `cryptography==38.0.4`.
