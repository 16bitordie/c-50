import asyncio
import socketio
from aiortc import RTCPeerConnection, RTCSessionDescription, RTCIceCandidate
from src.config import config
from src.webrtc.media.camera import create_video_track

# Inicializar cliente de Socket.io
sio = socketio.AsyncClient()

# Variables globales para WebRTC
pc = None
data_channel = None

async def setup_webrtc():
    """Configura la conexión WebRTC (PeerConnection)."""
    global pc
    
    # Crear la conexión P2P
    pc = RTCPeerConnection()
    print("[WebRTC] PeerConnection creada.")

    # Añadir el track de video de la cámara a la conexión
    video_track = create_video_track()
    if video_track:
        pc.addTrack(video_track)
        print("[WebRTC] Track de video añadido a la conexión.")
    else:
        print("[WebRTC] Advertencia: No se pudo añadir el track de video.")

    # Manejar el estado de la conexión
    @pc.on("connectionstatechange")
    async def on_connectionstatechange():
        print(f"[WebRTC] Estado de conexión: {pc.connectionState}")
        if pc.connectionState == "failed":
            await pc.close()

    # Manejar la recepción de Data Channels (desde la App Android)
    @pc.on("datachannel")
    def on_datachannel(channel):
        global data_channel
        data_channel = channel
        print(f"[WebRTC] Data Channel recibido: {channel.label}")

        @channel.on("message")
        def on_message(message):
            print(f"[DataChannel] Mensaje recibido: {message}")
            # Aquí conectaremos con el control de motores más adelante
            # ej: if message == "ADELANTE": mover_motores()

# ============================================================================
# EVENTOS DE SOCKET.IO (SEÑALIZACIÓN)
# ============================================================================

@sio.event
async def connect():
    print(f"[Signaling] Conectado al servidor: {config.SIGNALING_SERVER_URL}")
    await sio.emit('join-room', config.ROOM_ID)
    print(f"[Signaling] Unido a la sala: {config.ROOM_ID}")

@sio.event
async def disconnect():
    print("[Signaling] Desconectado del servidor.")

@sio.on('offer')
async def on_offer(data):
    """Recibe una oferta de la App Android y responde."""
    print(f"\n[Signaling] Oferta recibida de: {data['senderId']}")
    
    if pc is None:
        await setup_webrtc()

    # Aplicar la oferta remota
    offer = RTCSessionDescription(sdp=data['offer']['sdp'], type=data['offer']['type'])
    await pc.setRemoteDescription(offer)
    print("[WebRTC] Remote Description (Offer) aplicada.")

    # Crear la respuesta
    answer = await pc.createAnswer()
    await pc.setLocalDescription(answer)
    print("[WebRTC] Local Description (Answer) creada.")

    # Enviar la respuesta de vuelta a la App Android
    await sio.emit('answer', {
        'roomId': config.ROOM_ID,
        'answer': {'type': pc.localDescription.type, 'sdp': pc.localDescription.sdp}
    })
    print("[Signaling] Respuesta enviada.")

@sio.on('ice-candidate')
async def on_ice_candidate(data):
    """Recibe candidatos ICE de la App Android."""
    print(f"[Signaling] Candidato ICE recibido de: {data['senderId']}")

    global pc
    if pc is not None:
        try:
            cand_data = data['candidate']
            
            # Extraemos los datos usando las claves de Android ('id' y 'label') o las estandar ('sdpMid')
            m_id = cand_data.get('sdpMid') or cand_data.get('id')
            m_index = cand_data.get('sdpMLineIndex')
            if m_index is None:
                 m_index = cand_data.get('label', 0)
                 
            candidate = RTCIceCandidate(
                component=m_index,
                foundation=0,
                ip="0.0.0.0", 
                port=0,
                priority=0,
                protocol="udp",
                type="host",
                sdpMid=m_id
            )
            candidate.sdpMid = m_id
            candidate.sdpMLineIndex = m_index
            if hasattr(candidate, 'candidate'):
                candidate.candidate = cand_data.get('candidate')
            
            try:
                await pc.addIceCandidate(candidate)
                print("[WebRTC] Candidato ICE inyectado en Pi.")
            except AttributeError:
                pass
        except Exception as e:
            print(f"[WebRTC] Error inyectando ICE: {e}")

async def start_signaling():
    """Inicia la conexión con el servidor de señalización."""
    try:
        await sio.connect(config.SIGNALING_SERVER_URL)
        await sio.wait()
    except Exception as e:
        print(f"[Error] No se pudo conectar al servidor de señalización: {e}")

if __name__ == "__main__":
    # Para probar este archivo de forma independiente
    asyncio.run(start_signaling())
