import platform
from aiortc.contrib.media import MediaPlayer
from src.config import config

def create_video_track():
    """
    Crea y devuelve un track de video capturado desde la cámara.
    Maneja las diferencias entre Windows (para pruebas) y Linux (Raspberry Pi Bullseye).
    """
    os_name = platform.system()
    
    try:
        if os_name == "Windows":
            print("[Video] Detectado Windows. Intentando abrir webcam por defecto...")
            player = MediaPlayer('video=Integrated Camera', format='dshow', options={
                'video_size': config.VIDEO_RESOLUTION,
                'framerate': str(config.VIDEO_FRAMERATE)
            })
            return player.video
            
        elif os_name == "Linux":
            # En Raspberry Pi OS Bullseye (Legacy), v4l2 funciona nativamente
            # y es mucho más rápido que usar OpenCV.
            print("[Video] Detectado Linux (Raspberry Pi Bullseye). Usando v4l2 nativo...")
            
            # Usamos MediaPlayer directamente apuntando a /dev/video0
            player = MediaPlayer('/dev/video0', format='v4l2', options={
                'video_size': config.VIDEO_RESOLUTION,
                'framerate': str(config.VIDEO_FRAMERATE)
            })
            
            print("[Video] Track de cámara (v4l2) creado correctamente.")
            return player.video
            
    except Exception as e:
        print(f"[Error Video] No se pudo inicializar la cámara: {e}")
        print("[Error Video] Asegúrate de tener una cámara conectada o permisos suficientes.")
        return None
