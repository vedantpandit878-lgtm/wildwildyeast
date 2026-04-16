const CATEGORIES = ['All', 'Loaves', 'Baguettes', 'Flatbreads', 'Enriched']

export default function CategoryPills({ active, onChange }) {
  return (
    <div
      className="flex gap-2 overflow-x-auto pb-2 px-4 scrollbar-hide"
      style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
      role="tablist"
      aria-label="Product categories"
    >
      {CATEGORIES.map((cat) => (
        <button
          key={cat}
          role="tab"
          aria-selected={active === cat}
          onClick={() => onChange(cat)}
          className={`flex-shrink-0 px-4 py-2 rounded-full text-sm font-medium transition-all duration-150 min-h-[40px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust active:scale-95 ${
            active === cat
              ? 'bg-brand-rust text-brand-white'
              : 'bg-brand-white text-brand-charcoal border border-gray-200 active:bg-gray-100'
          }`}
        >
          {cat}
        </button>
      ))}
    </div>
  )
}
