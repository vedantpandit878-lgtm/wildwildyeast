import { ChevronRight, ArrowRight } from 'lucide-react'

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

export default function OrderTable({ orders, onSelect, onAdvance }) {
  if (orders.length === 0) {
    return (
      <div className="text-center py-12 text-brand-charcoal/40">
        No orders here.
      </div>
    )
  }

  return (
    <>
      {/* Desktop table */}
      <div className="hidden md:block overflow-x-auto rounded-2xl bg-brand-white shadow-sm">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-100 text-left">
              {['Flat', 'Customer', 'Items', 'Total', 'Time', 'Status', 'Action'].map((h) => (
                <th
                  key={h}
                  className="px-4 py-3 text-xs font-semibold text-brand-charcoal/50 uppercase tracking-wide"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => {
              const items = order.items?.filter(Boolean) || []
              const summary = items
                .slice(0, 2)
                .map((i) => `${i.product_name}${i.quantity > 1 ? ` ×${i.quantity}` : ''}`)
                .join(', ')
              const extra = items.length > 2 ? ` +${items.length - 2}` : ''
              const canAdvance = order.status !== 'delivered'
              const nextStatus = order.status === 'pending' ? 'confirmed' : 'delivered'

              return (
                <tr
                  key={order.id}
                  onClick={() => onSelect(order)}
                  className="border-b border-gray-50 hover:bg-brand-cream/50 cursor-pointer transition-colors"
                >
                  <td className="px-4 py-3 font-semibold">{order.flat_number}</td>
                  <td className="px-4 py-3">{order.customer_name}</td>
                  <td className="px-4 py-3 text-brand-charcoal/60 max-w-[200px] truncate">
                    {summary}{extra}
                  </td>
                  <td className="px-4 py-3 font-semibold">{formatPrice(order.total_paise)}</td>
                  <td className="px-4 py-3 text-brand-charcoal/60">{formatDate(order.created_at)}</td>
                  <td className="px-4 py-3">
                    <span className={`text-xs font-semibold px-2 py-1 rounded-full ${STATUS_COLOR[order.status]}`}>
                      {STATUS_LABEL[order.status]}
                    </span>
                  </td>
                  <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                    {canAdvance && (
                      <button
                        onClick={() => onAdvance(order.id, nextStatus)}
                        className="flex items-center gap-1 text-xs font-medium text-brand-rust hover:text-brand-gold transition-colors active:scale-95 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust rounded px-2 py-1"
                        aria-label={`Advance order ${order.id.slice(0, 8)} to ${STATUS_LABEL[nextStatus]}`}
                      >
                        <ArrowRight size={14} />
                        {nextStatus === 'confirmed' ? 'Confirm' : 'Deliver'}
                      </button>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {/* Mobile cards */}
      <div className="md:hidden flex flex-col gap-3">
        {orders.map((order) => {
          const items = order.items?.filter(Boolean) || []
          const summary = items
            .slice(0, 2)
            .map((i) => `${i.product_name}${i.quantity > 1 ? ` ×${i.quantity}` : ''}`)
            .join(', ')
          const extra = items.length > 2 ? ` +${items.length - 2} more` : ''
          const canAdvance = order.status !== 'delivered'
          const nextStatus = order.status === 'pending' ? 'confirmed' : 'delivered'

          return (
            <div key={order.id} className="bg-brand-white rounded-2xl p-4 shadow-sm">
              <div className="flex items-start justify-between gap-2 mb-3">
                <div>
                  <p className="font-semibold text-brand-charcoal">
                    Flat {order.flat_number} — {order.customer_name}
                  </p>
                  <p className="text-xs text-brand-charcoal/50 mt-0.5">
                    {formatDate(order.created_at)}
                  </p>
                </div>
                <span className={`text-xs font-semibold px-2 py-1 rounded-full shrink-0 ${STATUS_COLOR[order.status]}`}>
                  {STATUS_LABEL[order.status]}
                </span>
              </div>
              <p className="text-sm text-brand-charcoal/60 mb-3">{summary}{extra}</p>
              <div className="flex items-center justify-between">
                <span className="font-bold text-brand-charcoal">{formatPrice(order.total_paise)}</span>
                <div className="flex gap-2">
                  {canAdvance && (
                    <button
                      onClick={() => onAdvance(order.id, nextStatus)}
                      className="text-xs font-medium text-brand-rust border border-brand-rust/30 px-3 py-2 rounded-lg active:scale-95 transition-transform focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust"
                    >
                      {nextStatus === 'confirmed' ? 'Confirm' : 'Deliver'}
                    </button>
                  )}
                  <button
                    onClick={() => onSelect(order)}
                    className="text-xs font-medium text-brand-charcoal/60 border border-gray-200 px-3 py-2 rounded-lg active:scale-95 transition-transform focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust flex items-center gap-1"
                    aria-label={`View details for order ${order.id.slice(0, 8)}`}
                  >
                    Details <ChevronRight size={12} />
                  </button>
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </>
  )
}
