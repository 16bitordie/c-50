package com.i02bahip.c50android
import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.webrtc.*
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.os.Build

class WebrtcManager(
    private val context: Context,
    private val signalingClient: SignalingClient
) {
    private val TAG = "WebrtcManager"

    // Componentes principales de WebRTC
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    // Captura de audio
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    var dataChannel: DataChannel? = null

    // Configuración base (Servidores STUN/TURN de Google)
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:6a8tenisdemesa.com:3478").setUsername("c50_robot").setPassword("mypassword123C50").createIceServer(),
        PeerConnection.IceServer.builder("turn:6a8tenisdemesa.com:3478?transport=tcp").setUsername("c50_robot").setPassword("mypassword123C50").createIceServer()
    )

    // Callbacks para la Vista (UI)
    var onVideoTrackReceived: ((VideoTrack) -> Unit)? = null

    init {
        initWebRTC()
        setupSignalingListeners()
    }

        private fun setupAudioManager() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull { 
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER 
            }
            if (speakerDevice != null) {
                audioManager.setCommunicationDevice(speakerDevice)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        }
    }

    private fun initWebRTC() {
        setupAudioManager()
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

        createLocalAudioTrack()
        Log.d(TAG, "WebRTC inicializado correctamente.")
    }

    fun startCall() {
        createPeerConnection()

        val dcInit = DataChannel.Init()
        dataChannel = peerConnection?.createDataChannel("robot-control", dcInit)

        // Muy importante: Configurar transceptores ANTES de crear la oferta
        // Le decimos al Cerebro: "Queremos RECIBIR video, pero no te enviamos video"
        peerConnection?.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )

        createOffer()
    }

    fun sendCommand(command: String) {
        if (dataChannel?.state() == DataChannel.State.OPEN) {
            val buffer = java.nio.ByteBuffer.wrap(command.toByteArray())
            dataChannel?.send(DataChannel.Buffer(buffer, false))
            Log.d(TAG, "Comando enviado: $command")
        } else {
            Log.e(TAG, "No se pudo enviar $command. El DataChannel no está abierto. Estado: ${dataChannel?.state()}")
        }
    }

        private fun createLocalAudioTrack() {
        Log.d(TAG, "Configurando captura de audio de Piloto...")
        val audioConstraints = MediaConstraints()
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))

        localAudioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", localAudioSource)
        localAudioTrack?.setEnabled(false) // Por defecto silenciado (PTT)
        Log.d(TAG, "Track de audio piloto creado con exito.")
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
        Log.d(TAG, "Micrófono habilitado: $enabled")
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "Nuevo candidato ICE local generado: ${candidate.sdpMid}")
                val jsonCandidate = JSONObject().apply {
                    put("type", "candidate")
                    put("candidate", candidate.sdp)
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
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
        if (localAudioTrack != null) {
            peerConnection?.addTrack(localAudioTrack, listOf("stream1"))
        }
    }

    private fun createOffer() {
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
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
                // Compatibilidad con la estructura para que funcione siempre
                val sdp = candidateObj.getString("candidate")
                val sdpMid = if (candidateObj.has("sdpMid")) candidateObj.getString("sdpMid") else candidateObj.getString("id")
                val sdpMLineIndex = if (candidateObj.has("sdpMLineIndex")) candidateObj.getInt("sdpMLineIndex") else candidateObj.getInt("label")

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




