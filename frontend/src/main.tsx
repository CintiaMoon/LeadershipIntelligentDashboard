import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import { AppProviders } from '@/app/providers/AppProviders';
import { createRouter } from '@/app/router/router';
import { brand } from '@/brand';
import { initTheme } from '@/stores/theme';
import './index.css';

initTheme();
document.title = brand.productName;
const router = createRouter();

createRoot(document.getElementById('root') as HTMLElement).render(
  <StrictMode>
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>
  </StrictMode>,
);
