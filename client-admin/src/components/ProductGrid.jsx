import { useState } from 'react'
import { Pencil, Trash2, Loader2 } from 'lucide-react'

const CATEGORIES = ['Loaves', 'Baguettes', 'Flatbreads', 'Enriched']

export default function ProductGrid({ products, onToggle, onDelete, onEdit, onAdd }) {
  const [showModal, setShowModal] = useState(false)
  const [editProduct, setEditProduct] = useState(null)
  const [form, setForm] = useState({
    name: '', category: 'Loaves', description: '', price: '', available: true,
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  function openAdd() {
    setEditProduct(null)
    setForm({ name: '', category: 'Loaves', description: '', price: '', available: true })
    setError('')
    setShowModal(true)
  }

  function openEdit(product) {
    setEditProduct(product)
    setForm({
      name: product.name,
      category: product.category,
      description: product.description || '',
      price: String(product.price_paise / 100),
      available: product.available,
    })
    setError('')
    setShowModal(true)
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!form.name.trim() || !form.price) { setError('Name and price are required'); return }
    const price_paise = Math.round(parseFloat(form.price) * 100)
    if (isNaN(price_paise) || price_paise <= 0) { setError('Enter a valid price in ₹'); return }
    setSaving(true)
    setError('')
    try {
      const payload = {
        name: form.name.trim(),
        category: form.category,
        description: form.description.trim() || undefined,
        price_paise,
        available: form.available,
      }
      if (editProduct) {
        await onEdit(editProduct.id, payload)
      } else {
        await onAdd(payload)
      }
      setShowModal(false)
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h2 className="font-display text-xl font-bold text-brand-charcoal">Products</h2>
        <button
          onClick={openAdd}
          className="bg-brand-rust text-brand-white font-medium text-sm px-4 py-2.5 rounded-xl min-h-[44px] active:scale-95 transition-transform focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-gold"
        >
          + Add product
        </button>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {products.map((product) => (
          <div
            key={product.id}
            className={`bg-brand-white rounded-2xl p-4 shadow-sm flex flex-col gap-3 transition-opacity ${
              !product.available ? 'opacity-60' : ''
            }`}
          >
            <div className="flex items-start justify-between gap-2">
              <div className="flex-1 min-w-0">
                <span className="text-xs font-medium text-brand-charcoal/40 uppercase tracking-wide">
                  {product.category}
                </span>
                <h3 className="font-display font-semibold text-brand-charcoal mt-0.5 leading-tight">
                  {product.name}
                </h3>
              </div>
              <span className="font-bold text-brand-charcoal shrink-0">
                ₹{(product.price_paise / 100).toFixed(0)}
              </span>
            </div>

            {product.description && (
              <p className="text-sm text-brand-charcoal/60 leading-snug">{product.description}</p>
            )}

            <div className="flex items-center justify-between pt-1 border-t border-gray-100">
              <label className="flex items-center gap-2 cursor-pointer">
                <button
                  role="switch"
                  aria-checked={product.available}
                  onClick={() => onToggle(product.id, !product.available)}
                  className={`relative w-11 h-6 rounded-full transition-colors duration-200 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust ${
                    product.available ? 'bg-brand-rust' : 'bg-gray-300'
                  }`}
                  aria-label={`${product.available ? 'Mark unavailable' : 'Mark available'}: ${product.name}`}
                >
                  <span
                    className={`absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform duration-200 ${
                      product.available ? 'translate-x-5' : 'translate-x-0'
                    }`}
                  />
                </button>
                <span className="text-xs font-medium text-brand-charcoal/60">
                  {product.available ? 'Available' : 'Out today'}
                </span>
              </label>

              <div className="flex gap-1">
                <button
                  onClick={() => openEdit(product)}
                  className="w-9 h-9 flex items-center justify-center rounded-lg text-brand-charcoal/50 hover:text-brand-charcoal hover:bg-gray-100 active:scale-95 transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust"
                  aria-label={`Edit ${product.name}`}
                >
                  <Pencil size={15} />
                </button>
                <button
                  onClick={() => onDelete(product.id)}
                  className="w-9 h-9 flex items-center justify-center rounded-lg text-red-400 hover:text-red-600 hover:bg-red-50 active:scale-95 transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-red-400"
                  aria-label={`Delete ${product.name}`}
                >
                  <Trash2 size={15} />
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Add / Edit modal */}
      {showModal && (
        <>
          <div
            className="fixed inset-0 bg-black/50 z-40"
            onClick={() => setShowModal(false)}
            aria-hidden="true"
          />
          <div
            role="dialog"
            aria-modal="true"
            aria-label={editProduct ? 'Edit product' : 'Add product'}
            className="fixed inset-x-4 top-1/2 -translate-y-1/2 z-50 bg-brand-white rounded-2xl p-6 max-w-md mx-auto shadow-2xl"
          >
            <h2 className="font-display text-xl font-bold text-brand-charcoal mb-6">
              {editProduct ? 'Edit product' : 'Add product'}
            </h2>
            <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
              <div className="flex flex-col gap-1.5">
                <label htmlFor="p-name" className="text-sm font-medium text-brand-charcoal">
                  Product name
                </label>
                <input
                  id="p-name"
                  type="text"
                  value={form.name}
                  onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                  autoFocus
                  className="h-12 px-3 rounded-xl border border-gray-200 bg-brand-white text-brand-charcoal focus:outline-none focus:ring-2 focus:ring-brand-rust"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label htmlFor="p-cat" className="text-sm font-medium text-brand-charcoal">
                  Category
                </label>
                <select
                  id="p-cat"
                  value={form.category}
                  onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}
                  className="h-12 px-3 rounded-xl border border-gray-200 bg-brand-white text-brand-charcoal focus:outline-none focus:ring-2 focus:ring-brand-rust"
                >
                  {CATEGORIES.map((c) => <option key={c}>{c}</option>)}
                </select>
              </div>

              <div className="flex flex-col gap-1.5">
                <label htmlFor="p-desc" className="text-sm font-medium text-brand-charcoal">
                  Description{' '}
                  <span className="text-brand-charcoal/40 font-normal">(optional)</span>
                </label>
                <input
                  id="p-desc"
                  type="text"
                  value={form.description}
                  onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
                  className="h-12 px-3 rounded-xl border border-gray-200 bg-brand-white text-brand-charcoal focus:outline-none focus:ring-2 focus:ring-brand-rust"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label htmlFor="p-price" className="text-sm font-medium text-brand-charcoal">
                  Price (₹)
                </label>
                <input
                  id="p-price"
                  type="number"
                  inputMode="decimal"
                  min="1"
                  step="1"
                  value={form.price}
                  onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
                  className="h-12 px-3 rounded-xl border border-gray-200 bg-brand-white text-brand-charcoal focus:outline-none focus:ring-2 focus:ring-brand-rust"
                />
              </div>

              {error && (
                <p className="text-sm text-red-600" role="alert">{error}</p>
              )}

              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="flex-1 min-h-[48px] bg-gray-100 text-brand-charcoal font-medium rounded-xl active:scale-95 transition-transform focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-rust"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="flex-1 min-h-[48px] bg-brand-rust text-brand-white font-medium rounded-xl flex items-center justify-center gap-2 active:scale-95 disabled:opacity-60 transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-gold"
                >
                  {saving ? <><Loader2 size={16} className="animate-spin" /> Saving…</> : 'Save'}
                </button>
              </div>
            </form>
          </div>
        </>
      )}
    </div>
  )
}
