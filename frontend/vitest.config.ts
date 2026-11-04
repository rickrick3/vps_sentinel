import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.ts'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      setupFiles: ['src/test/setup.ts'],
      restoreMocks: true,
      unstubGlobals: true,
      // Tests d'écrans complets (routeur, Mantine, saisie au clavier) : plus longs que les 5 s par défaut, et au-delà de
      // l'attente des requêtes de Testing Library (10 s, src/test/setup.ts).
      testTimeout: 30_000,
      // Un environnement jsdom par worker est lourd : au-delà de quatre, les workers se disputent le processeur et les
      // premiers rendus dépassent les délais d'attente.
      maxWorkers: 4,
    },
  }),
)
