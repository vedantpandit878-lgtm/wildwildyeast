import { X } from 'lucide-react'
import { useEffect, useRef } from 'react'

function formatPrice(paise) { return `₹${(paise / 100).toFixed(0)}` }
function formatDate(iso) {
  return new Date(iso).toLocaleString('en-IN', {
    day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
  })
}

const STATUS_LABEL = { pending: 'Pending', confirmed: 'Confirmed', delivered: 'Delivered' }
const STATUS_COLOR = {
  pending: 'bg-brand-amber/20 text-brand-amber',
  confirmed: 'bg-blue-100 text-blue-700',
  delivered: 'bg-green-100 text-green-700',
}

export default function OrderDrawer({ order, onClose, onAdvance }) {
  const ref = useRef(null)

  useEffect(() => {
    if (order) ref.current?.focus()
  }, [order])

  if (!order) return null

  const items = order.items?.filter(Boolean) || []
  const canAdvance = order.status !== 'delivered'
  const nextStatus = order.status === 'pending' ? 'confirmed' : 'delivered'

  return (
    <>
      <div
        className="fixed inset-0 bg-black/40 z-40"
        onClick={onClose}
        aria-hidden="true"
      />
      <aside
        ref={ref}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label={`Order ${order.id.slice(0, 8)}`}
        className="fixed right-0 top-0 bottom-0 z-50 w-full max-w-md bg-brand-white shadow-2xl flex flex-col outline-none md:border-l border-gray-200"
        style={{ animation: 'slideIn 200ms ease-out' }}
      >
        <style>{`
          @keyframes slideIn {
            from { transform: translateX(100%); }
            to { transform: translateX(0); }
          }
        `}</style>

        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <div>
            <h2 className="font-display text-lg font-semibold text-brand-charcoal">
              Order details
            </h2>
            <p className="text-xs text-brand-charcoal/40 font-mono">
              #{order.id.slice(0, 8).toUpperCase()}
            </p>
          </div>
          <button
            onClick={onClose}
            className="w-10 h-10 flex items-center justify-center rounded-full hover:bg-gray-100 text-brand-charcoal/60 active:scale-95 transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust"
            aria-label="Close order details"
          >
            <X size={20} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6 flex flex-col gap-6">
          <div className="flex flex-col gap-1">
            <h3 className="text-xs uppercase tracking-wide text-brand-charcoal/40 font-medium">
              Customer
            </h3>
            <p className="font-semibold text-brand-charcoal">{order.customer_name}</p>
            <p className="text-brand-charcoal/60">Flat {order.flat_number}</p>
          </div>

          <div className="flex items-start justify-between gap-4">
            <div>
              <h3 className="text-xs uppercase tracking-wide text-brand-charcoal/40 font-medium mb-1">
                Status
              </h3>
              <span className={`text-sm font-semibold px-3 py-1 rounded-full ${STATUS_COLOR[order.status]}`}>
                {STATUS_LABEL[order.status]}
              </span>
            </div>
            <div className="text-right">
              <h3 className="text-xs uppercase tracking-wide text-brand-charcoal/40 font-medium mb-1">
                Ordered
              </h3>
              <p className="text-sm text-brand-charcoal">{formatDate(order.created_at)}</p>
            </div>
          </div>

          <div>
            <h3 className="text-xs uppercase tracking-wide text-brand-charcoal/40 font-medium mb-3">
              Items
            </h3>
            <div className="flex flex-col gap-2">
              {items.map((item) => (
                <div key={item.id} className="flex justify-between text-sm">
                  <span className="text-brand-charcoal">
                    {item.product_name} × {item.quantity}
                  </span>
                  <span className="font-medium">
                    {formatPrice(item.unit_price_paise * item.quantity)}
                  </span>
                </div>
              ))}
              <div className="pt-3 border-t border-gray-100 flex justify-between font-bold text-brand-charcoal">
                <span>Total</span>
                <span>{formatPrice(order.total_paise)}</span>
              </div>
            </div>
          </div>

          {order.notes && (
            <div>
              <h3 className="text-xs uppercase tracking-wide text-brand-charcoal/40 font-medium mb-1">
                Notes
              </h3>
              <p className="text-sm text-brand-charcoal bg-brand-cream rounded-xl p-3">
                {order.notes}
              </p>
            </div>
          )}

          <div>
            <h3 className="text-xs uppercase tracking-wide text-brand-charcoal/40 font-medium mb-1">
              Payment
            </h3>
            <p className="text-sm text-brand-charcoal capitalize">{order.payment_method}</p>
          </div>
        </div>

        {canAdvance && (
          <div className="p-6 border-t border-gray-100">
            <button
              onClick={() => onAdvance(order.id, nextStatus)}
              className="w-full min-h-[52px] bg-brand-rust text-brand-white font-semibold rounded-xl active:scale-95 transition-transform focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-gold"
            >
              Mark as {STATUS_LABEL[nextStatus]}
            </button>
          </div>
        )}
      </aside>
    </>
  )
}
