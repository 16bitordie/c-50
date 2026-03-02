#!/bin/bash

# ==============================================================================
# Script de Instalación Automática para C-50 (Raspberry Pi OS Bullseye)
# ==============================================================================
# Este script configura todo el entorno necesario para ejecutar el cerebro
# del robot C-50 desde cero en una instalación limpia de Raspberry Pi OS Bullseye.
# ==============================================================================

set -e # Detener el script si ocurre algún error

echo "🤖 Iniciando configuración del entorno para C-50..."

# 1. Actualizar el sistema
echo "📦 Actualizando repositorios del sistema..."
sudo apt-get update
# sudo apt-get upgrade -y # Opcional, descomentar si se desea actualizar todo el SO

# 2. Instalar dependencias del sistema (C/C++ y librerías multimedia)
echo "🛠️ Instalando dependencias del sistema (PyAV, NumPy, etc.)..."
sudo apt-get install -y \
    python3-venv \
    python3-dev \
    python3-av \
    python3-numpy \
    python3-cffi \
    libavdevice-dev \
    libavfilter-dev \
    libopus-dev \
    libvpx-dev \
    pkg-config \
    libsrtp2-dev

# 3. Asegurarse de que no hay conflictos con aiohttp del sistema
echo "🧹 Limpiando posibles conflictos de aiohttp..."
sudo apt-get remove -y python3-aiohttp || true

# 4. Crear el entorno virtual
echo "🐍 Creando entorno virtual de Python..."
# Borrar el entorno anterior si existe
if [ -d "venv" ]; then
    echo "   Borrando entorno virtual existente..."
    rm -rf venv
fi

# Crear el entorno heredando los paquetes del sistema (para usar python3-av y python3-numpy)
python3 -m venv --system-site-packages venv

# 5. Activar el entorno e instalar dependencias de Python
echo "📥 Instalando dependencias de Python en el entorno virtual..."
source venv/bin/activate

# Actualizar pip
pip install --upgrade pip setuptools wheel

# Instalar dependencias exactas (el orden es importante para evitar conflictos)
echo "   Instalando aiortc y dependencias base..."
pip install --no-deps aiortc==1.2.1
pip install "pyee>=8.1.0,<9.0.0" cryptography==38.0.4 pylibsrtp>=0.5.6 google-crc32c>=1.1.2 "aioice>=0.7.5,<0.8.0"

echo "   Instalando dependencias de red y señalización..."
pip install aiohttp==3.8.6 "python-socketio[asyncio_client]==5.7.2" python-engineio==4.3.4 websocket-client==1.9.0

echo "   Instalando dependencias de hardware y utilidades..."
pip install gpiozero==2.0 python-dotenv==1.0.0

# 6. Verificación final
echo "✅ Verificando instalación..."
python3 -c "import aiortc; import av; import socketio; import aiohttp; print('   ¡Todo funciona correctamente!')"

echo "=============================================================================="
echo "🎉 ¡Instalación completada con éxito!"
echo "=============================================================================="
echo "Para ejecutar el cerebro del robot, usa los siguientes comandos:"
echo "  source venv/bin/activate"
echo "  python3 -m src.main"
echo "=============================================================================="
