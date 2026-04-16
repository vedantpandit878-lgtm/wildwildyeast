export default function MetricCard({ label, value, sub, accent }) {
  return (
    <div
      className={`bg-brand-white rounded-2xl p-5 flex flex-col gap-1 shadow-sm border-l-4 ${accent || 'border-brand-rust'}`}
    >
      <span className="text-xs font-medium text-brand-charcoal/50 uppercase tracking-wide">
        {label}
      </span>
      <span className="font-display text-3xl font-bold text-brand-charcoal">{value}</span>
      {sub && <span className="text-xs text-brand-charcoal/40">{sub}</span>}
    </div>
  )
}
