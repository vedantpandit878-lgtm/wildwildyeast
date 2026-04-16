const BASE = import.meta.env.VITE_API_URL || 'http://localhost:3001/api'

function getToken() { return localStorage.getItem('wwy_admin_token') }
function authHeaders() {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` }
}

export async function login(username, password) {
  const res = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  const data = await res.json()
  if (!res.ok) throw new Error(data.error || 'Login failed')
  return data
}

export async function getOrders(params = {}) {
  const qs = new URLSearchParams(
    Object.entries(params).filter(([, v]) => v)
  ).toString()
  const res = await fetch(`${BASE}/orders${qs ? '?' + qs : ''}`, { headers: authHeaders() })
  if (!res.ok) throw new Error('Failed to load orders')
  return res.json()
}

export async function updateOrderStatus(id, status) {
  const res = await fetch(`${BASE}/orders/${id}/status`, {
    method: 'PATCH',
    headers: authHeaders(),
    body: JSON.stringify({ status }),
  })
  if (!res.ok) throw new Error('Failed to update')
  return res.json()
}

export async function getCustomers() {
  const res = await fetch(`${BASE}/customers`, { headers: authHeaders() })
  if (!res.ok) throw new Error('Failed to load customers')
  return res.json()
}

export async function getProducts() {
  const res = await fetch(`${BASE}/products`)
  if (!res.ok) throw new Error('Failed to load products')
  return res.json()
}

export async function createProduct(data) {
  const res = await fetch(`${BASE}/products`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  const json = await res.json()
  if (!res.ok) throw new Error(json.error || 'Failed to create product')
  return json
}

export async function updateProduct(id, data) {
  const res = await fetch(`${BASE}/products/${id}`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  const json = await res.json()
  if (!res.ok) throw new Error(json.error || 'Failed to update product')
  return json
}

export async function deleteProduct(id) {
  const res = await fetch(`${BASE}/products/${id}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  if (!res.ok) throw new Error('Failed to delete product')
}
