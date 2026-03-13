package com.i02bahip.c50android
import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.webrtc.*

class WebrtcManager(
    private val context: Context,
    private val signalingClient: SignalingClient
) {
    private val TAG = "WebrtcManager"

    // Componentes principales de WebRTC
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null

    // Configuración base (Servidores STUN/TURN de Google)
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        // Servidor TURN gratuito de Metered para perforar el 4G:
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer()
    )

    // Callbacks para la Vista (UI)
    var onVideoTrackReceived: ((VideoTrack) -> Unit)? = null

    init {
        initWebRTC()
        setupSignalingListeners()
    }

    private fun initWebRTC() {
        Log.d(TAG, "Inicializando motor WebRTC...")

        // 1. Inicializar el entorno global de WebRTC para Android
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        // 2. Crear la Factoría (Fábrica de conexiones)
        val factoryOptions = PeerConnectionFactory.Options()
        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(null, true, true)
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(null)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(factoryOptions)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()

        Log.d(TAG, "WebRTC inicializado correctamente.")
    }

    fun startCall() {
        createPeerConnection()

        // Muy importante: Configurar transceptores ANTES de crear la oferta
        // Le decimos a la Raspberry: "Queremos RECIBIR video, pero no te enviamos video"
        peerConnection?.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )

        createOffer()
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "Nuevo candidato ICE local generado: ${candidate.sdpMid}")
                val jsonCandidate = JSONObject().apply {
                    put("type", "candidate")
                    put("candidate", candidate.sdp)
                    // Las claves en socket.io/aiortc suelen llamarse sdpMid y sdpMLineIndex
                    put("id", candidate.sdpMid)
                    put("label", candidate.sdpMLineIndex)
                }
                val jsonPayload = JSONObject().apply { put("roomId", "c-50-room"); put("candidate", jsonCandidate) } ; signalingClient.sendIceCandidate(jsonPayload)
            }

            override fun onTrack(transceiver: RtpTransceiver) {
                Log.d(TAG, "¡Track recibido de la Raspberry!")
                if (transceiver.receiver.track() is VideoTrack) {
                    val videoTrack = transceiver.receiver.track() as VideoTrack
                    onVideoTrackReceived?.invoke(videoTrack)
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "Estado de conexión ICE: $state")
            }

            // --- Métodos obligatorios de la interfaz (los dejamos vacíos por ahora) ---
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })
    }

    private fun createOffer() {
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d(TAG, "Oferta SDP creada. Configurando Local Description...")
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sessionDescription)

                // Enviar la oferta a la Raspberry Pi
                val jsonPayload = JSONObject().apply {
                    put("roomId", "c-50-room")
                    val offerData = JSONObject().apply {
                        put("type", "offer")
                        put("sdp", sessionDescription.description)
                    }
                    put("offer", offerData)
                }
                signalingClient.sendOffer(jsonPayload)
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(reason: String?) { Log.e(TAG, "Fallo al crear oferta: $reason") }
            override fun onSetFailure(reason: String?) {}
        }, mediaConstraints)
    }

    private fun setupSignalingListeners() {
        signalingClient.onAnswerReceived = { data ->
            Log.d(TAG, "Procesando Respuesta (Answer)...")
            try {
                val sdpData = data.getJSONObject("answer")
                val sdpString = sdpData.getString("sdp")
                val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdpString)

                peerConnection?.setRemoteDescription(SimpleSdpObserver(), sessionDescription)
            } catch (e: Exception) {
                Log.e(TAG, "Error parseando Answer: ${e.message}")
            }
        }

        signalingClient.onIceCandidateReceived = { data ->
            try {
                val candidateObj = data.getJSONObject("candidate")
                // Compatibilidad con la estructura que envía aiortc
                val sdp = candidateObj.getString("candidate")
                val sdpMid = candidateObj.getString("sdpMid")
                val sdpMLineIndex = candidateObj.getInt("sdpMLineIndex")

                val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                peerConnection?.addIceCandidate(iceCandidate)
                Log.d(TAG, "Candidato ICE remoto añadido")
            } catch (e: Exception) {
                Log.e(TAG, "Error parseando ICE Candidate: ${e.message}")
            }
        }
    }

    fun onDestroy() {
        peerConnection?.close()
        peerConnectionFactory.dispose()
    }
}

// Clase auxiliar para no tener que implementar todos los métodos vacíos todo el tiempo
open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
