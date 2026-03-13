package com.i02bahip.c50brain

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.webrtc.*

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

    // Callback si quisieramos ver la camara localmente (opcional)
    var onLocalVideoTrackReady: ((VideoTrack) -> Unit)? = null

    init {
        initWebRTC()
        setupSignalingListeners()
    }

    private fun initWebRTC() {
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
        createLocalVideoTrack()
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
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onTrack(transceiver: RtpTransceiver) {}
        })

        if (localVideoTrack != null) {
            peerConnection?.addTrack(localVideoTrack, listOf("stream1"))
            Log.d(TAG, "Track de video local inyectado al PeerConnection")
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
                }, MediaConstraints())
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando Offer: ${e.message}")
            }
        }

        signalingClient.onIceCandidateReceived = { data ->
            try {
                val candidateObj = data.getJSONObject("candidate")
                val sdp = candidateObj.getString("candidate")
                val sdpMid = candidateObj.getString("sdpMid")
                val sdpMLineIndex = candidateObj.getInt("sdpMLineIndex")

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

