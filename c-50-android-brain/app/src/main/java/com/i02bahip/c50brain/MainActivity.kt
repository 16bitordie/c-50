package com.i02bahip.c50brain

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class MainActivity : ComponentActivity() {

    private lateinit var signalingClient: SignalingClient
    private lateinit var webrtcManager: WebrtcManager
    private val eglBase = EglBase.create()

    private var localVideoTrack by mutableStateOf<VideoTrack?>(null)
    private var connectionInfoText by mutableStateOf("Estado: Esperando piloto...")
    private var lastCommandText by mutableStateOf("Comando: NINGUNO")

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        if (cameraGranted) {
            startRobotBrain()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            BrainScreen(
                videoTrack = localVideoTrack, 
                eglBaseContext = eglBase.eglBaseContext,
                connectionInfo = connectionInfoText,
                lastCommand = lastCommandText
            )
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        // Puedes anadir permisos de bluetooth aqui luego
        // si no los tienes.

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startRobotBrain()
        }
    }

    private fun startRobotBrain() {
        signalingClient = SignalingClient(
            url = "https://6a8tenisdemesa.com",
            roomName = "c-50-room"
        )

        webrtcManager = WebrtcManager(
            context = this,
            signalingClient = signalingClient,
            rootEglBase = eglBase
        )

        webrtcManager.onLocalVideoTrackReady = { track ->
            localVideoTrack = track
        }

        webrtcManager.onConnectionInfoChanged = { info ->
            connectionInfoText = info
        }

        webrtcManager.onCommandReceived = { cmd ->
            lastCommandText = "Instrucción Mando: $cmd"
        }

        signalingClient.connect()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::signalingClient.isInitialized) signalingClient.disconnect()
        if (::webrtcManager.isInitialized) webrtcManager.onDestroy()
        eglBase.release()
    }
}

@Composable
fun BrainScreen(videoTrack: VideoTrack?, eglBaseContext: EglBase.Context, connectionInfo: String, lastCommand: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (videoTrack != null) {
            AndroidView(
                factory = { context ->
                    SurfaceViewRenderer(context).apply {
                        init(eglBaseContext, null)
                        setEnableHardwareScaler(true)
                        setMirror(true) // En la interfaz del robot puede ser util modo espejo si usamos camara delantera
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    videoTrack.addSink(view)
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Cargando Camara...", color = Color.White)
            }
        }
        
        // Overlay de texto para saber que esta app es el Cerebro
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(8.dp)
        ) {
            Text(
                text = "🤖 C-50 BRAIN ACTIVE",
                color = Color.Green,
                fontSize = 18.sp
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
            Text(
                text = connectionInfo,
                color = Color.Yellow,
                fontSize = 14.sp
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
            Text(
                text = lastCommand,
                color = Color.Cyan,
                fontSize = 18.sp
            )
        }
    }
}
