import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './style.css';

// index.html içindeki tek root düğümüne uygulamayı bağlar. StrictMode geliştirmede
// Effect kurulum/temizliğini yeniden çalıştırarak eksik kaynak temizliğini görünür kılar.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
