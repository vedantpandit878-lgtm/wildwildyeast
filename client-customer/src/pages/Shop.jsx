import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { ShoppingCart } from 'lucide-react'
import { getProducts } from '../api/products'
import { placeOrder } from '../api/orders'
import ProductCard from '../components/ProductCard'
import CategoryPills from '../components/CategoryPills'
import CartSheet from '../components/CartSheet'
import BottomNav from '../components/BottomNav'

function ProductSkeleton() {
  return (
    <div className="bg-brand-white rounded-2xl p-4 flex flex-col gap-3 animate-pulse">
      <div className="h-5 bg-gray-200 rounded w-3/4" />
      <div className="h-4 bg-gray-200 rounded w-full" />
      <div className="flex justify-between items-center">
        <div className="h-6 bg-gray-200 rounded w-16" />
        <div className="h-12 bg-gray-200 rounded-xl w-24" />
      </div>
    </div>
  )
}

export default function Shop() {
  const navigate = useNavigate()
  const flat = localStorage.getItem('wwy_flat')
  const customerName = localStorage.getItem('wwy_name')

  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(true)
  const [category, setCategory] = useState('All')
  const [cart, setCart] = useState({})
  const [cartOpen, setCartOpen] = useState(false)
  const [isPlacing, setIsPlacing] = useState(false)
  const [toast, setToast] = useState(null)

  useEffect(() => {
    if (!flat) { navigate('/'); return }
    getProducts().then(setProducts).catch(console.error).finally(() => setLoading(false))
  }, [flat, navigate])

  const cartCount = Object.values(cart).reduce((s, v) => s + v, 0)

  const filteredProducts = category === 'All'
    ? products
    : products.filter((p) => p.category === category)

  const handleAdd = useCallback((id) => {
    setCart((c) => ({ ...c, [id]: (c[id] || 0) + 1 }))
  }, [])

  const handleIncrease = useCallback((id) => {
    setCart((c) => ({ ...c, [id]: (c[id] || 0) + 1 }))
  }, [])

  const handleDecrease = useCallback((id) => {
    setCart((c) => {
      const next = { ...c, [id]: (c[id] || 0) - 1 }
      if (next[id] <= 0) delete next[id]
      return next
    })
  }, [])

  async function handlePlaceOrder() {
    const items = Object.entries(cart)
      .filter(([, qty]) => qty > 0)
      .map(([id, qty]) => {
        const product = products.find((p) => p.id === id)
        return {
          product_id: id,
          product_name: product.name,
          quantity: qty,
          unit_price_paise: product.price_paise,
        }
      })

    if (items.length === 0) return
    setIsPlacing(true)

    try {
      const order = await placeOrder({
        flat_number: flat,
        customer_name: customerName,
        items,
        payment_method: 'cash',
      })
      setCart({})
      setCartOpen(false)
      navigate(`/confirm/${order.id}`)
    } catch (err) {
      setToast({ message: 'Something went wrong. Try again?', type: 'error' })
    } finally {
      setIsPlacing(false)
    }
  }

  useEffect(() => {
    if (toast) {
      const t = setTimeout(() => setToast(null), 3000)
      return () => clearTimeout(t)
    }
  }, [toast])

  return (
    <div className="min-h-screen bg-brand-cream pb-24">
      {/* Top bar */}
      <header className="sticky top-0 z-30 bg-brand-black px-4 py-3 flex items-center justify-between">
        <img src="/logo-dark.png" alt="Wild Wild Yeast Ferments" className="h-10 w-10 object-contain" />
        <span className="font-display text-brand-white text-lg font-semibold tracking-wide">Wild Wild Yeast</span>
        <button
          onClick={() => setCartOpen(true)}
          className="relative w-11 h-11 flex items-center justify-center text-brand-white rounded-full active:scale-95 transition-transform focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-gold"
          aria-label={`Open cart, ${cartCount} item${cartCount !== 1 ? 's' : ''}`}
        >
          <ShoppingCart size={24} strokeWidth={1.8} />
          {cartCount > 0 && (
            <span className="absolute -top-1 -right-1 bg-brand-amber text-brand-white text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center">
              {cartCount > 9 ? '9+' : cartCount}
            </span>
          )}
        </button>
      </header>

      {/* Welcome */}
      <div className="px-4 pt-6 pb-2">
        <p className="text-brand-charcoal/60 text-sm">Good to see you,</p>
        <h1 className="font-display text-2xl font-bold text-brand-charcoal">{customerName || 'neighbour'} 👋</h1>
      </div>

      {/* Categories */}
      <div className="py-4">
        <CategoryPills active={category} onChange={setCategory} />
      </div>

      {/* Products */}
      <div className="px-4 grid grid-cols-1 sm:grid-cols-2 gap-4">
        {loading
          ? Array.from({ length: 6 }).map((_, i) => <ProductSkeleton key={i} />)
          : filteredProducts.map((product) => (
              <ProductCard
                key={product.id}
                product={product}
                qty={cart[product.id] || 0}
                onAdd={() => handleAdd(product.id)}
                onIncrease={() => handleIncrease(product.id)}
                onDecrease={() => handleDecrease(product.id)}
              />
            ))}
      </div>

      {/* Toast */}
      {toast && (
        <div
          role="status"
          aria-live="polite"
          className="fixed top-20 left-4 right-4 z-50 bg-brand-charcoal text-brand-white px-4 py-3 rounded-xl text-sm font-medium text-center shadow-lg"
        >
          {toast.message}
        </div>
      )}

      <CartSheet
        cart={cart}
        products={products}
        isOpen={cartOpen}
        onClose={() => setCartOpen(false)}
        onIncrease={handleIncrease}
        onDecrease={handleDecrease}
        onPlaceOrder={handlePlaceOrder}
        isPlacing={isPlacing}
      />

      <BottomNav />
    </div>
  )
}
