import platform
import asyncio
from aiortc import VideoStreamTrack
from aiortc.contrib.media import MediaPlayer
from src.config import config

class LowLatencyVideoTrack(VideoStreamTrack):
    """
    Wrapper de VideoStreamTrack que elimina el lag acumulativo.
    Si el codificador de la Raspberry Pi va más lento que la cámara, 
    los frames se acumulan en la cola provocando segundos de retraso.
    Esta clase vacía la basura y entrega siempre el frame más reciente.
    """
    def __init__(self, track):
        super().__init__()
        self.kind = "video"
        self.track = track

    async def recv(self):
        # Si track._queue existe, vamos a vaciar los frames atascados en el pasado
        if hasattr(self.track, '_queue'):
            # Mientras haya más de 1 frame de retraso esperando...
            while self.track._queue.qsize() > 1:
                try:
                    # Sacamos el frame viejo y lo tiramos a la basura
                    self.track._queue.get_nowait()
                except asyncio.QueueEmpty:
                    break
        
        # Devolvemos el único frame reciente de la cola
        return await self.track.recv()

    def stop(self):
        """Asegura que al cerrar la conexión WebRTC, se libere la cámara hardware."""
        super().stop()
        if self.track and hasattr(self.track, 'stop'):
            self.track.stop()

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
            return LowLatencyVideoTrack(player.video)

        elif os_name == "Linux":
            # En Raspberry Pi OS Bullseye (Legacy), v4l2 funciona nativamente
            # y es mucho más rápido que usar OpenCV.
            print("[Video] Detectado Linux... Usando v4l2 nativo con MJPEG...")
            
            player = MediaPlayer('/dev/video0', format='v4l2', options={        
                'video_size': config.VIDEO_RESOLUTION,
                'framerate': str(config.VIDEO_FRAMERATE), # Prueba a subir tu config.py a 24 o 30
                'input_format': 'h264', # <- ¡LA MAGIA ES ESTO!
                'fflags': 'nobuffer',
                'flags': 'low_delay',
                'avioflags': 'direct'
            })
            
            print("[Video] Track de cámara (v4l2) creado correctamente.")
            # Track encapsulado para tirar frames si la Pi no procesa a tiempo
            return LowLatencyVideoTrack(player.video)

    except Exception as e:
        print(f"[Error Video] No se pudo inicializar la cámara: {e}")
        print("[Error Video] Asegúrate de tener una cámara conectada o permisos suficientes.")
        return None
