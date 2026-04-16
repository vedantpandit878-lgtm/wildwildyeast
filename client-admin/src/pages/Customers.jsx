import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Search, ArrowUpDown } from 'lucide-react'
import { getCustomers } from '../api'

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('en-IN', {
    day: 'numeric', month: 'short', year: 'numeric',
  })
}

export default function Customers() {
  const navigate = useNavigate()
  const [customers, setCustomers] = useState([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [sortDir, setSortDir] = useState('asc')

  const token = localStorage.getItem('wwy_admin_token')
  useEffect(() => { if (!token) navigate('/login') }, [token, navigate])

  useEffect(() => {
    getCustomers()
      .then(setCustomers)
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  const filtered = customers
    .filter((c) => {
      if (!search.trim()) return true
      const q = search.toLowerCase()
      return c.flat_number.toLowerCase().includes(q) || c.name.toLowerCase().includes(q)
    })
    .sort((a, b) => {
      const cmp = a.flat_number.localeCompare(b.flat_number, undefined, { numeric: true })
      return sortDir === 'asc' ? cmp : -cmp
    })

  return (
    <div className="min-h-screen bg-brand-cream">
      <header className="bg-brand-black px-4 md:px-6 py-4 flex items-center justify-between sticky top-0 z-30">
        <div className="flex items-center gap-3">
          <img src="/logo-dark.png" alt="Wild Wild Yeast" className="h-9 w-9 object-contain" />
          <span className="font-display text-brand-white font-semibold">Customers</span>
        </div>
        <button
          onClick={() => navigate('/dashboard')}
          className="text-xs text-brand-white/40 hover:text-brand-white/70 transition-colors"
        >
          ← Dashboard
        </button>
      </header>

      <main className="px-4 md:px-6 py-6 max-w-7xl mx-auto flex flex-col gap-6">
        <div className="relative">
          <Search
            size={18}
            className="absolute left-4 top-1/2 -translate-y-1/2 text-brand-charcoal/40"
            aria-hidden="true"
          />
          <input
            type="search"
            placeholder="Search flat or name…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full h-12 pl-11 pr-4 rounded-xl bg-brand-white border border-gray-200 text-brand-charcoal placeholder:text-brand-charcoal/40 focus:outline-none focus:ring-2 focus:ring-brand-rust"
            aria-label="Search customers"
          />
        </div>

        {loading ? (
          <div className="flex flex-col gap-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="bg-brand-white rounded-2xl p-4 animate-pulse h-16" />
            ))}
          </div>
        ) : (
          <>
            {/* Desktop table */}
            <div className="hidden md:block overflow-x-auto rounded-2xl bg-brand-white shadow-sm">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100">
                    <th className="px-4 py-3 text-left">
                      <button
                        onClick={() => setSortDir((d) => d === 'asc' ? 'desc' : 'asc')}
                        className="flex items-center gap-1 text-xs font-semibold text-brand-charcoal/50 uppercase tracking-wide hover:text-brand-charcoal transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust rounded"
                        aria-label={`Sort by flat ${sortDir === 'asc' ? 'descending' : 'ascending'}`}
                      >
                        Flat <ArrowUpDown size={12} />
                      </button>
                    </th>
                    {['Name', 'Phone', 'Total orders', 'Last order'].map((h) => (
                      <th
                        key={h}
                        className="px-4 py-3 text-left text-xs font-semibold text-brand-charcoal/50 uppercase tracking-wide"
                      >
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((c) => (
                    <tr
                      key={c.id}
                      className="border-b border-gray-50 hover:bg-brand-cream/50 transition-colors"
                    >
                      <td className="px-4 py-3 font-semibold">{c.flat_number}</td>
                      <td className="px-4 py-3">{c.name}</td>
                      <td className="px-4 py-3 text-brand-charcoal/60">{c.phone || '—'}</td>
                      <td className="px-4 py-3 font-semibold">{c.total_orders}</td>
                      <td className="px-4 py-3 text-brand-charcoal/60">{formatDate(c.last_order_at)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Mobile cards */}
            <div className="md:hidden flex flex-col gap-3">
              {filtered.map((c) => (
                <div key={c.id} className="bg-brand-white rounded-2xl p-4 flex justify-between items-center">
                  <div>
                    <p className="font-semibold text-brand-charcoal">
                      Flat {c.flat_number} — {c.name}
                    </p>
                    <p className="text-xs text-brand-charcoal/50 mt-0.5">
                      {c.total_orders} order{c.total_orders !== 1 ? 's' : ''} · Last: {formatDate(c.last_order_at)}
                    </p>
                    {c.phone && (
                      <p className="text-xs text-brand-charcoal/40 mt-0.5">{c.phone}</p>
                    )}
                  </div>
                </div>
              ))}
            </div>

            {filtered.length === 0 && (
              <div className="text-center py-12 text-brand-charcoal/40">
                No customers found.
              </div>
            )}
          </>
        )}
      </main>
    </div>
  )
}
