package com.i02bahip.c50brain

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.webrtc.*
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.os.Build

class WebrtcManager(
    private val context: Context,
    private val signalingClient: SignalingClient,
    private val rootEglBase: EglBase
) {
    private val TAG = "C50-Brain-Webrtc"

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    
    // Captura de video
    private var videoCapturer: VideoCapturer? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    // Captura de audio
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:6a8tenisdemesa.com:3478").setUsername("c50_robot").setPassword("mypassword123C50").createIceServer(),
        PeerConnection.IceServer.builder("turn:6a8tenisdemesa.com:3478?transport=tcp").setUsername("c50_robot").setPassword("mypassword123C50").createIceServer()
    )

    // Callback si quisieramos ver la camara localmente (opcional)
    var onLocalVideoTrackReady: ((VideoTrack) -> Unit)? = null
    // Callback para enviar informacion a la UI
    var onConnectionInfoChanged: ((String) -> Unit)? = null
    // Callback para recibir comandos del cerebro
    var onCommandReceived: ((String) -> Unit)? = null

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
        Log.d(TAG, "Inicializando motor WebRTC en modo Cerebro...")

        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)

        val factoryOptions = PeerConnectionFactory.Options()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(factoryOptions)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()
            
        Log.d(TAG, "WebRTC inicializado correctamente.")
        createLocalAudioTrack()
        createLocalVideoTrack()
    }

        private fun createLocalAudioTrack() {
        Log.d(TAG, "Configurando captura de audio...")
        val audioConstraints = MediaConstraints()
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))

        localAudioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", localAudioSource)
        Log.d(TAG, "Track de audio local creado con exito.")
    }

    private fun createLocalVideoTrack() {
        Log.d(TAG, "Configurando captura de c�mara...")
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        var cameraDeviceName: String? = null

        // Buscamos la c�mara trasera
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                cameraDeviceName = deviceName
                break
            }
        }
        // Si no hay trasera, pillamos cualquiera (delantera)
        if (cameraDeviceName == null) cameraDeviceName = deviceNames.firstOrNull()

        if (cameraDeviceName != null) {
            videoCapturer = enumerator.createCapturer(cameraDeviceName, null)
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
            localVideoSource = peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)
            videoCapturer!!.initialize(surfaceTextureHelper, context, localVideoSource!!.capturerObserver)
            
            // Iniciar captura a resolucion bajita/media como haciamos con la Raspberry
            // Ej: 640x480 a 30fps
            videoCapturer!!.startCapture(640, 480, 30)

            localVideoTrack = peerConnectionFactory.createVideoTrack("100", localVideoSource)
            onLocalVideoTrackReady?.invoke(localVideoTrack!!)
            
            Log.d(TAG, "Track de video local creado con exito.")
        } else {
            Log.e(TAG, "No se encontro ninguna camara en este dispositivo.")
        }
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
                    put("candidate", candidate.sdp)
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("id", candidate.sdpMid)
                    put("label", candidate.sdpMLineIndex)
                }
                val jsonPayload = JSONObject().apply { put("roomId", "c-50-room"); put("candidate", jsonCandidate) }
                signalingClient.sendIceCandidate(jsonPayload)
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "Estado de conexion ICE: $state")
                if (state == PeerConnection.IceConnectionState.CONNECTED || state == PeerConnection.IceConnectionState.COMPLETED) {
                    peerConnection?.getStats { report ->
                        var localType = "unknown"
                        var remoteType = "unknown"
                        var isRelay = false
                        
                        for ((id, rtcStat) in report.statsMap) {
                            if (rtcStat.type == "candidate-pair" && rtcStat.members["state"] == "succeeded") {
                                val localId = rtcStat.members["localCandidateId"] as? String
                                val remoteId = rtcStat.members["remoteCandidateId"] as? String
                                
                                if (localId != null) {
                                    localType = report.statsMap[localId]?.members?.get("candidateType") as? String ?: "unknown"
                                }
                                if (remoteId != null) {
                                    remoteType = report.statsMap[remoteId]?.members?.get("candidateType") as? String ?: "unknown"
                                }
                                
                                if (localType == "relay" || remoteType == "relay") {
                                    isRelay = true
                                }
                                break
                            }
                        }
                        
                        val protocol = if (isRelay) "TURN (Seguro/4G)" else "STUN/Directo"
                        onConnectionInfoChanged?.invoke("Estado: VÍDEO ACTIVO\nRed: $protocol\n-> L:$localType R:$remoteType")
                    }
                } else {
                    onConnectionInfoChanged?.invoke("Estado ICE: $state")
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {
                dataChannel?.registerObserver(object: DataChannel.Observer {
                    override fun onBufferedAmountChange(p0: Long) {}
                    override fun onStateChange() {}
                    override fun onMessage(buffer: DataChannel.Buffer) {
                        val data = ByteArray(buffer.data.remaining())
                        buffer.data.get(data)
                        val command = String(data)
                        onCommandReceived?.invoke(command)
                        Log.d(TAG, "Mando dice: $command")
                    }
                })
            }
            override fun onRenegotiationNeeded() {}
            override fun onTrack(transceiver: RtpTransceiver) {}
        })

        if (localVideoTrack != null) {
            peerConnection?.addTrack(localVideoTrack, listOf("stream1"))
            Log.d(TAG, "Track de video local inyectado al PeerConnection")
        }

        if (localAudioTrack != null) {
            peerConnection?.addTrack(localAudioTrack, listOf("stream1"))
            Log.d(TAG, "Track de audio local inyectado al PeerConnection")
        }
    }

    private fun setupSignalingListeners() {
        signalingClient.onOfferReceived = { data ->
            try {
                Log.d(TAG, "Procesando oferta recibida del piloto...")
                if (peerConnection == null) {
                    createPeerConnection()
                }

                val offerData = data.getJSONObject("offer")
                val sdpString = offerData.getString("sdp")
                val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdpString)

                peerConnection?.setRemoteDescription(SimpleSdpObserver(), sessionDescription)

                val answerConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                }

                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(sessionDescription: SessionDescription) {
                        peerConnection?.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                        Log.d(TAG, "Enviando respuesta al piloto...")

                        val jsonPayload = JSONObject().apply {
                            put("roomId", "c-50-room")
                            val answerData = JSONObject().apply {
                                put("type", "answer")
                                put("sdp", sessionDescription.description)
                            }
                            put("answer", answerData)
                        }
                        signalingClient.sendAnswer(jsonPayload)
                    }

                    override fun onSetSuccess() {}
                    override fun onCreateFailure(reason: String?) { Log.e(TAG, "Fallo al crear answer: $reason") }
                    override fun onSetFailure(reason: String?) {}
                }, answerConstraints)
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando Offer: ${e.message}")
            }
        }

        signalingClient.onIceCandidateReceived = { data ->
            try {
                val candidateObj = data.getJSONObject("candidate")
                val sdp = candidateObj.getString("candidate")
                val sdpMid = if (candidateObj.has("sdpMid")) candidateObj.getString("sdpMid") else candidateObj.getString("id")
                val sdpMLineIndex = if (candidateObj.has("sdpMLineIndex")) candidateObj.getInt("sdpMLineIndex") else candidateObj.getInt("label")

                val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                peerConnection?.addIceCandidate(iceCandidate)
                Log.d(TAG, "Candidato ICE piloto agregado")
            } catch (e: Exception) {
                Log.e(TAG, "Error parseando ICE Candidate del piloto: ${e.message}")
            }
        }
    }

    fun onDestroy() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        peerConnection?.close()
        peerConnectionFactory.dispose()
        surfaceTextureHelper?.dispose()
    }
}

// Clase auxiliar
open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}






