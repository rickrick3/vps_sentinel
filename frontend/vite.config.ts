import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// En dev, /api passe par le proxy de Vite vers core-api lancé depuis le poste : l'API reste sur la même origine que la
// page, comme derrière le gateway, et le cookie de session suit.
export default defineConfig({
  plugins: [react()],
  build: {
    rolldownOptions: {
      output: {
        // Bibliothèques dans leurs propres fichiers : elles restent en cache d'une version de l'application à l'autre.
        codeSplitting: {
          groups: [
            { name: 'react', test: /node_modules[\\/](react|react-dom|react-router|scheduler|@tanstack)[\\/]/ },
            { name: 'mantine', test: /node_modules[\\/]@mantine[\\/]/ },
          ],
        },
      },
    },
  },
  server: {
    port: 5173,
    // Origine autorisée en dev : http://localhost:5173 (SENTINEL_ALLOWED_ORIGINS). Un autre port ferait refuser le login.
    strictPort: true,
    proxy: {
      // Adresse IPv4 explicite : selon le système, localhost peut désigner ::1, où core-api n'écoute pas forcément.
      '/api': 'http://127.0.0.1:8080',
    },
  },
})
