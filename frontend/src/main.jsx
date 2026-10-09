import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'
import App from './App'
import PrintManager from './components/PrintManager'
import PublicMenu from './public/PublicMenu'

const publicPath = window.location.pathname.match(/^\/pedir\/([a-z0-9]+(?:-[a-z0-9]+)*)\/?$/)

createRoot(document.getElementById('root')).render(
  <StrictMode>
    {publicPath ? <PublicMenu slug={publicPath[1]} /> : <PrintManager><App /></PrintManager>}
  </StrictMode>,
)
