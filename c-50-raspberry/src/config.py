import os
from dotenv import load_dotenv

# Cargar variables de entorno desde el archivo .env
load_dotenv()

class Config:
    """Configuración centralizada para C-50."""
    
    # Señalización
    SIGNALING_SERVER_URL = os.getenv("SIGNALING_SERVER_URL", "https://6a8tenisdemesa.com")
    ROOM_ID = os.getenv("ROOM_ID", "c-50-room")
    
    # Video
    VIDEO_RESOLUTION = os.getenv("VIDEO_RESOLUTION", "640x480")
    VIDEO_FRAMERATE = int(os.getenv("VIDEO_FRAMERATE", 30))
    
    # Hardware (Pines GPIO)
    MOTOR_LEFT_FORWARD = int(os.getenv("MOTOR_LEFT_FORWARD", 17))
    MOTOR_LEFT_BACKWARD = int(os.getenv("MOTOR_LEFT_BACKWARD", 27))
    MOTOR_RIGHT_FORWARD = int(os.getenv("MOTOR_RIGHT_FORWARD", 22))
    MOTOR_RIGHT_BACKWARD = int(os.getenv("MOTOR_RIGHT_BACKWARD", 23))

config = Config()
