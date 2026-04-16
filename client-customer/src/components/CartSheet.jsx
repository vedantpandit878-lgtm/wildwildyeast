import { useEffect, useRef } from 'react'
import { X, Minus, Plus, Loader2 } from 'lucide-react'

function formatPrice(paise) {
  return `₹${(paise / 100).toFixed(0)}`
}

export default function CartSheet({ cart, products, isOpen, onClose, onIncrease, onDecrease, onPlaceOrder, isPlacing }) {
  const sheetRef = useRef(null)

  useEffect(() => {
    if (isOpen) {
      sheetRef.current?.focus()
      document.body.style.overflow = 'hidden'
    } else {
      document.body.style.overflow = ''
    }
    return () => { document.body.style.overflow = '' }
  }, [isOpen])

  const items = Object.entries(cart)
    .filter(([, qty]) => qty > 0)
    .map(([id, qty]) => ({ product: products.find((p) => p.id === id), qty }))
    .filter((i) => i.product)

  const total = items.reduce((sum, { product, qty }) => sum + product.price_paise * qty, 0)

  if (!isOpen) return null

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/50 z-40 transition-opacity duration-200"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Sheet */}
      <div
        ref={sheetRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label="Cart"
        className="fixed bottom-0 left-0 right-0 z-50 bg-brand-white rounded-t-3xl max-h-[85vh] flex flex-col safe-bottom outline-none"
        style={{ animation: 'slideUp 250ms ease-out' }}
      >
        <style>{`@keyframes slideUp { from { transform: translateY(100%); } to { transform: translateY(0); } }`}</style>

        {/* Handle */}
        <div className="flex justify-center pt-3 pb-1">
          <div className="w-10 h-1 bg-gray-300 rounded-full" />
        </div>

        <div className="flex items-center justify-between px-6 py-3 border-b border-gray-100">
          <h2 className="font-display text-xl font-semibold text-brand-charcoal">Your cart</h2>
          <button
            onClick={onClose}
            className="w-10 h-10 flex items-center justify-center rounded-full text-brand-charcoal/60 hover:bg-gray-100 active:scale-95 transition-all duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust"
            aria-label="Close cart"
          >
            <X size={20} />
          </button>
        </div>

        {/* Items */}
        <div className="flex-1 overflow-y-auto px-6 py-4 flex flex-col gap-4">
          {items.length === 0 ? (
            <p className="text-center text-brand-charcoal/50 py-8">Nothing in the cart yet.</p>
          ) : (
            items.map(({ product, qty }) => (
              <div key={product.id} className="flex items-center justify-between gap-4">
                <div className="flex-1 min-w-0">
                  <p className="font-medium text-brand-charcoal truncate">{product.name}</p>
                  <p className="text-sm text-brand-charcoal/60">
                    {formatPrice(product.price_paise)} each
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => onDecrease(product.id)}
                    className="w-8 h-8 flex items-center justify-center rounded-full border border-gray-200 text-brand-rust active:scale-95 transition-transform focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust"
                    aria-label={`Remove one ${product.name}`}
                  >
                    <Minus size={14} strokeWidth={2.5} />
                  </button>
                  <span className="font-bold w-6 text-center">{qty}</span>
                  <button
                    onClick={() => onIncrease(product.id)}
                    className="w-8 h-8 flex items-center justify-center rounded-full border border-gray-200 text-brand-rust active:scale-95 transition-transform focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust"
                    aria-label={`Add another ${product.name}`}
                  >
                    <Plus size={14} strokeWidth={2.5} />
                  </button>
                  <span className="font-bold text-brand-charcoal w-16 text-right">
                    {formatPrice(product.price_paise * qty)}
                  </span>
                </div>
              </div>
            ))
          )}

          {items.length > 0 && (
            <p className="text-sm text-brand-charcoal/60 text-center mt-2">
              Free delivery within the building 😄
            </p>
          )}
        </div>

        {/* Footer */}
        {items.length > 0 && (
          <div className="px-6 pb-6 pt-4 border-t border-gray-100 flex flex-col gap-4">
            <div className="flex justify-between items-center">
              <span className="text-brand-charcoal font-medium">Total</span>
              <span className="font-display text-2xl font-bold text-brand-charcoal">{formatPrice(total)}</span>
            </div>
            <button
              onClick={onPlaceOrder}
              disabled={isPlacing || items.length === 0}
              className="w-full min-h-[56px] bg-brand-rust text-brand-white font-semibold text-base rounded-2xl flex items-center justify-center gap-2 active:scale-95 disabled:opacity-60 disabled:cursor-not-allowed transition-all duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-gold"
            >
              {isPlacing ? (
                <>
                  <Loader2 size={20} className="animate-spin" />
                  <span>Placing order…</span>
                </>
              ) : (
                'Place order'
              )}
            </button>
          </div>
        )}
      </div>
    </>
  )
}
