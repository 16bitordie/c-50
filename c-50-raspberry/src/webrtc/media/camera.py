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
            # Usamos libcamerasrc a través de GStreamer (que ffmpeg/aiortc puede leer)
            print("[Video] Detectado Linux (Raspberry Pi Bookworm). Usando libcamerasrc...")
            
            # En lugar de ejecutar un comando de consola (que aiortc interpreta como archivo),
            # le decimos a aiortc que use el dispositivo virtual de libcamera si existe,
            # o que use un pipeline de GStreamer.
            # Para aiortc, la forma más limpia en Bookworm es usar el wrapper v4l2 de libcamera
            # que se activa precargando una librería, pero desde Python es complejo.
            
            # Vamos a intentar usar el dispositivo de video que libcamera expone (suele ser video0 o video1)
            # pero forzando el formato a h264 o mjpeg que son más compatibles.
            options = {
                'video_size': config.VIDEO_RESOLUTION,
                'framerate': str(config.VIDEO_FRAMERATE)
            }
            
            # Intentamos abrir /dev/video0 (que libcamera a veces emula)
            # Si falla, intentamos con /dev/video11 (que a veces es el nodo de hardware en Bookworm)
            try:
                player = MediaPlayer('/dev/video0', format='v4l2', options=options)
            except Exception as e:
                print(f"[Video] Falló /dev/video0: {e}. Intentando /dev/video11...")
                player = MediaPlayer('/dev/video11', format='v4l2', options=options)
            
        else:
            print(f"[Video] SO no soportado para captura directa: {os_name}")
            return None

        print("[Video] Track de cámara creado correctamente.")
        return player.video

    except Exception as e:
        print(f"[Error Video] No se pudo inicializar la cámara: {e}")
        print("[Error Video] Asegúrate de tener una cámara conectada o permisos suficientes.")
        return None
