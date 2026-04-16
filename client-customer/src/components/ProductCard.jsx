import { Minus, Plus } from 'lucide-react'

function formatPrice(paise) {
  return `₹${(paise / 100).toFixed(0)}`
}

export default function ProductCard({ product, qty, onAdd, onIncrease, onDecrease }) {
  const isUnavailable = !product.available

  return (
    <div
      className={`bg-brand-white rounded-2xl p-4 flex flex-col gap-3 shadow-sm transition-opacity duration-150 ${
        isUnavailable ? 'opacity-60' : ''
      }`}
      onClick={() => !isUnavailable && qty === 0 && onAdd()}
      role={!isUnavailable && qty === 0 ? 'button' : undefined}
      tabIndex={!isUnavailable && qty === 0 ? 0 : undefined}
      onKeyDown={(e) => !isUnavailable && qty === 0 && e.key === 'Enter' && onAdd()}
      aria-label={!isUnavailable && qty === 0 ? `Add ${product.name} to cart` : undefined}
    >
      <div className="flex-1">
        <h3 className="font-display text-lg font-semibold text-brand-charcoal leading-tight">
          {product.name}
        </h3>
        {product.description && (
          <p className="text-sm text-brand-charcoal/70 mt-1 leading-snug">{product.description}</p>
        )}
      </div>

      <div className="flex items-center justify-between gap-3">
        <span className="font-sans font-bold text-brand-charcoal text-lg">
          {formatPrice(product.price_paise)}
        </span>

        {isUnavailable ? (
          <span className="text-xs font-medium text-brand-charcoal/50 bg-gray-100 px-3 py-2 rounded-lg">
            Out today
          </span>
        ) : qty === 0 ? (
          <button
            onClick={(e) => { e.stopPropagation(); onAdd(); }}
            className="bg-brand-rust text-brand-white font-medium text-sm px-5 py-3 rounded-xl min-h-[48px] min-w-[80px] active:scale-95 transition-transform duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-gold"
            aria-label={`Add ${product.name} to cart`}
          >
            + Add
          </button>
        ) : (
          <div className="flex items-center gap-3 bg-brand-cream rounded-xl px-2 py-1">
            <button
              onClick={(e) => { e.stopPropagation(); onDecrease(); }}
              className="w-9 h-9 flex items-center justify-center rounded-lg text-brand-rust active:scale-95 transition-transform duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust"
              aria-label={`Remove one ${product.name}`}
            >
              <Minus size={18} strokeWidth={2.5} />
            </button>
            <span className="font-bold text-brand-charcoal min-w-[24px] text-center">{qty}</span>
            <button
              onClick={(e) => { e.stopPropagation(); onIncrease(); }}
              className="w-9 h-9 flex items-center justify-center rounded-lg text-brand-rust active:scale-95 transition-transform duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust"
              aria-label={`Add another ${product.name}`}
            >
              <Plus size={18} strokeWidth={2.5} />
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
