import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth'
import { ProtectedRoute } from './ProtectedRoute'
import { BookPage } from './pages/BookPage'
import { ChapterPage } from './pages/ChapterPage'
import { LibraryPage } from './pages/LibraryPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { SectionPage } from './pages/SectionPage'

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/" element={<Navigate to="/library" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/library" element={<LibraryPage />} />
          <Route path="/books/:bookId" element={<BookPage />} />
          <Route path="/books/:bookId/chapters/:chapterId" element={<ChapterPage />} />
          <Route
            path="/books/:bookId/chapters/:chapterId/sections/:sectionId"
            element={<SectionPage />}
          />
        </Route>
        <Route path="*" element={<Navigate to="/library" replace />} />
      </Routes>
    </AuthProvider>
  )
}
