package com.i02bahip.c50android
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier 
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.roundToInt
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
            // Un Composable principal que envuelve el video y los controles
            Box(modifier = Modifier.fillMaxSize()) {
                C50VideoRenderer(
                    videoTrack = remoteVideoTrack,
                    eglBaseContext = eglBase.eglBaseContext
                )
                
                // Controles superpuestos (Joystick a la izquierda)
                AnalogJoystick(
                    modifier = Modifier.align(Alignment.BottomStart).padding(32.dp),
                    onCommand = { cmd -> webrtcManager.sendCommand(cmd) }
                )
            }
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

@Composable
fun AnalogJoystick(modifier: Modifier = Modifier, onCommand: (String) -> Unit) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val maxRadius = 150f // Límite del joystick

    Box(
        modifier = modifier
            .size(150.dp)
            .background(Color.Gray.copy(alpha = 0.4f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        // Vuelve al centro al soltar
                        offsetX = 0f
                        offsetY = 0f
                        onCommand("JOY:0,0")
                    }
                ) { change, dragAmount ->
                    change.consume()
                    var newX = offsetX + dragAmount.x
                    var newY = offsetY + dragAmount.y
                    
                    val distance = sqrt(newX.pow(2) + newY.pow(2))
                    
                    // Restringir el círculo interno para que no se salga de su base
                    if (distance > maxRadius) {
                        newX = (newX / distance) * maxRadius
                        newY = (newY / distance) * maxRadius
                    }
                    
                    offsetX = newX
                    offsetY = newY

                    // Convertir el desplazamiento a valores analógicos de -100 a +100
                    // Invertimos el eje Y para que "hacia arriba" sea positivo
                    val xPercent = (offsetX / maxRadius * 100).toInt()
                    val yPercent = (-offsetY / maxRadius * 100).toInt()
                    
                    onCommand("JOY:$xPercent,$yPercent")
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // El "palo" del joystick (círculo interno)
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(60.dp)
                .background(Color.DarkGray, CircleShape)
        )
    }
}
