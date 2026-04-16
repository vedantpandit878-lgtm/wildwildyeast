import { useNavigate } from 'react-router-dom'
import { LogOut, Home } from 'lucide-react'
import BottomNav from '../components/BottomNav'

export default function Profile() {
  const navigate = useNavigate()
  const flat = localStorage.getItem('wwy_flat')
  const name = localStorage.getItem('wwy_name')

  function handleLogout() {
    localStorage.removeItem('wwy_flat')
    localStorage.removeItem('wwy_name')
    navigate('/')
  }

  return (
    <div className="min-h-screen bg-brand-cream pb-24">
      <header className="bg-brand-black px-6 py-4">
        <h1 className="font-display text-xl font-bold text-brand-white">Profile</h1>
      </header>

      <div className="px-6 pt-8 flex flex-col gap-6 max-w-sm mx-auto">
        {/* Avatar */}
        <div className="flex flex-col items-center gap-3">
          <div className="w-20 h-20 bg-brand-rust/10 rounded-full flex items-center justify-center">
            <Home size={32} className="text-brand-rust" />
          </div>
          <div className="text-center">
            <p className="font-display text-2xl font-bold text-brand-charcoal">{name}</p>
            <p className="text-brand-charcoal/60 mt-1">Flat {flat}</p>
          </div>
        </div>

        {/* Info card */}
        <div className="bg-brand-white rounded-2xl p-4 flex flex-col gap-4">
          <div className="flex justify-between items-center py-1">
            <span className="text-brand-charcoal/60 text-sm">Name</span>
            <span className="font-medium text-brand-charcoal">{name}</span>
          </div>
          <div className="h-px bg-gray-100" />
          <div className="flex justify-between items-center py-1">
            <span className="text-brand-charcoal/60 text-sm">Flat number</span>
            <span className="font-medium text-brand-charcoal">{flat}</span>
          </div>
        </div>

        {/* Logout */}
        <button
          onClick={handleLogout}
          className="w-full min-h-[52px] bg-brand-white text-brand-charcoal font-semibold rounded-2xl flex items-center justify-center gap-2 border border-gray-200 active:scale-95 transition-transform duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust"
        >
          <LogOut size={18} />
          <span>Switch flat</span>
        </button>

        <p className="text-center text-xs text-brand-charcoal/30">Fermented with patience. Eaten without guilt (probably).</p>
      </div>

      <BottomNav />
    </div>
  )
}
