import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { getProducts, createProduct, updateProduct, deleteProduct } from '../api'
import ProductGrid from '../components/ProductGrid'

export default function Products() {
  const navigate = useNavigate()
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(true)

  const token = localStorage.getItem('wwy_admin_token')
  useEffect(() => { if (!token) navigate('/login') }, [token, navigate])

  useEffect(() => {
    getProducts()
      .then(setProducts)
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  async function handleToggle(id, available) {
    await updateProduct(id, { available })
    setProducts((prev) => prev.map((p) => p.id === id ? { ...p, available } : p))
  }

  async function handleDelete(id) {
    if (!confirm('Delete this product? This cannot be undone.')) return
    await deleteProduct(id)
    setProducts((prev) => prev.filter((p) => p.id !== id))
  }

  async function handleEdit(id, data) {
    const updated = await updateProduct(id, data)
    setProducts((prev) => prev.map((p) => p.id === id ? updated : p))
  }

  async function handleAdd(data) {
    const created = await createProduct(data)
    setProducts((prev) => [...prev, created])
  }

  return (
    <div className="min-h-screen bg-brand-cream">
      <header className="bg-brand-black px-4 md:px-6 py-4 flex items-center justify-between sticky top-0 z-30">
        <div className="flex items-center gap-3">
          <img src="/logo-dark.png" alt="Wild Wild Yeast" className="h-9 w-9 object-contain" />
          <span className="font-display text-brand-white font-semibold">Products</span>
        </div>
        <button
          onClick={() => navigate('/dashboard')}
          className="text-xs text-brand-white/40 hover:text-brand-white/70 transition-colors"
        >
          ← Dashboard
        </button>
      </header>

      <main className="px-4 md:px-6 py-6 max-w-7xl mx-auto">
        {loading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="bg-brand-white rounded-2xl p-4 animate-pulse h-40" />
            ))}
          </div>
        ) : (
          <ProductGrid
            products={products}
            onToggle={handleToggle}
            onDelete={handleDelete}
            onEdit={handleEdit}
            onAdd={handleAdd}
          />
        )}
      </main>
    </div>
  )
}
