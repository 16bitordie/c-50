import express from 'express';
import http from 'http';
import { Server } from 'socket.io';
import cors from 'cors';
import { config } from './config/env.js';
import { setupSignaling } from './sockets/signaling.js';

// 1. Inicializar Express y el servidor HTTP
const app = express();
const server = http.createServer(app);

// 2. Configurar Middlewares
app.use(cors({
  origin: config.corsOrigin,
  methods: ['GET', 'POST']
}));

// 3. Inicializar Socket.io
const io = new Server(server, {
  cors: {
    origin: config.corsOrigin,
    methods: ['GET', 'POST']
  }
});

// 4. Configurar la lógica de señalización
setupSignaling(io);

// 5. Ruta de salud (Healthcheck) para verificar que el servidor está vivo
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'ok', message: 'C-50 Signaling Server is running' });
});

// 6. Iniciar el servidor
server.listen(config.port, () => {
  console.log(`=========================================`);
  console.log(`🚀 Servidor de Señalización C-50 iniciado`);
  console.log(`📡 Escuchando en el puerto: ${config.port}`);
  console.log(`=========================================`);
});
