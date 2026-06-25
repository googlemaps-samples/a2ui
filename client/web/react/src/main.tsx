import { StrictMode, Suspense, lazy } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { getAttributionId } from './utils/platform'

// Expose attribution ID globally
(window as any).A2UI_ATTRIBUTION_ID = getAttributionId();

// This file serves as the main entry point for the googlemaps-samples/a2ui demo project, for both web and mobile implementations. Depending on the build flag, will render either the web implementation or the mobile implementation.
const App = import.meta.env.VITE_BUILD_TARGET === 'mobile' 
  ? lazy(() => import('./AppMobile.tsx'))
  : lazy(() => import('./App.tsx'));

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Suspense fallback={<div style={{ textAlign: 'center', marginTop: '50px' }}>Loading...</div>}>
      <App />
    </Suspense>
  </StrictMode>,
)
