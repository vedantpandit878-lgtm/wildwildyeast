const BASE = import.meta.env.VITE_API_URL || 'http://localhost:3000/api'

export async function getProducts() {
  const res = await fetch(`${BASE}/products`)
  if (!res.ok) throw new Error('Failed to load products')
  return res.json()
}
