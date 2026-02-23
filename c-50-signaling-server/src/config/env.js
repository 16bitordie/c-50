import dotenv from 'dotenv';

// Cargar variables de entorno desde el archivo .env
dotenv.config();

export const config = {
  port: process.env.PORT || 3000,
  corsOrigin: process.env.CORS_ORIGIN || '*',
};
