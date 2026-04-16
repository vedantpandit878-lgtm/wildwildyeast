import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { RefreshCw, Search, Users, Package } from 'lucide-react'
import { getOrders, updateOrderStatus } from '../api'
import MetricCard from '../components/MetricCard'
import OrderTable from '../components/OrderTable'
import OrderDrawer from '../components/OrderDrawer'

const TABS = [
  { key: '', label: 'All' },
  { key: 'pending', label: 'Pending' },
  { key: 'confirmed', label: 'Confirmed' },
  { key: 'delivered', label: 'Delivered' },
]

function formatPrice(paise) { return `₹${(paise / 100).toFixed(0)}` }

function isToday(iso) {
  const d = new Date(iso)
  const now = new Date()
  return (
    d.getDate() === now.getDate() &&
    d.getMonth() === now.getMonth() &&
    d.getFullYear() === now.getFullYear()
  )
}

export default function Dashboard() {
  const navigate = useNavigate()
  const [orders, setOrders] = useState([])
  const [loading, setLoading] = useState(true)
  const [tab, setTab] = useState('')
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState(null)

  const token = localStorage.getItem('wwy_admin_token')
  useEffect(() => { if (!token) navigate('/login') }, [token, navigate])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const data = await getOrders()
      setOrders(data)
    } catch (err) {
      if (err.message?.includes('401') || err.message?.includes('403')) {
        localStorage.removeItem('wwy_admin_token')
        navigate('/login')
      }
    } finally {
      setLoading(false)
    }
  }, [navigate])

  useEffect(() => { load() }, [load])

  async function handleAdvance(id, status) {
    await updateOrderStatus(id, status)
    setOrders((prev) =>
      prev.map((o) => o.id === id ? { ...o, status, updated_at: new Date().toISOString() } : o)
    )
    if (selected?.id === id) setSelected((s) => ({ ...s, status }))
  }

  const todayOrders = orders.filter((o) => isToday(o.created_at))
  const todayRevenue = todayOrders
    .filter((o) => o.status === 'delivered')
    .reduce((s, o) => s + o.total_paise, 0)

  const filtered = orders
    .filter((o) => !tab || o.status === tab)
    .filter((o) => {
      if (!search.trim()) return true
      const q = search.toLowerCase()
      return o.flat_number.toLowerCase().includes(q) || o.customer_name.toLowerCase().includes(q)
    })

  return (
    <div className="min-h-screen bg-brand-cream">
      <header className="bg-brand-black px-4 md:px-6 py-4 flex items-center justify-between sticky top-0 z-30">
        <div className="flex items-center gap-3">
          <img src="/logo-dark.png" alt="Wild Wild Yeast" className="h-9 w-9 object-contain" />
          <span className="font-display text-brand-white font-semibold hidden sm:block">
            Dashboard
          </span>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => navigate('/customers')}
            className="w-10 h-10 flex items-center justify-center text-brand-white/60 hover:text-brand-white rounded-full active:scale-95 transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-gold"
            aria-label="Customers"
          >
            <Users size={18} />
          </button>
          <button
            onClick={() => navigate('/products')}
            className="w-10 h-10 flex items-center justify-center text-brand-white/60 hover:text-brand-white rounded-full active:scale-95 transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-gold"
            aria-label="Products"
          >
            <Package size={18} />
          </button>
          <button
            onClick={load}
            className="w-10 h-10 flex items-center justify-center text-brand-white/60 hover:text-brand-white rounded-full active:scale-95 transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-gold"
            aria-label="Refresh orders"
          >
            <RefreshCw size={18} className={loading ? 'animate-spin' : ''} />
          </button>
          <button
            onClick={() => {
              localStorage.removeItem('wwy_admin_token')
              navigate('/login')
            }}
            className="text-xs text-brand-white/40 hover:text-brand-white/70 transition-colors px-2"
          >
            Sign out
          </button>
        </div>
      </header>

      <main className="px-4 md:px-6 py-6 max-w-7xl mx-auto flex flex-col gap-6">
        {/* Metrics */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <MetricCard
            label="Orders today"
            value={todayOrders.length}
            accent="border-brand-rust"
          />
          <MetricCard
            label="Pending"
            value={todayOrders.filter((o) => o.status === 'pending').length}
            accent="border-brand-amber"
          />
          <MetricCard
            label="Confirmed"
            value={todayOrders.filter((o) => o.status === 'confirmed').length}
            accent="border-blue-400"
          />
          <MetricCard
            label="Revenue"
            value={formatPrice(todayRevenue)}
            sub="Delivered orders"
            accent="border-green-400"
          />
        </div>

        {/* Search */}
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
            className="w-full h-12 pl-11 pr-4 rounded-xl bg-brand-white border border-gray-200 text-brand-charcoal placeholder:text-brand-charcoal/40 focus:outline-none focus:ring-2 focus:ring-brand-rust transition-all"
            aria-label="Search orders"
          />
        </div>

        {/* Status tabs */}
        <div
          className="flex gap-2 overflow-x-auto pb-1"
          style={{ scrollbarWidth: 'none' }}
          role="tablist"
          aria-label="Filter orders by status"
        >
          {TABS.map((t) => (
            <button
              key={t.key}
              role="tab"
              aria-selected={tab === t.key}
              onClick={() => setTab(t.key)}
              className={`flex-shrink-0 px-4 py-2 rounded-full text-sm font-medium transition-all min-h-[40px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust active:scale-95 ${
                tab === t.key
                  ? 'bg-brand-rust text-brand-white'
                  : 'bg-brand-white text-brand-charcoal border border-gray-200'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>

        {/* Orders */}
        {loading ? (
          <div className="flex flex-col gap-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="bg-brand-white rounded-2xl p-4 animate-pulse h-16" />
            ))}
          </div>
        ) : (
          <OrderTable
            orders={filtered}
            onSelect={setSelected}
            onAdvance={handleAdvance}
          />
        )}
      </main>

      <OrderDrawer
        order={selected}
        onClose={() => setSelected(null)}
        onAdvance={handleAdvance}
      />
    </div>
  )
}
