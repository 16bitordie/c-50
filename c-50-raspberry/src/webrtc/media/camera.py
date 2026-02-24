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
            # Usamos un comando de consola (libcamera-vid) y lo pasamos a aiortc.
            print("[Video] Detectado Linux (Raspberry Pi Bookworm). Usando libcamera-vid...")
            
            # libcamera-vid captura el video, lo codifica en h264 y lo envía por stdout (-)
            # aiortc (ffmpeg) lee ese stdout como si fuera un archivo.
            cmd = (
                f"libcamera-vid -t 0 --inline --width {config.VIDEO_RESOLUTION.split('x')[0]} "
                f"--height {config.VIDEO_RESOLUTION.split('x')[1]} --framerate {config.VIDEO_FRAMERATE} "
                f"--codec h264 -o -"
            )
            
            player = MediaPlayer(cmd, format='h264', options={})
            
        else:
            print(f"[Video] SO no soportado para captura directa: {os_name}")
            return None

        print("[Video] Track de cámara creado correctamente.")
        return player.video

    except Exception as e:
        print(f"[Error Video] No se pudo inicializar la cámara: {e}")
        print("[Error Video] Asegúrate de tener una cámara conectada o permisos suficientes.")
        return None
