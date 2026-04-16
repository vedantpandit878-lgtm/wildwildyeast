const BASE = import.meta.env.VITE_API_URL || 'http://localhost:3000/api'

export async function lookupOrCreateCustomer(flat_number) {
  const res = await fetch(`${BASE}/customers`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ flat_number }),
  })
  const data = await res.json()
  if (res.status === 404 && data.new_customer) return { new_customer: true, flat_number: data.flat_number }
  if (!res.ok) throw new Error(data.error || 'Error')
  return data
}

export async function createCustomer(flat_number, name) {
  const res = await fetch(`${BASE}/customers`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ flat_number, name }),
  })
  const data = await res.json()
  if (!res.ok) throw new Error(data.error || 'Error')
  return data
}

export async function getCustomerOrders(flat_number) {
  const res = await fetch(`${BASE}/customers/${flat_number}/orders`)
  if (!res.ok) throw new Error('Failed to load orders')
  return res.json()
}
