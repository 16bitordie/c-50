package com.i02bahip.c50android
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class MainActivity : ComponentActivity() {

    // Instancias de nuestros gestores de conexión
    private lateinit var signalingClient: SignalingClient
    private lateinit var webrtcManager: WebrtcManager

    // EglBase es el motor gráfico interno de WebRTC
    private val eglBase = EglBase.create()

    // Variable de estado de Compose. Cuando reciba una pista de vídeo (VideoTrack),
    // la interfaz se actualizará automáticamente.
    private var remoteVideoTrack by mutableStateOf<VideoTrack?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Mantener la pantalla siempre encendida mientras la app esté abierta
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 1. Inicializamos el Signaling Client con los datos correctos
        signalingClient = SignalingClient(
            url = "https://6a8tenisdemesa.com",
            roomName = "c-50-room"
        )

        // 2. Inicializamos el WebRTC Manager pasándole contexto y el signaling
        webrtcManager = WebrtcManager(
            context = this,
            signalingClient = signalingClient
        )

        // Asignamos el callback para la UI (como variable, no en constructor)
        webrtcManager.onVideoTrackReceived = { track ->
            // ¡Magia! Cuando el Pi envíe el vídeo, se asigna aquí y Compose redibuja
            remoteVideoTrack = track
        }

        // 3. Conectamos al servidor de Signaling y cuando estemos dentro de la sala, llamamos
        signalingClient.onConnected = {
            webrtcManager.startCall()
        }
        signalingClient.connect()

        // 4. Mostramos la interfaz de usuario
        setContent {
            // Un Composable personalizado que envuelve el reproductor de WebRTC
            C50VideoRenderer(
                videoTrack = remoteVideoTrack,
                eglBaseContext = eglBase.eglBaseContext
            )
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        // Limpiamos los recursos para que no se quede la red o vista pillada
        signalingClient.disconnect()
        // webrtcManager.disconnect() <-- ¡Bórrala! La implementaremos luego
        eglBase.release()
    }
}

@Composable
fun C50VideoRenderer(videoTrack: VideoTrack?, eglBaseContext: EglBase.Context) {
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                // Inicializamos el renderizador con el motor gráfico
                init(eglBaseContext, null)
                setEnableHardwareScaler(true)
                setMirror(false) // En el robot no queremos efecto espejo
            }
        },
        modifier = Modifier.fillMaxSize(), // Que ocupe toda la pantalla
        update = { view ->
            // Si el videoTrack ya no es nulo (nos llegó desde la Pi), lo inyectamos a la vista
            videoTrack?.addSink(view)
        }
    )
}
