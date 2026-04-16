import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronDown, ChevronUp } from 'lucide-react'
import { getCustomerOrders } from '../api/customers'
import BottomNav from '../components/BottomNav'
import StatusTracker from '../components/StatusTracker'

function formatPrice(paise) {
  return `₹${(paise / 100).toFixed(0)}`
}

function formatDate(iso) {
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

const STATUS_LABEL = { pending: 'Pending', confirmed: 'Confirmed', delivered: 'Delivered' }
const STATUS_COLOR = {
  pending: 'bg-brand-amber/20 text-brand-amber',
  confirmed: 'bg-blue-100 text-blue-700',
  delivered: 'bg-green-100 text-green-700',
}

export default function MyOrders() {
  const navigate = useNavigate()
  const flat = localStorage.getItem('wwy_flat')
  const [orders, setOrders] = useState([])
  const [loading, setLoading] = useState(true)
  const [expanded, setExpanded] = useState(null)

  useEffect(() => {
    if (!flat) { navigate('/'); return }
    getCustomerOrders(flat).then(setOrders).catch(console.error).finally(() => setLoading(false))
  }, [flat, navigate])

  return (
    <div className="min-h-screen bg-brand-cream pb-24">
      <header className="bg-brand-black px-6 py-4">
        <h1 className="font-display text-xl font-bold text-brand-white">My Orders</h1>
      </header>

      <div className="px-4 pt-4 flex flex-col gap-3">
        {loading && Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="bg-brand-white rounded-2xl p-4 animate-pulse flex flex-col gap-3">
            <div className="h-4 bg-gray-200 rounded w-1/2" />
            <div className="h-4 bg-gray-200 rounded w-3/4" />
            <div className="h-4 bg-gray-200 rounded w-1/3" />
          </div>
        ))}

        {!loading && orders.length === 0 && (
          <div className="text-center py-16 text-brand-charcoal/50">
            <p className="text-lg font-display">No orders yet.</p>
            <p className="text-sm mt-2">Your bread journey starts whenever you're ready.</p>
          </div>
        )}

        {orders.map((order) => {
          const isExpanded = expanded === order.id
          const items = order.items?.filter(Boolean) || []
          const summary = items.slice(0, 2).map((i) => `${i.product_name}${items.length > 2 && items.indexOf(i) === 1 ? ` +${items.length - 2} more` : ''}`).join(', ')

          return (
            <div key={order.id} className="bg-brand-white rounded-2xl overflow-hidden shadow-sm">
              <button
                onClick={() => setExpanded(isExpanded ? null : order.id)}
                className="w-full p-4 text-left flex flex-col gap-2 active:bg-gray-50 transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust rounded-2xl"
                aria-expanded={isExpanded}
                aria-controls={`order-${order.id}`}
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="text-xs text-brand-charcoal/50">{formatDate(order.created_at)}</p>
                    <p className="font-medium text-brand-charcoal mt-0.5 text-sm leading-snug">{summary || 'Order'}</p>
                  </div>
                  <div className="flex flex-col items-end gap-1 shrink-0">
                    <span className={`text-xs font-semibold px-2 py-1 rounded-full ${STATUS_COLOR[order.status]}`}>
                      {STATUS_LABEL[order.status]}
                    </span>
                    <span className="font-bold text-brand-charcoal text-sm">{formatPrice(order.total_paise)}</span>
                  </div>
                </div>

                <div className="flex items-center justify-between">
                  <div className="flex-1">
                    <StatusTracker status={order.status} />
                  </div>
                  {isExpanded ? <ChevronUp size={16} className="text-brand-charcoal/40 shrink-0 ml-2 mb-5" /> : <ChevronDown size={16} className="text-brand-charcoal/40 shrink-0 ml-2 mb-5" />}
                </div>
              </button>

              {isExpanded && (
                <div id={`order-${order.id}`} className="px-4 pb-4 flex flex-col gap-2 border-t border-gray-100 pt-3">
                  {items.map((item) => (
                    <div key={item.id} className="flex justify-between text-sm">
                      <span className="text-brand-charcoal">{item.product_name} × {item.quantity}</span>
                      <span className="font-medium">{formatPrice(item.unit_price_paise * item.quantity)}</span>
                    </div>
                  ))}
                  <div className="pt-2 border-t border-gray-100 flex justify-between text-sm font-bold">
                    <span>Total</span>
                    <span>{formatPrice(order.total_paise)}</span>
                  </div>
                  <p className="text-xs text-brand-charcoal/40 mt-1 font-mono">#{order.id.slice(0, 8).toUpperCase()}</p>
                </div>
              )}
            </div>
          )
        })}
      </div>

      <BottomNav />
    </div>
  )
}
