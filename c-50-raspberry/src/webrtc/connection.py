import asyncio
import socketio
from aiortc import RTCPeerConnection, RTCSessionDescription, RTCIceCandidate, RTCConfiguration, RTCIceServer
from aiortc.sdp import candidate_from_sdp
from src.config import config
from src.webrtc.media.camera import create_video_track

# Inicializar cliente de Socket.io
sio = socketio.AsyncClient()

# Variables globales para WebRTC
pc = None
data_channel = None
ice_candidates_buffer = []

async def setup_webrtc():
    """Configura la conexión WebRTC (PeerConnection)."""
    global pc

    # Configurar servidores STUN (¡Vital para salir de la red local!)
    config = RTCConfiguration(
        iceServers=[
            RTCIceServer(urls=["stun:stun.l.google.com:19302"])
        ]
    )
    
    # Crear la conexión P2P con el servidor STUN añadido
    pc = RTCPeerConnection(configuration=config)
    print("[WebRTC] PeerConnection creada con servidor STUN.")

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
    
    global pc, ice_candidates_buffer
    ice_candidates_buffer = []  # Limpia el buffer
    
    if pc is None:
        await setup_webrtc()

    offer = RTCSessionDescription(sdp=data['offer']['sdp'], type=data['offer']['type'])
    await pc.setRemoteDescription(offer)
    print("[WebRTC] Remote Description (Offer) aplicada.")

    answer = await pc.createAnswer()
    await pc.setLocalDescription(answer)
    print("[WebRTC] Local Description (Answer) creada.")

    print(f"[Debug] Enviando Answer al Room ID: {config.ROOM_ID}")
    await sio.emit('answer', {
        'roomId': config.ROOM_ID,
        'answer': {'type': pc.localDescription.type, 'sdp': pc.localDescription.sdp}
    })
    print("[Signaling] Respuesta enviada.")

    # AHORA que ya tenemos Answer, inyectamos todos los ICE acumulados
    for cand in ice_candidates_buffer:
        try:
            await pc.addIceCandidate(cand)
            print("[WebRTC] Candidato ICE buffer inyectado.")
        except:
            pass
    ice_candidates_buffer = [] # Vaciamos

@sio.on('ice-candidate')
async def on_ice_candidate(data):
    """Recibe candidatos ICE de la App Android y los procesa."""
    global pc, ice_candidates_buffer
    
    try:
        cand_data = data['candidate']
        m_id = cand_data.get('sdpMid') or cand_data.get('id')
        m_index = cand_data.get('sdpMLineIndex')
        if m_index is None: m_index = cand_data.get('label', 0)
        
        # Parsear el candidato real desde el string SDP que envía Android
        candidate_str = cand_data.get('candidate')
        if not candidate_str:
            return
            
        candidate = candidate_from_sdp(candidate_str)
        candidate.sdpMid = m_id
        candidate.sdpMLineIndex = m_index

        # Si no hemos enviado Answer aún, lo guardamos para luego
        if pc is None or pc.localDescription is None:
            ice_candidates_buffer.append(candidate)
            print("[WebRTC] ICE encolado (esperando Answer).")
        else:
            await pc.addIceCandidate(candidate)
            print("[WebRTC] Candidato ICE inyectado directo.")
    except Exception as e:
        print(f"[WebRTC] Error con ICE: {e}")

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
