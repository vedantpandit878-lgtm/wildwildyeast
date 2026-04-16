const BASE = import.meta.env.VITE_API_URL || 'http://localhost:3000/api'

export async function placeOrder(orderData) {
  const res = await fetch(`${BASE}/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(orderData),
  })
  const data = await res.json()
  if (!res.ok) throw new Error(data.error || 'Error placing order')
  return data
}

export async function getOrder(id) {
  const res = await fetch(`${BASE}/orders/${id}`)
  if (!res.ok) throw new Error('Order not found')
  return res.json()
}
