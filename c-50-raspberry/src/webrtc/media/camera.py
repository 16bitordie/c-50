import platform
import asyncio
import cv2
from aiortc import VideoStreamTrack
from av import VideoFrame
from src.config import config

class OpenCVVideoTrack(VideoStreamTrack):
    """
    Un track de video personalizado para aiortc que usa OpenCV para capturar
    los frames de la cámara. Esto soluciona los problemas de compatibilidad
    de ffmpeg/v4l2 con libcamera en Raspberry Pi OS Bookworm.
    """
    
    # aiortc necesita saber qué tipo de track es este
    kind = "video"
    
    def __init__(self, camera_index=0):
        super().__init__()  # No olvides llamar al constructor padre
        self.camera_index = camera_index
        self.cap = cv2.VideoCapture(self.camera_index)
        
        # Configurar resolución
        width, height = map(int, config.VIDEO_RESOLUTION.split('x'))
        self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, width)
        self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, height)
        self.cap.set(cv2.CAP_PROP_FPS, config.VIDEO_FRAMERATE)
        
        if not self.cap.isOpened():
            raise Exception(f"No se pudo abrir la cámara con índice {self.camera_index}")

    async def recv(self):
        """
        Este método es llamado por aiortc cada vez que necesita un nuevo frame.
        """
        pts, time_base = await self.next_timestamp()

        # Leer frame de OpenCV (esto es bloqueante, pero en cámaras suele ser rápido)
        # Para hacerlo 100% asíncrono habría que usar un hilo separado, pero para
        # empezar esto funcionará.
        ret, frame = self.cap.read()
        if not ret:
            raise Exception("No se pudo leer el frame de la cámara")

        # OpenCV usa BGR por defecto, aiortc espera BGR24 o YUV
        # Convertimos el frame de numpy array a un objeto VideoFrame de av
        video_frame = VideoFrame.from_ndarray(frame, format="bgr24")
        video_frame.pts = pts
        video_frame.time_base = time_base

        return video_frame

    def stop(self):
        """Libera la cámara cuando se cierra la conexión."""
        super().stop()
        if self.cap is not None:
            self.cap.release()

def create_video_track():
    """
    Crea y devuelve un track de video capturado desde la cámara.
    Maneja las diferencias entre Windows (para pruebas) y Linux (Raspberry Pi).
    """
    os_name = platform.system()
    
    try:
        if os_name == "Windows":
            # En Windows, seguimos usando MediaPlayer por simplicidad
            from aiortc.contrib.media import MediaPlayer
            print("[Video] Detectado Windows. Intentando abrir webcam por defecto...")
            player = MediaPlayer('video=Integrated Camera', format='dshow', options={
                'video_size': config.VIDEO_RESOLUTION,
                'framerate': str(config.VIDEO_FRAMERATE)
            })
            return player.video
            
        elif os_name == "Linux":
            # En Raspberry Pi OS Bookworm, usamos nuestro track personalizado con OpenCV
            print("[Video] Detectado Linux (Raspberry Pi Bookworm). Usando OpenCV...")
            
            # El índice 0 suele corresponder a /dev/video0
            track = OpenCVVideoTrack(camera_index=0)
            print("[Video] Track de cámara (OpenCV) creado correctamente.")
            return track
    except Exception as e:
        print(f"[Error Video] No se pudo inicializar la cámara: {e}")
        print("[Error Video] Asegúrate de tener una cámara conectada o permisos suficientes.")
        return None
