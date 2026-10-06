import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import { AuthProvider } from './auth'
import { ProtectedRoute } from './ProtectedRoute'
import { BookPage } from './pages/BookPage'
import { ChapterPage } from './pages/ChapterPage'
import { LibraryPage } from './pages/LibraryPage'
import { LoginPage } from './pages/LoginPage'
import { NotePage } from './pages/NotePage'
import { RegisterPage } from './pages/RegisterPage'
import { SectionPage } from './pages/SectionPage'
import { SearchPage } from './pages/SearchPage'
import { BackupPage } from './pages/BackupPage'
import { SettingsPage } from './pages/SettingsPage'
import { Navigate } from 'react-router-dom'
import './index.css'

const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/library" replace /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  {
    element: <ProtectedRoute />,
    children: [
      { path: '/library', element: <LibraryPage /> },
      { path: '/search', element: <SearchPage /> },
      { path: '/backup', element: <BackupPage /> },
      { path: '/settings', element: <SettingsPage /> },
      { path: '/books/:bookId', element: <BookPage /> },
      { path: '/books/:bookId/chapters/:chapterId', element: <ChapterPage /> },
      {
        path: '/books/:bookId/chapters/:chapterId/sections/:sectionId',
        element: <SectionPage />,
      },
      { path: '/books/:bookId/notes/:noteId', element: <NotePage /> },
    ],
  },
  { path: '*', element: <Navigate to="/library" replace /> },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  </StrictMode>,
)
