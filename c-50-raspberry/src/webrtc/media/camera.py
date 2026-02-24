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
            # La forma más robusta de integrar libcamera con aiortc es usar un pipeline de GStreamer
            print("[Video] Detectado Linux (Raspberry Pi Bookworm). Usando pipeline de GStreamer...")
            
            # Construimos un pipeline de GStreamer que usa libcamerasrc
            # y lo convierte a un formato que aiortc (ffmpeg) pueda entender.
            pipeline = (
                f"libcamerasrc ! "
                f"video/x-raw,width={config.VIDEO_RESOLUTION.split('x')[0]},height={config.VIDEO_RESOLUTION.split('x')[1]},framerate={config.VIDEO_FRAMERATE}/1 ! "
                f"videoconvert ! appsink"
            )
            
            # aiortc no soporta GStreamer pipelines directamente en MediaPlayer.
            # La alternativa real en Bookworm para aiortc es usar el wrapper libcamerify
            # o forzar el formato de pixel a algo muy básico en /dev/video0.
            
            # Vamos a intentar la opción más compatible para /dev/video0 en Bookworm:
            # Forzar el formato a YUV420P que es el más universal para ffmpeg
            options = {
                'video_size': config.VIDEO_RESOLUTION,
                'framerate': str(config.VIDEO_FRAMERATE),
                'pixel_format': 'yuv420p'
            }
            
            try:
                print("[Video] Intentando abrir /dev/video0 con formato yuv420p...")
                player = MediaPlayer('/dev/video0', format='v4l2', options=options)
            except Exception as e:
                print(f"[Video] Falló /dev/video0 con yuv420p: {e}")
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
