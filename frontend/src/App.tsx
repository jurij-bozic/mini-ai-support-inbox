import { Route, Routes } from 'react-router'
import { RequireAuth } from './auth/RequireAuth'
import { Layout } from './pages/Layout'
import { LoginPage } from './pages/LoginPage'
import { MessageDetailPage } from './pages/MessageDetailPage'
import { MessageListPage } from './pages/MessageListPage'
import { NewMessagePage } from './pages/NewMessagePage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route path="/" element={<MessageListPage />} />
          <Route path="/new" element={<NewMessagePage />} />
          <Route path="/messages/:id" element={<MessageDetailPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
