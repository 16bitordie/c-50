import asyncio
from src.webrtc.connection import start_signaling

async def main():
    """Punto de entrada principal para el cerebro de C-50."""
    print("==================================================")
    print("🤖 Iniciando C-50 (Cerebro Raspberry Pi)")
    print("==================================================")
    
    # Iniciar la conexión de señalización y WebRTC
    await start_signaling()

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[C-50] Apagando sistema...")
