import { Home, ClipboardList, User } from 'lucide-react'
import { NavLink } from 'react-router-dom'

export default function BottomNav() {
  const navItem = ({ isActive }) =>
    `flex flex-col items-center gap-1 py-2 px-4 min-w-[64px] text-xs font-medium transition-colors duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust rounded-md ${
      isActive ? 'text-brand-rust' : 'text-brand-charcoal/60'
    }`

  return (
    <nav
      className="fixed bottom-0 left-0 right-0 bg-brand-white border-t border-gray-200 safe-bottom z-40"
      aria-label="Main navigation"
    >
      <div className="flex justify-around items-center max-w-lg mx-auto">
        <NavLink to="/shop" className={navItem} aria-label="Shop">
          <Home size={22} strokeWidth={1.8} />
          <span>Shop</span>
        </NavLink>
        <NavLink to="/orders" className={navItem} aria-label="My orders">
          <ClipboardList size={22} strokeWidth={1.8} />
          <span>My Orders</span>
        </NavLink>
        <NavLink to="/profile" className={navItem} aria-label="Profile">
          <User size={22} strokeWidth={1.8} />
          <span>Profile</span>
        </NavLink>
      </div>
    </nav>
  )
}
