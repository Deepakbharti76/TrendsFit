import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import 'bootstrap/dist/css/bootstrap.min.css';
import './styles.css';
import 'bootstrap/dist/js/bootstrap.bundle.min.js';
import App from './App';
import { AuthProvider, CartProvider, ConfigProvider, ToastProvider } from './lib/AppContext';

class Boundary extends React.Component {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch(err) { console.error(err); }
  render() {
    if (!this.state.failed) return this.props.children;
    return <div className="container py-5 text-center"><h1 className="h3">Something went wrong</h1><p>Please refresh the page. If it keeps happening, contact support.</p><button className="btn btn-dark" onClick={() => window.location.assign('/')}>Go to home</button></div>;
  }
}

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <Boundary>
      <BrowserRouter>
        <ToastProvider>
          <ConfigProvider>
            <AuthProvider>
              <CartProvider>
                <App />
              </CartProvider>
            </AuthProvider>
          </ConfigProvider>
        </ToastProvider>
      </BrowserRouter>
    </Boundary>
  </React.StrictMode>,
);
