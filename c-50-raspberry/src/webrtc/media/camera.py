import platform
from aiortc.contrib.media import MediaPlayer
from src.config import config

def create_video_track():
    """
    Crea y devuelve un track de video capturado desde la cámara.
    Maneja las diferencias entre Windows (para pruebas) y Linux (Raspberry Pi).
    """
    os_name = platform.system()
    
    try:
        if os_name == "Windows":
            # En Windows, usamos DirectShow (dshow) para acceder a la webcam por defecto
            print("[Video] Detectado Windows. Intentando abrir webcam por defecto (video=video0)...")
            # 'video=Integrated Camera' o 'video=USB Video Device' suele ser el nombre, 
            # pero 'video=0' o 'video=default' a veces funciona dependiendo de ffmpeg.
            # Para mayor compatibilidad en pruebas de Windows, usamos un dispositivo genérico.
            player = MediaPlayer('video=Integrated Camera', format='dshow', options={
                'video_size': config.VIDEO_RESOLUTION,
                'framerate': str(config.VIDEO_FRAMERATE)
            })
            
        elif os_name == "Linux":
            # En Raspberry Pi OS Bookworm con cámara CSI, v4l2 no funciona directamente.
            print("[Video] Detectado Linux (Raspberry Pi Bookworm).")
            
            # La solución definitiva para Bookworm sin libcamerify es usar el dispositivo
            # de video que libcamera expone para compatibilidad v4l2.
            # Según v4l2-ctl --list-devices, la cámara está en /dev/video0
            
            # Vamos a intentar la opción más compatible para /dev/video0 en Bookworm:
            # Forzar el formato a YUYV422 que es el que vimos que soporta nativamente
            options = {
                'video_size': config.VIDEO_RESOLUTION,
                'framerate': str(config.VIDEO_FRAMERATE),
                'pixel_format': 'yuyv422'
            }
            
            try:
                print("[Video] Intentando abrir /dev/video0 con formato yuyv422...")
                player = MediaPlayer('/dev/video0', format='v4l2', options=options)
            except Exception as e:
                print(f"[Video] Falló /dev/video0 con yuyv422: {e}")
                print("[Video] Intentando abrir /dev/video0 sin opciones estrictas...")
                player = MediaPlayer('/dev/video0', format='v4l2')
            
        else:
            print(f"[Video] SO no soportado para captura directa: {os_name}")
            return None

        print("[Video] Track de cámara creado correctamente.")
        return player.video

    except Exception as e:
        print(f"[Error Video] No se pudo inicializar la cámara: {e}")
        print("[Error Video] Asegúrate de tener una cámara conectada o permisos suficientes.")
        return None
