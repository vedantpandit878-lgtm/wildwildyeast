const STEPS = [
  { key: 'pending', label: 'Placed' },
  { key: 'confirmed', label: 'Confirmed' },
  { key: 'delivered', label: 'Delivered' },
]

const STATUS_INDEX = { pending: 0, confirmed: 1, delivered: 2 }

export default function StatusTracker({ status }) {
  const current = STATUS_INDEX[status] ?? 0

  return (
    <div className="flex items-center gap-0" role="list" aria-label="Order status">
      {STEPS.map((step, i) => {
        const isActive = i <= current
        const isLast = i === STEPS.length - 1
        return (
          <div key={step.key} className="flex items-center flex-1 last:flex-none" role="listitem">
            <div className="flex flex-col items-center gap-1">
              <div
                className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold border-2 transition-all duration-300 ${
                  isActive
                    ? 'bg-brand-rust border-brand-rust text-brand-white'
                    : 'bg-brand-white border-gray-300 text-brand-charcoal/40'
                }`}
                aria-label={`${step.label}${i === current ? ' (current)' : ''}`}
              >
                {i < current ? (
                  <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                    <path d="M2 7l4 4 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                ) : (
                  i + 1
                )}
              </div>
              <span className={`text-xs font-medium ${isActive ? 'text-brand-rust' : 'text-brand-charcoal/40'}`}>
                {step.label}
              </span>
            </div>
            {!isLast && (
              <div className={`flex-1 h-0.5 mx-1 mb-5 transition-all duration-300 ${i < current ? 'bg-brand-rust' : 'bg-gray-200'}`} />
            )}
          </div>
        )
      })}
    </div>
  )
}
