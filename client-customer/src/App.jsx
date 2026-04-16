import { useEffect } from 'react'
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom'
import Login from './pages/Login'
import Shop from './pages/Shop'
import OrderConfirm from './pages/OrderConfirm'
import MyOrders from './pages/MyOrders'
import Profile from './pages/Profile'

function AutoLogin({ children }) {
  const navigate = useNavigate()
  useEffect(() => {
    const flat = localStorage.getItem('wwy_flat')
    const name = localStorage.getItem('wwy_name')
    if (flat && name) navigate('/shop', { replace: true })
  }, [navigate])
  return children
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={
        <AutoLogin>
          <Login />
        </AutoLogin>
      } />
      <Route path="/shop" element={<Shop />} />
      <Route path="/confirm/:id" element={<OrderConfirm />} />
      <Route path="/orders" element={<MyOrders />} />
      <Route path="/profile" element={<Profile />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
