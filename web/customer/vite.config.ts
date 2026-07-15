export default {
  server: {
    port: 5175,
    proxy: {
      '/public': {
        target: process.env.VITE_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
}
