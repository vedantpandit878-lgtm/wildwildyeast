import { useState, useEffect } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { getOrder } from '../api/orders'
import BottomNav from '../components/BottomNav'
import StatusTracker from '../components/StatusTracker'

function formatPrice(paise) {
  return `₹${(paise / 100).toFixed(0)}`
}

export default function OrderConfirm() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [order, setOrder] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getOrder(id).then(setOrder).catch(console.error).finally(() => setLoading(false))
  }, [id])

  if (loading) {
    return (
      <div className="min-h-screen bg-brand-cream flex items-center justify-center pb-24">
        <div className="animate-pulse flex flex-col items-center gap-4">
          <div className="w-24 h-24 bg-gray-200 rounded-full" />
          <div className="h-6 bg-gray-200 rounded w-48" />
        </div>
        <BottomNav />
      </div>
    )
  }

  if (!order) {
    return (
      <div className="min-h-screen bg-brand-cream flex flex-col items-center justify-center gap-4 px-6 pb-24">
        <p className="text-brand-charcoal/60 text-center">Order not found.</p>
        <Link to="/shop" className="text-brand-rust underline">Back to shop</Link>
        <BottomNav />
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-brand-cream pb-24">
      <div className="px-6 pt-12 flex flex-col items-center gap-6 max-w-sm mx-auto">
        {/* Success checkmark */}
        <div className="w-24 h-24 bg-brand-rust/10 rounded-full flex items-center justify-center">
          <svg width="48" height="48" viewBox="0 0 48 48" fill="none" aria-hidden="true">
            <circle cx="24" cy="24" r="22" stroke="#8B3A1A" strokeWidth="2.5" />
            <path d="M14 24l7 7 13-14" stroke="#8B3A1A" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </div>

        <div className="text-center">
          <h1 className="font-display text-2xl font-bold text-brand-charcoal">Order placed!</h1>
          <p className="text-brand-charcoal/60 mt-2">Order placed. We're already preheating.</p>
          <p className="text-xs text-brand-charcoal/40 mt-2 font-mono">#{order.id.slice(0, 8).toUpperCase()}</p>
        </div>

        {/* Status */}
        <div className="w-full bg-brand-white rounded-2xl p-4">
          <StatusTracker status={order.status} />
        </div>

        {/* Items */}
        <div className="w-full bg-brand-white rounded-2xl p-4 flex flex-col gap-3">
          <h2 className="font-semibold text-brand-charcoal">Order summary</h2>
          {order.items?.filter(Boolean).map((item) => (
            <div key={item.id} className="flex justify-between text-sm">
              <span className="text-brand-charcoal">{item.product_name} × {item.quantity}</span>
              <span className="font-medium text-brand-charcoal">{formatPrice(item.unit_price_paise * item.quantity)}</span>
            </div>
          ))}
          <div className="pt-2 border-t border-gray-100 flex justify-between font-bold text-brand-charcoal">
            <span>Total</span>
            <span>{formatPrice(order.total_paise)}</span>
          </div>
        </div>

        {/* Actions */}
        <div className="w-full flex flex-col gap-3">
          <Link
            to="/orders"
            className="w-full min-h-[52px] bg-brand-rust text-brand-white font-semibold rounded-2xl flex items-center justify-center active:scale-95 transition-transform focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-gold"
          >
            Track this order
          </Link>
          <Link
            to="/shop"
            className="w-full min-h-[52px] bg-brand-white text-brand-charcoal font-semibold rounded-2xl flex items-center justify-center border border-gray-200 active:scale-95 transition-transform focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust"
          >
            Order more
          </Link>
        </div>
      </div>

      <BottomNav />
    </div>
  )
}
