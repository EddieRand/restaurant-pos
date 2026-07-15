import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './index.css'
import './i18n'

async function bootstrap() {
  if (import.meta.env.VITE_MOCK_AUTH === 'true') {
    const { installMockAdapter } = await import('./api/mock')
    installMockAdapter()
  }

  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  )
}

bootstrap()
